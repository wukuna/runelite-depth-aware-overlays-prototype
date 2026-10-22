Design proposal: depth-aware world overlays

Would there be interest in an opt-in, renderer-owned API for depth-aware world overlays, with ground-item labels as the first use case?

We have an isolated local prototype against RuneLite 1.13.2's GPU renderer. Hills and walls occlude labels, ordinary props such as chests can be excluded, and visible players cover labels without being redrawn through the landscape. A bounded automatic lift helps labels clear the item's local ground surface. Classification currently uses conservative object-name heuristics plus test-only ID overrides; that is not a proposed final classification policy.

It builds a separate depth attachment from eligible visible geometry and composites only the participating labels against it. It does not erase overlay pixels or modify the original scene depth. Existing overlays continue through their usual path. Static geometry ranges are batched, empty label passes are skipped, and label textures and upload buffers are cached/reused. Synthetic graphics tests cover scene preservation, MSAA, state restoration and later UI drawing; broader live compatibility testing remains outstanding.

The broader use would be a shared service for overlays anchored to the game world. Potential consumers include:

- Ground Items and Loot Filters: item names, value labels and custom icons that respect landscape occlusion, with players appearing in front where appropriate. Loot Filters would retain its rules, colors and settings.
- Existing object and resource annotations: labels attached to fishing spots, trees or other annotated scene objects, so an unrelated foreground wall can hide them. Attachment offsets or owner exclusion would need to prevent the target object from hiding its own label.
- Player/NPC nameplates and identification icons: an optional occlusion-aware presentation for otherwise permitted annotations. Moving anchors and actor/self-occlusion need additional handling; the current prototype only uses players as occluders.
- User-placed world notes: a text label or pin attached to a tile or location, staying anchored as the camera moves and disappearing behind intervening landscape.
- Ground markers and object highlights: a possible later extension for world-space shapes that sit behind characters and walls. These need a geometry/decal path and surface-depth bias; text billboards alone do not implement this.

These are candidate rendering applications, not implemented integrations or claims that every feature built on the API would be permitted. The renderer would supply presentation only; each consumer would remain responsible for its own feature rules and data. Screen-space panels, menus and other ordinary overlays would keep their existing behavior.

A minimal first API could accept bounded world-anchored text/image commands, with renderer-owned depth, batching and resource lifetime. The same service should be shared across participating plugins, with a global budget rather than a separate depth pass per plugin. Occlusion policy should be explicit and renderer-controlled: ordinary full-scene depth and the prototype's selective landscape/player policy serve different needs. Owner exclusion, anchor types and policy selection would need maintainer agreement rather than exposing arbitrary framebuffer access.

That API does not exist yet; the current implementation demonstrates ground-item text labels in an isolated GPU client, not all these consumers or a Loot Filters integration. Unsupported renderers would need capability detection and a defined fallback, such as the consumer's existing overlay mode. A Hub plugin should not replace the active renderer's callbacks.

Performance is the main concern. On an RTX 3070 Ti Laptop GPU at 1080p, 64 cached labels alone measured about 0.06 ms CPU submission time. A synthetic single batch of 500 small geometry ranges plus labels measured 0.08 ms CPU / 0.18 ms GPU at 4x MSAA. That is a batching best case, not a live-game FPS claim: 500 separate fullscreen replays still measured roughly 17.5 ms GPU. Rebuilding 64 labels every frame cost about 4.6 ms CPU even after optimization. Additional depth storage is about 32 MiB at 1080p/4x MSAA and 127 MiB at 4K/4x. Current limits are 64 labels and 128 cached textures; more items would require a deliberate prioritization/budget policy.

Would this belong in core as an optional rendering service? Before proposing this for inclusion in core, we'd appreciate guidance on classification, a useful first set of consumers, API scope, shared performance budgets, transparency/roof handling, and whether selective geometry replay is the right architecture. The source is published for review only; no client binaries or Plugin Hub releases are provided; this is a feasibility discussion, not a claim of approval or release readiness.

Background:
https://github.com/riktenx/loot-filters/issues/216
https://github.com/runelite/runelite/wiki/Developer-Guide
https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/hooks/DrawCallbacks.java
