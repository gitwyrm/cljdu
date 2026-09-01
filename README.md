# cljdu

A small native disk-usage browser inspired by `ncdu`.

`cljdu` is ordinary JVM Clojure, rendered by [clj-gpui](https://github.com/gitwyrm/clj-gpui) (GPUI, not a browser or Electron). Select a folder, scan it in the background, and drill into the largest directories.

Primary platforms: **macOS** and **Linux**. Windows is out of scope.

## Features

- Native folder picker (GPUI / desktop portal, with `zenity` fallback on Linux)
- Recursive scan of regular files and directories
- Symbolic links are listed, never followed
- Unreadable paths are skipped, not fatal
- Largest-first listing with human sizes and percent of the current folder
- Drill in, clickable path breadcrumbs, Back, Refresh
- Live “Scanning…” path, size, and counts (no expensive pre-scan)
- Remembers the last folder
- Show the current folder in Finder / the system file manager
- Catppuccin Violet Dark (pinned; does not follow OS light/dark)
- Read-only: no delete

## Development

Requirements: Java 21+, [Clojure CLI](https://clojure.org/guides/install_clojure), a Rust toolchain (first run builds the GPUI host), and a display. On Linux, Vulkan is required; Mesa lavapipe is enough.

```bash
clj -M:dev
```

nREPL listens on `127.0.0.1:7888` (see `.nrepl-port`). Edit `src/` and save for hot reload.

```bash
clojure -M:test
clojure -M:cljfmt check
clojure -M:cljfmt fix
```

Connect to a running UI with `clojure -M:connect`.

`deps.edn` depends on [clj-gpui](https://github.com/gitwyrm/clj-gpui) at git SHA `c31b0cf8edf7ed54ccf0114a85458c705ecc7120` (scroll views fill leftover height; viewport width/size live on the wrapper).

## Packaging

End users do not need Rust, Cargo, the Clojure CLI, or a system JDK. Packaging is native-only:

| Build host | Command | Output |
|---|---|---|
| macOS | `clj -X:build package` | `target/package/cljdu.app` |
| Linux | `clj -X:build package` | `target/package/cljdu-0.1.0-x86_64.AppImage` and `target/package/cljdu_0.1.0_amd64.deb` |

Use `-X` (not `-T`) so clj-gpui stays on the classpath. That uses clj-gpui's `gpui.package` plus `gpui.edn` in this repo. The bundle contains a jlink JRE, an uberjar started with `gpui.prod` (no nREPL, no source watcher, no Cargo), and the GPUI host.

If the AppImage cannot mount FUSE, run it extracted:

```bash
APPIMAGE_EXTRACT_AND_RUN=1 ./target/package/cljdu-0.1.0-x86_64.AppImage
```

`LICENSE` and `NOTICE` from this repo are copied into the packages (`usr/share/doc/cljdu/` on Linux, `Contents/Resources/licenses/` on macOS).

Other tasks: `clj -X:build uberjar`, `clj -X:build host`, `clj -X:build jre`.

macOS codesigning is not applied. After a local `.app`:

```bash
codesign --deep --force --sign - target/package/cljdu.app   # ad-hoc
```

Notarization can be added later with a Developer ID and `notarytool`.

## License

MIT. The Catppuccin Violet palette is adapted from [utility_belt_gpui](https://github.com/gitwyrm/utility_belt_gpui) (MIT OR Apache-2.0); see `NOTICE`.
