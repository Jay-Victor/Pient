#!/system/bin/sh
# Pient 终端层：Root 档的 root 侧启动器（由宿主以 `su -c "sh <此脚本> …"` 调用，运行在 root 身份）。
#
# 与 PRoot 路径的区别（照 Operit：默认 PRoot，条件具备（Root）时用 chroot）：
#   - chroot 是**真 root**：guest 里的 uid 0 就是真的 0，可改系统、可 apt 装服务、可绑特权端口；
#     且零模拟开销（PRoot 要 ptrace 拦截每个系统调用）。
#   - 代价：需要 /dev /proc /sys 与工作区的挂载（root 才能 mount），挂载点会留在设备上。
#
# 参数：
#   无参    → 交互会话（stdin 直通给 guest bash，供终端页长连）
#   -c FILE → 单条命令（命令正文放在 FILE 里，避开 su -c 的多层引号地狱）
set -u

P=$(dirname "$0")          # 运行时根（脚本落在 <pient-rt>/ 下）
R=$P/rootfs

[ -x "$R/bin/bash" ] || { echo "[pient] rootfs 未就绪：$R" >&2; exit 127; }

# chroot 前把宿主侧该给的东西挂进去（幂等：已是挂载点会失败，忽略即可）
mount -t proc proc "$R/proc" 2>/dev/null
mount -t sysfs sysfs "$R/sys" 2>/dev/null
mount -o bind /dev "$R/dev" 2>/dev/null
mount -o bind "$P/app" "$R/workspace" 2>/dev/null

GUEST_ENV="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin HOME=/root LANG=C.UTF-8 TERM=xterm"

if [ "${1:-}" = "-c" ] && [ -f "${2:-}" ]; then
  # 命令正文从文件读，作为单个参数交给 guest bash
  exec env -i $GUEST_ENV chroot "$R" /bin/bash -c "$(cat "$2")"
fi

exec env -i $GUEST_ENV chroot "$R" /bin/bash
