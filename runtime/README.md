# Pient 工具层运行时（设备侧）

> 目的：把 **pi 官方工具层**跑在设备上——内嵌 Node 宿主 + pi 官方 npm 包（七工具）+ rg/fd 原生二进制，
> 并由 Android 应用经 **官方 RPC 协议**（JSONL over stdio）驱动。
> 这是《Pient 开发计划》M1（核心运行时）里「宿主先行」的切片：**先让工具在设备上真能跑**。
> 终端环境（Ubuntu rootfs / PRoot）**已接上**（2026-09-12）：`bash` 工具经 pi 的 `shellPath` 直落 rootfs 里的 GNU bash。

## 当前状态（2026-09-12 实测）

| 项 | 结果 |
| --- | --- |
| 设备 | AVD `Medium_Phone_API_36.1`，Android 16 / API 36 / x86_64 / 1080×2400 |
| Node | **v26.4.0 在设备上运行**（Termux 产物；`process.platform === "android"`） |
| pi 包 | `@earendil-works/pi-coding-agent@0.85.1`（官方 npm，dist 已构建，含 `dist/bundle/rpc-entry.js`） |
| 七工具 | **read / write / edit / ls / grep / find / bash 全部在设备上执行成功**（`probes/tools_probe.mjs`） |
| 应用内宿主 | MainActivity 启动 → 内嵌 Node 子进程 → `get_state` 回包成功（`PiHost` 日志） |
| 应用内命令执行 | 经 RPC `bash` 跑通 `uname -srm` / `echo` / `$HOME` 展开（`PiHostProbe` 日志，验证后已移除该临时探针） |
| 模型接线 | 应用按「服务商与模型配置」生成 `~/.pi/agent/models.json` + `auth.json`；宿主以 `--provider mock --model mock/deepseek-chat` 启动 → **`get_state` 里 `model=deepseek-chat`**（不再是 unknown） |
| agent 闭环 | **模型 → tool_call → 设备执行 → 结果回灌 → 最终回答** 全部跑通（`probes/agent_loop_probe.mjs`，`SUMMARY tool_calls=1 tool_results=1`） |
| 终端环境 | **Ubuntu 24.04.3 rootfs（PRoot，无需 root）已接上**：pi 的 `shellPath` → 随包分发的包装脚本 → `proot -0 -r rootfs` → GNU bash 5.2.21。应用进程内实测：`PRETTY_NAME="Ubuntu 24.04.3 LTS"` / `uid=0` / `cwd=/workspace` |
| 会话 · 授权 · 保活 | 会话映射（Pient 会话 ↔ pi 会话，首次映射物化历史）、工具级授权（守门扩展 + 三档策略 + 设置页）、前台服务托管 —— 均已实测通过 |

## 可执行文件为什么放在 native lib 目录（关键约束）

Android 10 起**应用不能 execve 自己私有目录里的文件**。实测 avc（`adb logcat | grep avc`）：

```
avc: granted { execute }            ... tcontext=u:object_r:app_data_file ...
avc: denied  { execute_no_trans }   path=/data/data/com.pient.app/files/pient-rt/usr/bin/node
                                    scontext=u:r:untrusted_app  tcontext=u:object_r:app_data_file
```
策略依据（`/system/etc/selinux/plat_sepolicy.cil`）：
- `untrusted_app_all × app_data_file` 只给 `execute`（mmap 用），**不给 `execute_no_trans`**（execve 用）；
  只有 targetSdk ≤ 27 的老域（`untrusted_app_25/27`）才有 `execute_no_trans`。
- `appdomain × apk_data_file` **给 `execute_no_trans`**；而 `/data/app(/.*)?` 全都是 `apk_data_file`
  （`plat_file_contexts`），**包括 `/data/app/.../lib/<abi>/`**。

所以二进制必须以 `jniLibs` 形式随 APK 分发（命名成 `libpient_*.so`），并让安装时解压到
native lib 目录：`app/build.gradle.kts` 里
```kotlin
packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += setOf("**/libpient_*.so") } }
```
（`useLegacyPackaging=false` 时库只在 APK 内 mmap，磁盘上没有可执行文件。）

**踩过的坑**：`adb shell run-as <pkg> files/.../node -v` 会**成功**（run-as 的域与真实应用域不同），
不能拿它当「应用能 exec 私有目录」的证据——必须在应用进程里跑才算数。

**rg/fd 的 PATH 问题**：pi 的 grep/find 用 `spawn("rg")`/`spawn("fd")` 走 PATH 查找，文件名必须是
`rg`/`fd`，而 native lib 目录里只能叫 `lib*.so`。解法：在应用私有目录 `files/pient-rt/bin/` 里
建 **同名符号链接**指向 lib 目录里的二进制（`PiRuntime.ensureLinks`，用 `Os.symlink`），
把该目录放进宿主 `PATH`；SELinux 校验的是符号链接的**最终目标**（apk_data_file，允许 exec）。
另注：pi 的 `tools-manager` 在 `platform()==="android"` 时**不会自动下载** rg/fd（提示 `pkg install …`），
离线优先必须内置。

## 目录形态

**打包形态 = 混合（2026-09-12 拍板）**：随包带 node + 核心库（离线即可起宿主），pi npm 包按需部署/更新。

```
APK 内（jniLibs，随包分发、安装时解压）
  lib/<abi>/libpient_node.so / _rg.so / _fd.so   ← 内嵌 Node（Termux 产物）/ ripgrep / fd
  lib/<abi>/libpient_proot.so / _proot_loader.so / _shell.so
                   ← PRoot、它的 ELF loader、pi 的 shellPath 包装脚本（三者都要被 execve，
                     只能落在 native lib 目录；rootfs 上万文件不进 APK，由 loader 以 mmap 加载）
  lib/<abi>/libpient_libc___shared_so.so 等 27 个 ← Node 的动态依赖（libc++_shared / libicu /
                   openssl / sqlite / ffi / c-ares / z / pcre2 …）
  assets/pient_runtime_libs.txt                   ← SONAME → jniLib 名映射表（构建期生成）
  （复制/改名/生成映射表 = build.gradle.kts 的 syncPientRuntime / syncPientRuntimeLibs /
    writePientRuntimeLibsManifest；名字整段转义——硬约束是「jniLibs 只收 lib*.so」，
    而依赖名带 SONAME 后缀 libicuuc.so.78 / libz.so.1，且 libicudata.so.78 与 .78.3 同时存在，
    截断命名会撞车）

应用私有目录（files/pient-rt/）
  usr/lib/<SONAME>    → 软链到 native lib 里的 libpient_*.so（App 每次启动重建，见 PiRuntime.ensureLinks；
                        不重建就会在 APK 更新后悬空——/data/app 路径会变）；
                        LD_LIBRARY_PATH 指向该目录（子进程环境变量里生效，实测有效）
  app/node_modules/…  pi 官方包（按需部署/更新：deploy_app_runtime.sh）
  rootfs/             Ubuntu 24.04.3 用户空间（约 97MB；GNU bash/coreutils/apt，首启解包，待做）
  bin/{node,rg,fd}    指向 native lib 二进制的符号链接（PATH 用；pi 的 grep/find 按名字找 rg/fd）
  home/  tmp/         HOME / TMPDIR
```

## 为什么用 Termux 产物（而不是先自建交叉编译）

- Termux 的 Node 二进制 `PT_INTERP = /system/bin/linker64`（**系统 linker，无需任何 patch**），
  动态依赖只指向系统 bionic（libc/libm/libdl）+ 第三方库；放到任意目录、`LD_LIBRARY_PATH` 指过去即可运行。
- 代价：依赖是 Termux 版本（ICU 78.3 / OpenSSL 3.6.3 等），升级时按 `fetch_runtime.py` 的固定版本重拉。

## 脚本与用法

```bash
# 1) 拉运行时（Node + 依赖库 + rg/fd + pi 官方 npm 包）到 runtime/cache/
python runtime/scripts/fetch_runtime.py --abi x86_64      # 模拟器；真机用 --abi aarch64

# 1b) 拉终端层产物（PRoot + loader + 依赖库 + Ubuntu base rootfs）到 runtime/cache/rootfs-<abi>/
python runtime/scripts/fetch_rootfs.py --abi x86_64
#    rootfs 的展开目前是设备侧手动铺（run-as 解包到 files/pient-rt/rootfs）；首启自动解包待做

# 2) 构建安装（把 runtime/cache 的二进制 + 依赖库打成本 ABI 的 libpient_*.so 与映射表 assets）
#    ABI 策略 = 单 ABI 出包（与 Operit 同口径）；默认 x86_64 供模拟器，真机/release 显式给
#    -PpientRuntimeAbi=arm64-v8a（release 漏写会直接构建失败，避免出无声的错包）
cd .. && JAVA_HOME='E:/Java/JDK21' ./gradlew.bat :app:assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/pient.apk
adb shell pm install -r /data/local/tmp/pient.apk

# 3) 部署 pi 官方 npm 包（按需那部分；二进制与依赖库已随 APK）
bash runtime/scripts/deploy_app_runtime.sh

# 4)（可选）不改应用、直接验证七工具：推 cache 到 /data/local/tmp 并跑探针
bash runtime/scripts/deploy_dev.sh

# 5)（可选）验证 agent 闭环：模型 → tool_call → 设备执行 → 结果回灌 → 回答
#    前置：本机跑 mock（scripts/mock_llm.py，见项目 skill），deploy_dev.sh 已写好指向它的 models.json
adb shell "cd /data/local/tmp/pient-runtime/work && PIENT_RT=/data/local/tmp/pient-runtime \
  PATH=/data/local/tmp/pient-runtime/usr/bin:\$PATH LD_LIBRARY_PATH=/data/local/tmp/pient-runtime/usr/lib \
  HOME=/data/local/tmp/pient-runtime/home TMPDIR=/data/local/tmp/pient-runtime/tmp \
  /data/local/tmp/pient-runtime/usr/bin/node \
  /data/local/tmp/pient-runtime/probes/agent_loop_probe.mjs @/data/local/tmp/pient-runtime/work/prompt.txt mock/deepseek-chat"
# prompt 里写 `[[tool:read {\"path\":\"hello.txt\"}]]` → mock 回工具调用 → 看 SUMMARY tool_calls=1 tool_results=1
```

应用侧入口：`com.pient.app.runtime.PiHost`（`ensureStarted()` → 启动宿主 + 刷新 `get_state`），
`PiRuntime`（路径/环境/就绪判定），`PiRpcClient`（JSONL 分帧、命令-响应关联、事件流）。
日志 tag：`PiHost`（宿主生命周期与 get_state）。

## 官方 RPC 握手（设备侧复现）

```bash
adb shell 'cd /data/local/tmp/pient-runtime/app && \
  PATH=/data/local/tmp/pient-runtime/usr/bin:$PATH \
  LD_LIBRARY_PATH=/data/local/tmp/pient-runtime/usr/lib \
  HOME=/data/local/tmp/pient-runtime/home \
  /data/local/tmp/pient-runtime/usr/bin/node \
  node_modules/@earendil-works/pi-coding-agent/dist/bundle/rpc-entry.js --mode rpc --no-session'
# stdin 逐行发：{"type":"get_state","id":"1"}  /  {"type":"bash","id":"2","command":"uname -srm"}
# stdout 逐行收：{"id":"1","type":"response","command":"get_state","success":true,"data":{...}}
```

## 已知约束与待定项

1. **bash 工具的执行环境已接上**：pi 的 `shellPath`（settings 项）指向随包分发的包装脚本，命令经 PRoot 跑在
   rootfs 的 GNU bash 里；宿主自身的兜底 shell 仍是 Toybox（未配 shellPath 时的旧行为，仅作降级）。
   **rootfs 首启解包未做**：现在靠 `run-as` 手动铺 97MB，产品形态按计划把 minbase 内嵌 assets。
2. **体积**：node 49MB + 依赖库 ~90MB + pi `node_modules` 119MB ≈ 260MB。当前分工是
   「二进制进 APK（+15MB 压缩后）＋库与包在应用私有目录」；最终打包形态（assets 首启解压 / 首启联网下载）
   与 ABI 策略（2026-09-12 已拍板：打包形态 = 混合、ABI = 单 ABI 出包，见「目录形态」；
  参考案例 Operit 同为单 ABI（arm64-v8a）+ `useLegacyPackaging=true` + 二进制做 lib*.so 进 jniLibs）。
3. **arm64 未实测**：终端层只跑过 x86_64 模拟器；真机需 `-PpientRuntimeAbi=arm64-v8a` + `fetch_rootfs.py --abi aarch64`。
4. **workspace 口径待收敛**：现在把宿主 `app/` 绑到 guest 的 `/workspace`（与六个文件工具同一目录）；
   用户项目目录 / SAF 目录如何映射进 Ubuntu 待定。
5. **终端层剩余**：`!` 命令复用同一通道；ulimit / 超时看门狗（计划 §6.4）；rootfs 内 `apt` 的可达性与代理设置。
