#!/usr/bin/env bash
# 一次性生成 Pient 的 release 签名（keystore + keystore.properties）。
#
# ⚠️ 只在首次发版前跑一次！重复运行会生成**另一把 key** —— 已发布的版本就再也装不上后续版本了。
# 密码在运行时随机生成，只写进本地 keystore.properties（已在 .gitignore 里，不入库）。
# 备份这两样东西：keystore/pient-release.jks + keystore.properties
#   —— 丢了 key 就没法给已装的用户发更新，只能让他们卸载重装。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# keytool / Gradle 都是原生 Windows 程序，拿不了 MSYS 风格的 /e/... 路径 —— 转成 E:/... 再传。
REPO_ROOT_WIN="$(cd "$REPO_ROOT" && pwd -W)"
KEYSTORE_DIR="$REPO_ROOT/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/pient-release.jks"
KEYSTORE_FILE_WIN="$REPO_ROOT_WIN/keystore/pient-release.jks"
PROPS_FILE="$REPO_ROOT/keystore.properties"
ALIAS="pient"

if [ -e "$KEYSTORE_FILE" ] || [ -e "$PROPS_FILE" ]; then
    echo "已存在：$KEYSTORE_FILE 或 $PROPS_FILE —— 不覆盖。要重做请先手动备份并删除。" >&2
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"

PW="$(python -c 'import secrets; print(secrets.token_urlsafe(24))')"

keytool -genkeypair \
    -keystore "$KEYSTORE_FILE_WIN" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -storetype PKCS12 \
    -storepass "$PW" -keypass "$PW" \
    -dname "CN=Pient, OU=Pient, O=Jay-Victor, C=CN"

cat > "$PROPS_FILE" <<EOF
# Pient release 签名（本机私有，勿入库；对应 keystore/pient-release.jks）
storeFile=$KEYSTORE_FILE_WIN
storePassword=$PW
keyAlias=$ALIAS
keyPassword=$PW
EOF

chmod 600 "$PROPS_FILE" "$KEYSTORE_FILE" 2>/dev/null || true
echo "已生成："
echo "  $KEYSTORE_FILE"
echo "  $PROPS_FILE（密码在里面，自己看/自己备份）"
