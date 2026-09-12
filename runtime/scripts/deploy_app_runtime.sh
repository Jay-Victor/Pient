#!/usr/bin/env bash
# 把运行时里**需要按需更新**的那部分（pi 官方 npm 包 + HOME/TMPDIR 骨架）部署到应用私有
# 目录 files/pient-rt/，供应用内的 pi 宿主使用。
#
# 打包形态 = 混合（2026-09-12 拍板）：
# - **随 APK 分发**：可执行文件（node/rg/fd）与其动态依赖库 —— 二进制必须走 native lib
#   目录（Android 10+ 禁止应用 exec 自己私有目录里的文件：SELinux untrusted_app ×
#   app_data_file 无 execute_no_trans，而 /data/app 下的 apk_data_file 允许）；依赖库一并
#   随包，离线即可起宿主。构建期由 build.gradle.kts 的 syncPientRuntime(Libs) 复制成
#   libpient_*.so，并生成 SONAME → jniLib 名映射表（assets），App 启动时据此在
#   files/pient-rt/usr/lib/ 建软链（见 PiRuntime.ensureLinks）。
# - **按需部署/更新**（本脚本）：pi npm 包（体积大、可独立升级）。
# 详见 runtime/README.md。
#
# 用法: bash runtime/scripts/deploy_app_runtime.sh
set -euo pipefail

ADB="${ADB:-E:/SDK/platform-tools/adb.exe}"
PKG="${PKG:-com.pient.app}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CACHE="$ROOT/cache"
DEVICE_STAGE="${DEVICE_STAGE:-/data/local/tmp/pient-rt-stage}"

[ -d "$CACHE/app/node_modules" ] || { echo "缺少 pi 包（$CACHE/app/node_modules），先跑 fetch_runtime.py"; exit 1; }

WIN() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

echo "== 暂存到 $DEVICE_STAGE（shell 可写），再 run-as 拷进应用私有目录 =="
"$ADB" shell rm -rf "$DEVICE_STAGE"
"$ADB" shell mkdir -p "$DEVICE_STAGE"
"$ADB" push "$(WIN "$CACHE/app")" "$DEVICE_STAGE/app" | tail -1

echo "== run-as 拷入 files/pient-rt/（保留既有 home/tmp）=="
"$ADB" shell "run-as $PKG mkdir -p files/pient-rt/usr files/pient-rt/app files/pient-rt/home files/pient-rt/tmp"
# 用 src/. 形式拷「内容」而不是「目录本身」，否则目标已存在时会嵌一层（src/目标名）
"$ADB" shell "run-as $PKG cp -r $DEVICE_STAGE/app/node_modules/. files/pient-rt/app/node_modules/"

echo "== 回读确认 =="
"$ADB" shell "run-as $PKG ls files/pient-rt/app/node_modules/@earendil-works"
# usr/lib 里应是**软链**（指向 /data/app/.../lib/<abi>/libpient_*.so，App 启动时建立）
"$ADB" shell "run-as $PKG ls -l files/pient-rt/usr/lib | head -3"
echo "== 清理暂存（设备上 $(echo "$DEVICE_STAGE")）=="
"$ADB" shell rm -rf "$DEVICE_STAGE"
echo "完成。重启应用后宿主会读取新运行时（日志 tag：PiHost）。"
