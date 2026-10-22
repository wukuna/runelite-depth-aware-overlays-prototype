# Depth-aware world overlays: RuneLite GPU prototype

Experimental source and design discussion, based on RuneLite `runelite-parent-1.13.2`. **Not an approved feature, Plugin Hub submission, or client release.** No binaries, authenticated launchers, profiles or account data are included.

Start with [the proposal](PROPOSAL.md), [performance findings](PERFORMANCE.txt), and [the renderer patch](renderer.patch).

The prototype draws ground-item text labels against a separate selective depth buffer: landscape and players occlude labels; classified props do not. It preserves the original scene depth and does not redraw players. Other applications in the proposal are future API consumers, not implemented features.

## Review the source

The patch applies at the root of RuneLite tag `runelite-parent-1.13.2` using `git apply --check renderer.patch` followed by `git apply renderer.patch`. It changes the GPU renderer and adds four helper classes. It is intentionally guarded by `Boolean.getBoolean("runelite.depthLabelsTest")` and test configuration; it does not enable itself in an ordinary client. This repository does not supply a launcher or recommend replacing a live client.

`tests/` contains the isolated Java/JUnit/OpenGL probes used during development. They use JUnit 4.13.2, LWJGL GLFW 3.3.2 and the platform native library, alongside the modified GPU sources and RuneLite client 1.13.2. They are review fixtures, not a turnkey upstream test integration. `DepthPerformanceProbe` takes its CSV output path as its first argument; system properties `probe.small=true` and `probe.batch=true` select small coverage and a single batched replay respectively. Run only with a suitable OpenGL context. See the report for limitations and baseline comparability.

CSV results are synthetic measurements on one laptop GPU, not live-game FPS guarantees. Input label counts over 64 are capped. In the batched CSV, `replay_calls` counts input ranges, not independent replay submissions. CPU and GPU elapsed times must not be added together.

## Scope and next decisions

The public API, other plugin integrations, alternate renderer support, global budgeting, and a production classification policy remain proposals. The current label cap is 64 piles within 12 tiles, with 128 cached textures. Dynamic/alpha geometry can still cause expensive separate replay calls. See the report before drawing performance conclusions.

Upstream discussion should start in RuneLite's development channel as described in its [Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide). Publishing this review package does not imply RuneLite or Jagex approval.

RuneLite-derived source retains its existing notices; see [LICENSE](LICENSE).
