#!/system/bin/sh
# Pient 终端层：pi bash 工具的 shellPath —— **AI 的工具命令的执行环境入口**。
#
# pi 以 `spawn(shellPath, ["-c", 命令])` 调用（core/tools/bash.ts:95），所以这个脚本决定
# 「AI 的工具命令跑在哪」；终端页的会话也用同一个脚本（用户看到的与 AI 用的一致）。
#
# 为什么这个文件在 APK 的 native lib 目录而不是应用私有目录：
# SELinux 下 untrusted_app 对 app_data_file 只有 execute(mmap)、没有 execute_no_trans(execve)，
# 私有目录里的 ELF 和 shebang 脚本**都不能被 exec**（两种都实测拒过）。只有 /data/app/.../lib/<abi>/
# （apk_data_file）允许 execve，所以本脚本随 APK 以 libpient_shell.so 分发、由构建期复制。
#
# 路径不写死：pi 传给子进程的 HOME = <pient-rt>/home，取其父目录即运行时根。
P=$(dirname "$HOME")

# ── 执行环境路由（应用写进 $P/exec_env；本脚本每次被执行时现读 → 改完下一条命令即生效）──
#   android       系统 shell（toybox）：am / pm / dumpsys / getprop 等 Android 命令直接可用
#   ubuntu        Ubuntu 24.04 rootfs（PRoot，应用 uid，无需 Root）
#   ubuntu-chroot 同一个 rootfs，su + chroot（真 uid 0，零模拟开销；无 su 时可见回退 PRoot）
# PIENT_EXEC_ENV 可覆盖（环境配置页安装组件时强制走 Ubuntu，不受当前选择影响）。
E=${PIENT_EXEC_ENV:-$(cat "$P/exec_env" 2>/dev/null)}

# 工作区 = 当前项目目录（应用写；没设过就退回随包工作区 app/）——agent 的 cwd 与 guest 的 /workspace 同一处
W=$(cat "$P/workspace" 2>/dev/null)
[ -n "$W" ] && [ -d "$W" ] || W=$P/app

# ── ① Android shell（需 Shizuku / Root 授权）：系统命令以 root 身份执行 ──
#   标准权限下这个环境不可选（页面里会拦）；真跑起来时若拿不到 su 就可见回退 Ubuntu。
if [ "$E" = "android" ]; then
  if command -v su >/dev/null 2>&1; then
    if [ "${1:-}" = "-c" ] && [ $# -ge 2 ]; then
      mkdir -p "$P/tmp"
      # 命令正文经文件转交，避开 su -c "...引号..." 的拆解（路径无空格，安全）
      printf '%s' "$2" > "$P/tmp/android-cmd.sh"
      exec su -c "cd $W 2>/dev/null; sh $P/tmp/android-cmd.sh"
    fi
    exec su -c "cd $W 2>/dev/null; /system/bin/sh"
  fi
  echo "[pient] 已选 Android shell，但未获得 Shizuku / Root 授权 —— 本次回退 Ubuntu（PRoot）。" >&2
fi

# ── ② Ubuntu（chroot）：su 通道，真 root ──
if [ "$E" = "ubuntu-chroot" ]; then
  if command -v su >/dev/null 2>&1; then
    if [ "${1:-}" = "-c" ] && [ $# -ge 2 ]; then
      mkdir -p "$P/tmp"
      # 命令正文经文件转交，避免 su -c "...被引号拆坏..."（路径无空格，安全）
      printf '%s' "$2" > "$P/tmp/root-cmd.sh"
      exec su -c "sh $P/pient-root-wrapper.sh -c $P/tmp/root-cmd.sh"
    fi
    exec su -c "sh $P/pient-root-wrapper.sh"
  fi
  echo "[pient] 已选 Ubuntu（chroot），但设备上没有可用的 su（未 root / 未授权）——本次回退 PRoot（应用 uid）。" >&2
fi

# ── ③ Ubuntu（PRoot，默认）：应用 uid 跑 GNU 用户空间 ──
# env -i：宿主环境是 Android 的（PATH 指向 /system/bin、LD_* 指向 Android 库），
# 直接透传会把 guest 污染成四不像（实测：guest 里 `head`/`id` 全找不到）。
# `-l`（link2symlink）**必须开**：SELinux 不允许 untrusted_app 建硬链接（avc denied { link }），
# 而 dpkg 每次都要 `link status → status-old`、解包也常带硬链接条目 —— 不开就是
# `dpkg: error creating new backup file '/var/lib/dpkg/status-old': Permission denied` →
# apt 彻底用不了（实测）。proot-distro / Operit 在 Android 上同样开这个。
exec env -i \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  HOME=/root LANG=C.UTF-8 TERM=xterm \
  LD_LIBRARY_PATH=$P/usr/lib \
  PROOT_LOADER=$P/bin/proot-loader \
  PROOT_TMP_DIR=$P/tmp \
  $P/bin/proot -0 -l -r $P/rootfs -w /workspace \
    -b /dev -b /proc -b /sys -b $W:/workspace \
    /bin/bash "$@"
