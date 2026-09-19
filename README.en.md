<div align="center">

<img src="Refences/Logo/Pient.png" width="96" alt="Pient" />

# Pient

**A native Android AI-agent workbench built on [Pi Agent](https://github.com/earendil-works/pi)**

[![Release](https://img.shields.io/github/v/release/Jay-Victor/Pient?label=release)](https://github.com/Jay-Victor/Pient/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20%C2%B7%20arm64--v8a-3ddc84)](https://github.com/Jay-Victor/Pient/releases)

[中文](README.md) · [Download](#download--install) · [Build from source](#build-from-source) · [Architecture](#architecture-at-a-glance) · [Gitee mirror](https://gitee.com/Jay-Victor/Pient)

</div>

---

## What is Pient

Pient = **Pi** + **Agent**. It brings [Pi Agent](https://github.com/earendil-works/pi) to Android in full: the UI is native Jetpack Compose, while the AI runtime is **the official pi packages, shipped inside the app, running in a bundled Ubuntu environment**. The app talks to pi only over pi's official RPC (`pi --mode rpc`, JSONL) — no invented in-between protocol.

Conversations, tools (file read/write, shell, search), sessions and context management are therefore all handled by pi itself; Pient provides the parts it lacks — **the Android-side UI, terminal, file access and system capabilities**:

- App ↔ one pi subprocess, no extra hops
- Bundled Ubuntu 24.04 (PRoot by default, chroot in Root mode) — no separate Linux download, no Termux
- The workspace is the project folder you opened: both the AI and the terminal land there

> Under active development; see [Releases](https://github.com/Jay-Victor/Pient/releases) for the latest version and downloads.

## Features

**Chat & branching**

- Streaming answers, thinking process and levels, tool-call cards; Markdown / tables / syntax highlighting / LaTeX rendering
- Message actions: fork a new session from here (native pi fork), copy message (plain text / Markdown / XML), quote-and-ask
- Branch canvas: the session's branch structure drawn as a node tree; branch again from any node

**Terminal & execution environment**

- Terminal page: one persistent Ubuntu session per chat session, multi-tab
- Environment setup: apt mirror selection, on-demand installation of Node.js / Python, pi version management
- Android shell channel: run commands as the system user via Shizuku (debug tier) or `su` (Root tier) — reachable by the AI as well

**Files**

- Project file tree: create / rename / delete / search, over both SAF and direct filesystem access
- Preview and editing: syntax highlighting with line numbers, Markdown render / source editing, images, audio & video playback, docx / xls(x) to text
- `@` file references and the `/` command palette (extension commands / prompt templates / skills)

**Models & providers**

- Provider and model configuration: a built-in catalog of 30+ providers, per-model context window / max output / reasoning / sampling parameters, per-model connectivity test
- Token and cost statistics, with a bundled pricing table
- Thinking levels forwarded to pi (rendered from the levels pi reports)

**UI & system**

- Material 3 with frosted / liquid glass surfaces; theme mode, color schemes, fonts, background images
- 8 UI languages: 简体中文 / 繁體中文 / English / 日本語 / Español / Français / Português / हिन्दी
- Three permission tiers: Standard / Debug (Shizuku) / Root
- On-disk app logs with export, keep-alive, and in-app "Check for updates" with download & install

## Screenshots

| Chat & tool calls | Branch canvas |
| --- | --- |
| ![Chat](docs/screenshots/chat.png) | ![Branch canvas](docs/screenshots/branch-canvas.png) |
| Assign tasks as soon as you open a project: tool calls, file trees and usage all in the message stream | Session branches drawn as a node tree — the blue line is the active path |

| Terminal (bundled Ubuntu 24.04) | File tree |
| --- | --- |
| ![Terminal](docs/screenshots/terminal.png) | ![File tree](docs/screenshots/file-tree.png) |
| One persistent shell per session; commands run by the AI get their own read-only mirror tab | The project's real filesystem: create / rename / import / export |

| File preview | Providers & models |
| --- | --- |
| ![File preview](docs/screenshots/file-preview.png) | ![Model config](docs/screenshots/model-config.png) |
| GFM rendering with a one-tap switch to source editing; code comes with line numbers and highlighting | A built-in provider catalog with per-model parameters, endpoints and a connectivity test |

| Settings | Open-source licenses |
| --- | --- |
| ![Settings](docs/screenshots/settings.png) | ![Licenses](docs/screenshots/licenses.png) |
| Theme & appearance, language, behaviour, models, data & permissions, about | Third-party components bundled with the app, plus the full GPL-3.0 text |

## Download & install

| Platform | Link |
| --- | --- |
| GitHub Releases | <https://github.com/Jay-Victor/Pient/releases> |
| Gitee Releases (China) | <https://gitee.com/Jay-Victor/Pient/releases> |

- Requires **Android 8.0 (API 26) or newer**; **arm64-v8a only** (the vast majority of current devices)
- The APK is ~71 MB and already contains the Ubuntu root filesystem and the pi runtime
- Existing users can update from within the app: About → Check for updates

## Getting started

1. **Create a project**: chat sidebar → New project, pick a folder as the workspace
2. **Configure a model**: Settings → Providers & models, enter an API key → "Test connection" → choose provider and model
3. **Install the runtime**: Settings → Environment, pick an apt mirror and install Node.js (pi needs Node ≥ 22.19). Ubuntu and pi itself ship with the app
4. (Optional) Grant the basic system permissions, and enable the "Debug" tier with Shizuku if you want the AI to run system-level commands

## Build from source

**Requirements**

| Dependency | Notes |
| --- | --- |
| JDK 17+ | Java 17 target (AGP 8.10.1 / Kotlin 2.3.0) |
| Android SDK | Platform 36 and matching Build-Tools (`compileSdk 36`, `minSdk 26`) |
| Python 3 | Build scripts under `runtime/scripts/` |
| Node.js + npm | `pack_pi_archive.py` resolves pi's dependency closure with the local npm and packs it into `assets/pient-pi.tgz` |
| Network | To fetch the runtime and npm packages (TUNA / ubuntu-cdimage / npmmirror by default; all sources live in `runtime/scripts/` and can be swapped) |

```bash
# 1) Fetch the runtime: Ubuntu base rootfs + PRoot (~30 MB into runtime/cache/, not tracked)
python runtime/scripts/fetch_rootfs.py --abi aarch64

# 2) Build (the first run resolves the pi packages via npm)
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

- Emulator only (x86_64): `python runtime/scripts/fetch_rootfs.py --abi x86_64`, then `./gradlew :app:assembleDebug -PpientRuntimeAbi=x86_64`
- Release build: run `./scripts/gen_release_keystore.sh` first to create the signing config (`keystore.properties` and `keystore/` are git-ignored — **never commit them**), then `./gradlew :app:assembleRelease`. Release builds are locked to arm64-v8a
- Put `sdk.dir=<Android SDK path>` in `local.properties`, or set `ANDROID_HOME`

## Architecture at a glance

| Layer | Where | Notes |
| --- | --- | --- |
| App (Android side) | `app/src/main/java/com/pient/app` | Compose UI + state; `data/` holds the data layer, `runtime/` talks to pi and to the guest |
| Conversation | `runtime/PiRpc.kt` | `pi --mode rpc` (JSONL); sessions, context and tools are all maintained by pi |
| Terminal | `runtime/TerminalSessions.kt` | One persistent process per session: wrapper script → PRoot → Ubuntu bash |
| Android shell | `runtime/AndroidShell.kt` + `ExecBridge.kt` | A separate Java-side channel (Shizuku / `su`); pi inside the guest calls back through a loopback bridge |
| Runtime (Ubuntu side) | shipped in the APK | Ubuntu 24.04 base + the official pi packages, unpacked into app-private storage on first launch |

Data layout: on the pi side `~/.pi/agent` (guest `/root/.pi/agent` ↔ host `files/pient-rt/rootfs/root/.pi/agent`), file-level compatible with desktop pi.

## Repository layout

```
app/src/main/java/com/pient/app/
├── data/            # state and data layer (ChatState / config stores / i18n / pricing / logs…)
├── runtime/         # pi RPC, terminal sessions, Android shell, runtime install & readiness checks
└── ui/              # Compose UI (chat / canvas / files / terminal / skills / plugins / settings…)
runtime/
├── scripts/         # runtime fetching, pi archive packing (invoked by the build)
└── terminal/        # pient-shell.sh (guest-side wrapper script)
Refences/Logo/       # icon sources and derived images
scripts/             # repository scripts (release keystore generation, …)
```

## License

Pient is released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

Third-party components distributed with the app keep their own licenses; the full list is available in the app under **Settings → About → Open-source licenses**. The main ones:

- **Bundled runtime**: official pi packages (MIT), PRoot (GPL-2.0), libtalloc (GPL-3.0), libandroid-shmem (BSD-3-Clause), Ubuntu 24.04 base rootfs (components under their own licenses, shipped as `/usr/share/doc/*/copyright` inside the image)
- **App dependencies**: AndroidX / Jetpack Compose, OkHttp, Apache POI, media3 (ExoPlayer), Backdrop, Liquid, Android-Image-Cropper (Apache-2.0), Shizuku (MIT), JLaTeXMath-Android (GPL-2.0-or-later with Classpath exception)
- **Fonts**: LXGW WenKai, JetBrains Mono (OFL-1.1)
- **Data**: simple-icons brand icons (CC0-1.0), [models.dev](https://models.dev) model pricing data

## Acknowledgements

- [pi](https://github.com/earendil-works/pi) — the runtime core Pient is built on
- [pi-web](https://github.com/agegr/pi-web) — reference for the UI and branching behaviour
- [Operit](https://github.com/AAswordman/Operit) — Android engineering blueprint (rootfs delivery, permissions, terminal)
- [Mdcito](https://github.com/Jay-Victor/Mdcito) — reference for materials and the update system
- [simple-icons](https://github.com/simple-icons/simple-icons), [models.dev](https://models.dev) — brand icons and pricing data
