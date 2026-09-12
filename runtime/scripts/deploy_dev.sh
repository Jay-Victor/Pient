#!/usr/bin/env bash
# 把 runtime/cache 里的运行时推到设备并跑工具层探针。
# 用法: bash runtime/scripts/deploy_dev.sh [探针工作目录]
# 前置: python runtime/scripts/fetch_runtime.py --abi <x86_64|aarch64>
set -euo pipefail

ADB="${ADB:-E:/SDK/platform-tools/adb.exe}"
DEVICE_DIR="${DEVICE_DIR:-/data/local/tmp/pient-runtime}"
ABI="${ABI:-x86_64}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CACHE="$ROOT/cache"

# 注意：Windows 上 Python chmod 不落执行位，MSYS 的 -x 判定会失败 → 用 -f，执行位在设备侧 chmod
[ -f "$CACHE/usr/bin/node" ] || { echo "缺少 $CACHE/usr/bin/node，先跑 fetch_runtime.py"; exit 1; }
[ -d "$CACHE/app/node_modules" ] || { echo "缺少 pi 包（$CACHE/app/node_modules），先跑 fetch_runtime.py"; exit 1; }

echo "== 设备 ABI 自检 =="
DEV_ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "device abi=$DEV_ABI (cache abi=$ABI)"
case "$DEV_ABI" in
  x86_64) [ "$ABI" = "x86_64" ] || echo "!! 警告：cache 是 $ABI，设备是 x86_64，无法执行" ;;
  arm64-v8a) [ "$ABI" = "aarch64" ] || echo "!! 警告：cache 是 $ABI，设备是 arm64-v8a，无法执行" ;;
esac

echo "== 推送运行时 =="
# adb 是原生 Windows 程序：MSYS 风格 /e/... 路径会被拒，转成 E:/... 原生路径
WIN() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
"$ADB" shell mkdir -p "$DEVICE_DIR"
"$ADB" shell rm -rf "$DEVICE_DIR/usr"
"$ADB" shell rm -rf "$DEVICE_DIR/app"
"$ADB" shell rm -rf "$DEVICE_DIR/work"
"$ADB" shell rm -rf "$DEVICE_DIR/probes"
"$ADB" push "$(WIN "$CACHE/usr")" "$DEVICE_DIR/usr" | tail -1
"$ADB" push "$(WIN "$CACHE/app")" "$DEVICE_DIR/app" | tail -1
"$ADB" push "$(WIN "$ROOT/probes")" "$DEVICE_DIR/probes" | tail -1
"$ADB" shell mkdir -p "$DEVICE_DIR/work"
"$ADB" shell chmod 755 "$DEVICE_DIR/usr/bin/node" "$DEVICE_DIR/usr/bin/rg" "$DEVICE_DIR/usr/bin/fd"

# 开发用 pi 模型配置（模型接线等价物）：指向本机 mock（模拟器内 10.0.2.2 = 宿主机）
# 直接写本地文件再 push——adb shell 里嵌 heredoc 的多层引号极易出错
CFG_DIR="$(mktemp -d)"
mkdir -p "$CFG_DIR/agent"
cat > "$CFG_DIR/agent/models.json" <<'EOF'
{"providers":{"mock":{"baseUrl":"http://10.0.2.2:8765/v1","api":"openai-completions","apiKey":"sk-mock","models":[{"id":"deepseek-chat","name":"deepseek-chat","contextWindow":200000,"maxTokens":64000,"reasoning":true}]}}}
EOF
cat > "$CFG_DIR/agent/auth.json" <<'EOF'
{"mock":{"type":"api_key","key":"sk-mock"}}
EOF
"$ADB" shell "mkdir -p $DEVICE_DIR/home/.pi"
"$ADB" push "$(WIN "$CFG_DIR/agent")" "$DEVICE_DIR/home/.pi/agent" | tail -1
rm -rf "$CFG_DIR"

echo "== 跑工具层探针 =="
"$ADB" shell "cd $DEVICE_DIR/app && PATH=$DEVICE_DIR/usr/bin:\$PATH LD_LIBRARY_PATH=$DEVICE_DIR/usr/lib PI_PKG_DIR=$DEVICE_DIR/app $DEVICE_DIR/usr/bin/node $DEVICE_DIR/probes/tools_probe.mjs $DEVICE_DIR/work" | tr -d '\r'
