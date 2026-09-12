#!/usr/bin/env python3
"""拉取终端层产物：PRoot（+ 依赖库）与 Ubuntu base rootfs。

用法:
    python runtime/scripts/fetch_rootfs.py --abi x86_64     # 模拟器
    python runtime/scripts/fetch_rootfs.py --abi aarch64    # 真机

产物（runtime/cache/rootfs-<abi>/）:
    bin/proot                      PRoot 主程序（随 APK 以 libpient_proot.so 分发）
    libexec/proot/loader*          PRoot 的 ELF loader（随 APK 分发，运行时 PROOT_LOADER 指过去）
    usr/lib/libtalloc.so*          PRoot 依赖库（并入运行时 usr/lib）
    ubuntu-base-<版本>-<arch>.tar.gz  rootfs 归档（首启解包到 files/pient-rt/rootfs）

为什么要这么拆：Android 10+ 应用不能 execve 自己私有目录里的文件（SELinux untrusted_app ×
app_data_file 无 execute_no_trans），只有 /data/app/.../lib/<abi>/（apk_data_file）允许 —— 所以
**主程序与 loader 必须随 APK 以 native lib 形态分发**，而 rootfs 里的 bash/coreutils 由 loader
以 mmap 方式加载（exec 权限允许），不需要逐个进 APK。
"""

from __future__ import annotations

import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fetch_runtime import CACHE, MIRROR, deb_files, download, log, resolve_filename  # noqa: E402

# PRoot 及其依赖（Termux 仓库固定版本，2026-09-12 实测同一来源）
PKGS = {
    # pkg: (版本, 文件名模板)；版本仅作「固定路径优先」尝试，404 时自动回退到仓库索引里的当前版本
    "proot": ("5.1.107-62", "pool/main/p/proot/proot_{ver}_{abi}.deb"),
    "libtalloc": ("2.4.3", "pool/main/libt/libtalloc/libtalloc_{ver}_{abi}.deb"),
    # proot 的依赖（deb 的 Depends 就这两个，缺一个都起不来）：
    #   libandroid-shmem → 匿名共享内存垫片；libtalloc → 内存池库
    # 注意：二进制里虽然出现过 libtermux-exec.so 字样，但它不在 Depends 里（新版 termux-exec
    # 亦已不再提供该 SONAME），实测不提供也能跑 —— 别照着字符串往清单里加。
    "libandroid-shmem": ("0.7", "pool/main/liba/libandroid-shmem/libandroid-shmem_{ver}_{abi}.deb"),
}

# Ubuntu base rootfs（清华镜像 ubuntu-cdimage 目录；版本按需升）
UBUNTU_VER = "24.04.3"
UBUNTU_ARCH = {"x86_64": "amd64", "aarch64": "arm64", "i686": "i386", "arm": "armhf"}
CDIMAGE = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/"


def fetch_rootfs(abi: str) -> str:
    root = os.path.join(CACHE, f"rootfs-{abi}")
    n = 0
    for pkg, (ver, tpl) in PKGS.items():
        rel = resolve_filename(pkg, abi, tpl, ver, {})
        deb = os.path.join(CACHE, "debs", os.path.basename(rel).replace(":", "_"))
        download(MIRROR + rel, deb)
        for name, blob in deb_files(deb):
            base = os.path.basename(name)
            if "/usr/bin/" in name and base == "proot":
                dest = os.path.join(root, "bin", base)
            elif "/libexec/proot/" in name and base.startswith("loader"):
                dest = os.path.join(root, "libexec", "proot", base)
            elif "/usr/lib/" in name and base.startswith(
                ("libtalloc.so", "libandroid-shmem.so", "libtermux-exec.so")
            ):
                dest = os.path.join(root, "usr", "lib", base)
            else:
                continue
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as f:
                f.write(blob)
            os.chmod(dest, 0o755)
            n += 1
            log(f"{pkg}: {os.path.relpath(dest, root)} ({len(blob)} B)")

    arch = UBUNTU_ARCH[abi]
    name = f"ubuntu-base-{UBUNTU_VER}-base-{arch}.tar.gz"
    url = f"{CDIMAGE}ubuntu-base/releases/{UBUNTU_VER[:-2]}/release/{name}"
    tar = os.path.join(root, name)
    download(url, tar)

    # SONAME 别名：链接器要找的是 libtalloc.so.2，而 deb 里只有带完整版本号的实体文件
    for alias, target in {"libtalloc.so.2": "libtalloc.so.2.4.3"}.items():
        src, dst = os.path.join(root, "usr", "lib", target), os.path.join(root, "usr", "lib", alias)
        if os.path.exists(src) and not os.path.exists(dst):
            shutil.copyfile(src, dst)
            log(f"SONAME 别名 {alias} -> {target}")

    for need in ("bin/proot", "libexec/proot/loader"):
        p = os.path.join(root, need)
        if not os.path.exists(p):
            raise RuntimeError(f"缺少 {need}，deb 布局可能变了：请检查 {root}")
    log(f"完成：{root}（{n} 个文件 + rootfs 归档 {os.path.getsize(tar) // 1048576} MB）")
    return root


def main() -> int:
    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--abi", default="x86_64", choices=list(UBUNTU_ARCH))
    args = ap.parse_args()
    fetch_rootfs(args.abi)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
