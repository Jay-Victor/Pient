#!/usr/bin/env python3
"""Pient 运行时（工具层）拉取脚本 —— 把设备侧 Node + 依赖库 + rg/fd + pi 官方 npm 包
拉到 runtime/cache/，供 deploy_dev.sh 推送到设备。

来源（2026-09-12 实测可用）：
  - Node/依赖库/ripgrep/fd：Termux 官方 deb（清华 TUNA 镜像；Termux 产物链接 Android bionic，
    可在任意 64 位 Android 上运行，interp = /system/bin/linker64，无需 patch）
  - pi 官方 npm 包：registry.npmjs.org（锁定版本）

用法：
  python scripts/fetch_runtime.py --abi x86_64            # 模拟器（本机 AVD）
  python scripts/fetch_runtime.py --abi aarch64           # 真机（arm64-v8a）
  python scripts/fetch_runtime.py --abi aarch64 --skip-npm
"""
from __future__ import annotations

import argparse
import gzip
import io
import json
import lzma
import os
import shutil
import subprocess
import sys
import tarfile
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                 # runtime/
CACHE = os.path.join(ROOT, "cache")
MIRROR = "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/"
PI_PACKAGE = "@earendil-works/pi-coding-agent"
PI_VERSION = "0.85.1"

# 版本与依赖闭包（2026-09-12 在 Termux stable 仓库 x86_64 索引中实测的版本）
PKGS = {
    # 包名: (版本, 仓库相对路径模板)
    "nodejs":            ("26.4.0-1",  "pool/main/n/nodejs/nodejs_{ver}_{abi}.deb"),
    "zlib":              ("1.3.2",     "pool/main/z/zlib/zlib_{ver}_{abi}.deb"),
    "libc++":            ("29",        "pool/main/libc/libc++/libc++_{ver}_{abi}.deb"),
    "openssl":           ("1:3.6.3",   "pool/main/o/openssl/openssl_{ver}_{abi}.deb"),
    "c-ares":            ("1.34.8",    "pool/main/c/c-ares/c-ares_{ver}_{abi}.deb"),
    "libicu":            ("78.3",      "pool/main/libi/libicu/libicu_{ver}_{abi}.deb"),
    "libsqlite":         ("3.53.4",    "pool/main/libs/libsqlite/libsqlite_{ver}_{abi}.deb"),
    "libffi":            ("3.8.0",     "pool/main/libf/libffi/libffi_{ver}_{abi}.deb"),
    "libandroid-support": ("29-1",     "pool/main/liba/libandroid-support/libandroid-support_{ver}_{abi}.deb"),
    "pcre2":             ("10.47",     "pool/main/p/pcre2/pcre2_{ver}_{abi}.deb"),
    "ripgrep":           ("15.2.0",    "pool/main/r/ripgrep/ripgrep_{ver}_{abi}.deb"),
    "fd":                ("10.5.0",    "pool/main/f/fd/fd_{ver}_{abi}.deb"),
}

# 需要的可执行文件（Pi 七工具的宿主依赖）
BINARIES = ("node", "rg", "fd")

# SONAME 别名：deb 里只带实体文件，symlink 成员会被 tarfile 跳过 → 这里按 SONAME 建副本
SONAME_ALIASES = {
    "libz.so.1": "libz.so.1.3.2",
    "libsqlite3.so": "libsqlite3.so.3.53.4",
    "libicui18n.so.78": "libicui18n.so.78.3",
    "libicuuc.so.78": "libicuuc.so.78.3",
    "libicudata.so.78": "libicudata.so.78.3",
}


def log(msg: str) -> None:
    print(f"[fetch_runtime] {msg}", flush=True)


def download(url: str, dest: str) -> None:
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    log(f"下载 {url}")
    with urllib.request.urlopen(url, timeout=180) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(tmp, dest)


def ar_members(path: str) -> dict[str, bytes]:
    """极简 ar 解析（deb 是 ar 归档；Python 无 stdlib ar 模块）"""
    data = open(path, "rb").read()
    if data[:8] != b"!<arch>\n":
        raise ValueError(f"{path} 不是 ar 归档")
    off, out = 8, {}
    while off + 60 <= len(data):
        hdr = data[off:off + 60]
        name = hdr[0:16].decode().strip().rstrip("/")
        size = int(hdr[48:58].decode().strip())
        out[name] = data[off + 60:off + 60 + size]
        off += 60 + size + (size % 2)
    return out


def deb_files(path: str):
    """yield (成员名, bytes)"""
    members = ar_members(path)
    key = next(k for k in members if k.startswith("data.tar"))
    blob = members[key]
    raw = lzma.decompress(blob) if key.endswith("xz") else gzip.decompress(blob)
    with tarfile.open(fileobj=io.BytesIO(raw)) as tf:
        for m in tf.getmembers():
            if m.isfile():
                yield m.name, tf.extractfile(m).read()


def fetch_index(abi: str) -> str:
    """拉取仓库索引（仅用于版本回退时查 Filename）"""
    path = os.path.join(CACHE, f"Packages_{abi}.gz")
    download(f"{MIRROR}dists/stable/main/binary-{abi}/Packages.gz", path)
    with gzip.open(path, "rt", encoding="utf-8", errors="replace") as f:
        return f.read()


def resolve_filename(pkg: str, abi: str, tpl: str, ver: str, index_cache: dict) -> str:
    """优先用固定版本路径；404 时回退到索引里的当前版本"""
    pinned = tpl.format(ver=ver, abi=abi)
    try:
        req = urllib.request.Request(MIRROR + pinned, method="HEAD")
        with urllib.request.urlopen(req, timeout=30):
            return pinned
    except Exception:
        pass
    if abi not in index_cache:
        index_cache[abi] = fetch_index(abi)
    index = index_cache[abi]
    for entry in index.split("\n\n"):
        if entry.startswith(f"Package: {pkg}\n"):
            for line in entry.splitlines():
                if line.startswith("Filename:"):
                    fn = line.split(": ", 1)[1].strip()
                    log(f"{pkg}: 固定版本 {ver} 不可用，回退到索引版本 {fn}")
                    return fn
    raise RuntimeError(f"仓库里找不到包 {pkg}")


def extract(abi: str, skip_npm: bool) -> None:
    usr_bin = os.path.join(CACHE, "usr", "bin")
    usr_lib = os.path.join(CACHE, "usr", "lib")
    os.makedirs(usr_bin, exist_ok=True)
    os.makedirs(usr_lib, exist_ok=True)
    index_cache: dict[str, str] = {}

    for pkg, (ver, tpl) in PKGS.items():
        rel = resolve_filename(pkg, abi, tpl, ver, index_cache)
        # Windows 文件名不允许 ':'（openssl 版本是 epoch 形式 "1:3.6.3"），落盘时替换
        deb = os.path.join(CACHE, "debs", os.path.basename(rel).replace(":", "_"))
        download(MIRROR + rel, deb)
        n_bin = n_lib = 0
        for name, blob in deb_files(deb):
            base = os.path.basename(name)
            if "/usr/bin/" in name and base in BINARIES:
                dest = os.path.join(usr_bin, base)
                open(dest, "wb").write(blob)
                os.chmod(dest, 0o755)
                n_bin += 1
            elif "/usr/lib/" in name and ".so" in base:
                open(os.path.join(usr_lib, base), "wb").write(blob)
                n_lib += 1
        log(f"{pkg} {ver}: bin={n_bin} lib={n_lib}")

    for alias, target in SONAME_ALIASES.items():
        src, dst = os.path.join(usr_lib, target), os.path.join(usr_lib, alias)
        if os.path.exists(src) and not os.path.exists(dst):
            shutil.copyfile(src, dst)
            log(f"SONAME 别名 {alias} -> {target}")

    missing = [b for b in BINARIES if not os.path.exists(os.path.join(usr_bin, b))]
    if missing:
        raise RuntimeError(f"缺少可执行文件: {missing}")

    if not skip_npm:
        app_dir = os.path.join(CACHE, "app")
        os.makedirs(app_dir, exist_ok=True)
        pkg_json = os.path.join(app_dir, "package.json")
        if not os.path.exists(pkg_json):
            with open(pkg_json, "w", encoding="utf-8") as f:
                json.dump({"name": "pient-runtime", "private": True, "type": "module"}, f, indent=1)
        log(f"npm install {PI_PACKAGE}@{PI_VERSION}（约 150MB）")
        subprocess.run(
            ["npm", "i", "--silent", "--no-audit", "--no-fund",
             f"{PI_PACKAGE}@{PI_VERSION}"],
            cwd=app_dir, check=True, shell=(os.name == "nt"),
        )
        log(f"pi 包就绪: {os.path.join(app_dir, 'node_modules')}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--abi", default="x86_64", choices=["x86_64", "aarch64", "i686", "arm"])
    ap.add_argument("--skip-npm", action="store_true", help="只拉 Node/库/二进制，不装 pi 包")
    args = ap.parse_args()
    extract(args.abi, args.skip_npm)
    log(f"完成：{CACHE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
