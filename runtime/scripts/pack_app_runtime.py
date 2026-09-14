#!/usr/bin/env python3
"""把宿主运行时（pi 官方 npm 包 + npm 本体）打成**随 APK 分发的 assets 归档**。

为什么要有它：宿主需要两样东西——pi 官方包（`app/node_modules/@earendil-works/pi-coding-agent`）
与 npm 本体（`npm/bin/npm-cli.js`，给 pi 的包管理器当 `npmCommand` 用）。此前 pi 包只
能靠 `deploy_app_runtime.sh` 用 adb 推到设备（产品形态下用户没有 adb），npm 更是完全没有。
打成归档后 App 首启自己解包，装完即用（与 rootfs 同一条链路，见 PiRuntime.ensureAppRuntimeAsync）。

归档布局（解包目标是 `files/pient-rt/`）：
  app/…   ← runtime/cache/app（pi 包 + 依赖）
  npm/…   ← runtime/cache/npm/node_modules/npm（npm 包本体）

用法：
  python runtime/scripts/pack_app_runtime.py --out build/generated/pientAssets/pient-app.tgz
"""
from __future__ import annotations

import argparse
import os
import sys
import tarfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                      # runtime/
CACHE = os.path.join(ROOT, "cache")
APP_SRC = os.path.join(CACHE, "app")
NPM_SRC = os.path.join(CACHE, "npm", "node_modules", "npm")

# 不必随包的体积负担：npm 的文档/测试与 lock 缓存
SKIP_NAMES = {".package-lock.json", "CHANGELOG.md", "docs"}


def log(msg: str) -> None:
    print(f"[pack_app_runtime] {msg}", flush=True)


def add_tree(tf: tarfile.TarFile, src: str, arc: str) -> int:
    """按目录树加入归档；返回加入的条目数（跳过 SKIP_NAMES 命名的条目）。"""
    n = 0
    for base, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in SKIP_NAMES]
        rel = os.path.relpath(base, src)
        arc_dir = arc if rel == "." else os.path.join(arc, rel)
        tf.add(base, arcname=arc_dir, recursive=False)
        n += 1
        for f in files:
            if f in SKIP_NAMES:
                continue
            path = os.path.join(base, f)
            try:
                tf.add(path, arcname=os.path.join(arc_dir, f), recursive=False)
                n += 1
            except OSError as e:      # 长路径/权限问题在 Windows 上偶发
                log(f"跳过 {path}：{e}")
    return n


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="输出 .tgz 路径（别用 .gz 后缀：aapt 会自动解压）")
    ap.add_argument("--app", default=APP_SRC)
    ap.add_argument("--npm", default=NPM_SRC)
    args = ap.parse_args()

    if not os.path.isdir(args.app):
        log(f"缺少 pi 包缓存：{args.app}（先跑 fetch_runtime.py）")
        return 1
    if not os.path.isfile(os.path.join(args.npm, "bin", "npm-cli.js")):
        log(f"缺少 npm 缓存：{args.npm}/bin/npm-cli.js（先跑 fetch_runtime.py --npm）")
        return 1

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    tmp = out + ".part"
    with tarfile.open(tmp, "w:gz", compresslevel=6) as tf:
        n_app = add_tree(tf, args.app, "app")
        n_npm = add_tree(tf, args.npm, "npm")
    os.replace(tmp, out)
    size = os.path.getsize(out)
    log(f"完成：{n_app} + {n_npm} 个条目 → {out}（{size / 1048576:.1f} MB）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
