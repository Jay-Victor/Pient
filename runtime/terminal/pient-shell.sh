#!/system/bin/sh
# Pient 终端层：pi bash 工具的 shellPath。
#
# pi 以 `spawn(shellPath, ["-c", 命令])` 调用（core/tools/bash.ts:95），所以这里把参数原样
# 交给 rootfs 里的 GNU bash —— 命令真正跑在 Ubuntu 24.04 用户空间里（PRoot 包裹，无需 root）。
#
# 为什么这个文件在 APK 的 native lib 目录而不是应用私有目录：
# SELinux 下 untrusted_app 对 app_data_file 只有 execute(mmap)、没有 execute_no_trans(execve)，
# 私有目录里的 ELF 和 shebang 脚本**都不能被 exec**（两种都实测拒过）。只有 /data/app/.../lib/<abi>/
# （apk_data_file）允许 execve，所以本脚本随 APK 以 libpient_shell.so 分发、由构建期复制。
#
# 路径不写死：pi 传给子进程的 HOME = <pient-rt>/home，取其父目录即运行时根。
P=$(dirname "$HOME")

# env -i：宿主环境是 Android 的（PATH 指向 /system/bin、LD_* 指向 Android 库），
# 直接透传会把 guest 污染成四不像（实测：guest 里 `head`/`id` 全找不到）。
exec env -i \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  HOME=/root LANG=C.UTF-8 TERM=xterm \
  LD_LIBRARY_PATH=$P/usr/lib \
  PROOT_LOADER=$P/bin/proot-loader \
  PROOT_TMP_DIR=$P/tmp \
  $P/bin/proot -0 -r $P/rootfs -w /workspace \
    -b /dev -b /proc -b /sys -b $P/app:/workspace \
    /bin/bash "$@"
