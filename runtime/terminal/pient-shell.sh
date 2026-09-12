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

# ── 档位路由（照 Operit：默认 PRoot 运行，条件具备（Root）时用 chroot）──
#   档位由应用写进 $P/terminal_mode（SettingsStore.permissionTier → pi 的 Root 档）。
#   本脚本每次被执行时现读 → 改档后**下一个**命令/会话即生效（已在跑的会话进程不换）。
# 工作区 = 当前项目目录（应用写；没设过就退回随包工作区 app/）——agent 的 cwd 与 guest 的 /workspace 同一处
W=$(cat "$P/workspace" 2>/dev/null)
[ -n "$W" ] && [ -d "$W" ] || W=$P/app

MODE=$(cat "$P/terminal_mode" 2>/dev/null)
if [ "$MODE" = "root" ]; then
  if command -v su >/dev/null 2>&1; then
    if [ "${1:-}" = "-c" ] && [ $# -ge 2 ]; then
      mkdir -p "$P/tmp"
      # 命令正文经文件转交，避免 su -c "...被引号拆坏..."（路径无空格，安全）
      printf '%s' "$2" > "$P/tmp/root-cmd.sh"
      exec su -c "sh $P/pient-root-wrapper.sh -c $P/tmp/root-cmd.sh"
    fi
    exec su -c "sh $P/pient-root-wrapper.sh"
  fi
  echo "[pient] 已选 Root 档，但设备上没有可用的 su（未 root / 未授权）——本次回退 PRoot（应用 uid）。" >&2
fi

# env -i：宿主环境是 Android 的（PATH 指向 /system/bin、LD_* 指向 Android 库），
# 直接透传会把 guest 污染成四不像（实测：guest 里 `head`/`id` 全找不到）。
exec env -i \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  HOME=/root LANG=C.UTF-8 TERM=xterm \
  LD_LIBRARY_PATH=$P/usr/lib \
  PROOT_LOADER=$P/bin/proot-loader \
  PROOT_TMP_DIR=$P/tmp \
  $P/bin/proot -0 -r $P/rootfs -w /workspace \
    -b /dev -b /proc -b /sys -b $W:/workspace \
    /bin/bash "$@"
