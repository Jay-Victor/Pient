#!/usr/bin/env python3
"""把 pi 官方 npm 包**连同它的依赖闭包**打成随 APK 预置的归档（`assets/pient-pi.tgz`）。

为什么（2026-09-14 用户拍板，按 Operit「工具包随 App」口径）：
    pi 的工具（read/write/edit/bash/grep/find/ls）就在 pi 包里 —— 「Pient 里有没有 pi 的工具」
    等价于「APK 里有没有这个归档」。预置之后用户只需在 Ubuntu 里装 node（≥22.19）就能 `pi`。

为什么必须带依赖闭包（实测踩过）：
    pi 的 `dist/bundle/cli.js` 不是完全自包含 —— 它把 `@earendil-works/chord` 等**外置**了：
    只放包本体时设备上报 `ERR_MODULE_NOT_FOUND: Cannot find package '@earendil-works/chord'
    imported from .../dist/bundle/chunks/chunk-*.js`。所以这里用本机 npm 真解析一遍依赖树，
    把 `node_modules/` 顶层（扁平布局，与 `npm i -g` 的结果一致）整个打进去。

归档布局（与 npm 全局安装一致 → 直接 `tar -x` 进 `/usr/lib/node_modules/`）：
    @earendil-works/pi-coding-agent/dist/bundle/cli.js
    @earendil-works/{chord,pi-ai,pi-agent-core,pi-tui}/…
    chalk/  typebox/  diff/  …（其余依赖）

用法：
    python runtime/scripts/pack_pi_archive.py --out <path>/pient-pi.tgz
    python runtime/scripts/pack_pi_archive.py --reuse        # 复用已有 stage（不重跑 npm）
"""
from __future__ import annotations

import argparse
import glob
import json
import gzip
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE = ROOT / "runtime" / "cache" / "pi"
STAGE = CACHE / "stage"
NPM_CACHE = CACHE / "npm-cache"
REGISTRIES = [
    "https://registry.npmmirror.com",      # 国内首选（实测 3s 内响应）
    "https://registry.npmjs.org",          # 兜底
]


def log(msg: str) -> None:
    print(f"[pack_pi] {msg}", flush=True)


def detect_version() -> str:
    for pj in sorted(glob.glob(str(ROOT / "Refences" / "pi-*" / "packages" / "coding-agent" / "package.json"))):
        m = re.search(r'"version"\s*:\s*"([^"]+)"', pathlib.Path(pj).read_text(encoding="utf-8"))
        if m:
            return m.group(1)
    return "0.85.1"


def npm_install(version: str) -> pathlib.Path:
    """在 STAGE 里真装一遍（只装 pi 包 + 它的依赖闭包），返回 node_modules 目录"""
    nm = STAGE / "node_modules"
    if (nm / "@earendil-works/pi-coding-agent/dist/bundle/cli.js").is_file():
        log("stage 已有完整安装（复用）")
        return nm
    if nm.exists():
        log("stage 残留不完整 → 清掉重装")
        shutil.rmtree(nm, ignore_errors=True)
    STAGE.mkdir(parents=True, exist_ok=True)
    NPM_CACHE.mkdir(parents=True, exist_ok=True)
    (STAGE / "package.json").write_text(
        json.dumps(
            {
                "name": "pient-pi-stage",
                "private": True,
                "dependencies": {"@earendil-works/pi-coding-agent": version},
            },
            indent=1,
        ) + "\n",
        encoding="utf-8",
    )
    last = None
    for reg in REGISTRIES:
        log(f"npm install（registry={reg}）—— 依赖由 npm 扁平提升，与 `npm i -g` 的布局一致")
        cmd = [
            "npm", "install", "--ignore-scripts",
            "--no-audit", "--no-fund", "--loglevel=error",
            "--cache", str(NPM_CACHE), "--registry", reg,
        ]
        p = subprocess.run(cmd, cwd=str(STAGE), capture_output=True, text=True, shell=(os.name == "nt"))
        if p.returncode == 0 and (nm / "@earendil-works/pi-coding-agent/dist/bundle/cli.js").is_file():
            log("npm install 完成")
            return nm
        last = (p.returncode, (p.stderr or p.stdout or "")[-400:])
        log(f"registry 失败：{last}")
    raise SystemExit(f"npm install 全部失败：{last}")


def size_of(path: pathlib.Path) -> int:
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file())


# 剪枝规则（2026-09-14 实测：npm 闭包 92.8MB，其中 sourcemap 30.1MB / .d.ts 11.3MB /
# 平台专用 esbuild 11.3MB / docs 1.9MB 全是运行时用不到的）。逐条只删「确定与运行无关」的东西：
PRUNE_SUFFIX = (".map", ".d.ts", ".d.mts", ".d.cts", ".md", ".markdown", ".flow", ".tsbuildinfo")
PRUNE_DIRS = ("docs", "examples", "test", "tests", "__tests__", ".bin", "@types", "@esbuild", "esbuild",
              "man", "coverage", ".github")
PRUNE_PATH_HINTS = ("-win32-", "-darwin-", "-freebsd-", "-android-", "win32-x64", "win32-arm64",
                    "darwin-x64", "darwin-arm64", "_esbuild", "esbuild.exe")
PRUNE_FILE_NAMES = ("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "CHANGELOG.md", "README.md",
                    ".npmignore", ".gitignore", "tsconfig.json")


def should_prune(rel: str) -> bool:
    lower = rel.lower()
    parts = lower.split("/")
    if parts[-1].endswith(PRUNE_SUFFIX) or parts[-1] in [n.lower() for n in PRUNE_FILE_NAMES]:
        return True
    if any(d in parts[:-1] for d in PRUNE_DIRS):
        return True
    return any(h in lower for h in PRUNE_PATH_HINTS)


def repack(nm: pathlib.Path, out: pathlib.Path) -> None:
    """把 node_modules/ 的内容（剥掉这层前缀、剪掉运行时用不到的）打成 gzip tar：
    目标侧 `tar -x` 直接落进全局 node_modules 目录"""
    out.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    kept = 0
    dropped = 0
    prefix = str(nm).replace("\\", "/") + "/"
    with out.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=6, mtime=0) as gz:
            with tarfile.open(fileobj=gz, mode="w|", format=tarfile.GNU_FORMAT) as to:
                for f in sorted(nm.rglob("*")):
                    rel = str(f).replace("\\", "/")
                    assert rel.startswith(prefix), rel
                    arc = rel[len(prefix):]
                    if f.is_file():
                        if should_prune(arc):
                            dropped += 1
                            continue
                        kept += f.stat().st_size
                    info = to.gettarinfo(str(f), arcname=arc)
                    if f.is_file():
                        with f.open("rb") as fh:
                            to.addfile(info, fh)
                    else:
                        to.addfile(info)
                    n += 1
    log(f"重打包：{out}（保留 {n} 项 / 剪掉 {dropped} 个文件；解开 {kept / 1048576:.1f} MB，"
        f"压缩后 {out.stat().st_size / 1048576:.2f} MB）")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--reuse", action="store_true", help="复用已有 stage/node_modules")
    a = ap.parse_args()
    version = a.version or detect_version()
    out = pathlib.Path(a.out) if a.out else (CACHE / "pient-pi.tgz")
    nm = STAGE / "node_modules"
    if a.reuse and nm.is_dir():
        log("--reuse：跳过 npm install")
    else:
        log(f"pi 版本：{version}")
        nm = npm_install(version)
    log(f"闭包体积（解开）：{size_of(nm) / 1048576:.1f} MB，包数：{len([d for d in nm.iterdir()])}")
    repack(nm, out)
    with tarfile.open(out) as tf:
        names = tf.getnames()
        ok_entry = any(n.endswith("pi-coding-agent/dist/bundle/cli.js") for n in names)
        # 依赖是**内嵌**在 pi 包自己的 node_modules 里 —— 这是 npm 的行为（pi 带 npm-shrinkwrap.json，
        # 实测 `npm i -g` 同样嵌套），所以预置布局与全局安装一致
        has_chord = any(n.endswith("@earendil-works/chord/package.json") for n in names)
    log(f"自检：cli.js={ok_entry} chord 依赖={has_chord}")
    return 0 if (ok_entry and has_chord) else 1


if __name__ == "__main__":
    sys.exit(main())
