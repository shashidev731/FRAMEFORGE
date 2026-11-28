# FRAMEFORGE — Verified Development Checkpoint

- **Application:** FRAMEFORGE (com.frameforge.app)
- **Repository:** https://github.com/shashidev731/FRAMEFORGE
- **Branch:** main
- **Last fully CI-verified code baseline:** 9883d0be013bd04ba1770e3a08860ea832fc50b3 (CI run #248, success)
- **Current code checkpoint:** `f7c8eaa675f02ad65a2ef2c3c55aa6b859c2712d` includes source-URI-scoped photo history queries, independent per-image undo/redo branches, source-specific cleanup, and Photo Studio controls bound to the selected image. It also configures `MultipleInputVideoGraph.Factory` for `CompositionPlayer`, required for separate visual sequences in editor preview. These changes do not alter the Room schema. **CI is unverified:** the connected GitHub status endpoint returned no status entries, and the available workflow-run lookup only returns pull-request-triggered runs (none were returned for these push commits). Do not mark these commits CI-verified until the Actions run is inspected.
- **Completion:** Not 100% complete. Code coverage, CI verification, and physical-device verification are tracked separately.

## 1. Latest verified CI and artifacts

| Run | Commit | Result | Verified steps |
|---|---|---|---|
| [#244](https://github.com/shashidev731/FRAMEFORGE/actions/runs/45945907747) | cb12671a1400460f86084a5c0c76a11a2b072058 | Success | Debug build, lint, unit tests, release build, APK validation, debug/release artifact upload |
| [#245](https://github.com/shashidev731/FRAMEFORGE/actions/runs/45946300367) | 57eda5832f4c31584d8d1fdcf9665c4fa61c5837 | Success | Debug build, lint, unit tests, release build, APK validation, artifact upload |
| [#246](https://github.com/shashidev731/FRAMEFORGE/actions/runs/45946732384) | e874fa5ca41e0251d2738484fab661e877a4ad83 | Success | Debug build, lint, unit tests, release build, APK validation, artifact upload |

Run #243 failed because the UI attempted to read WorkInfo.inputData, which WorkManager does not expose. Commit #244 replaced that access with a private, ownership-checked request-path marker; #244 then passed the full workflow.

Latest fully verified artifacts are available from [CI run #246](https://github.com/shashidev731/FRAMEFORGE/actions/runs/45946732384):
- frameforge-debug-apk — 21,843,707 bytes
- frameforge-release-unsigned-apk — 14,728,088 bytes
- frameforge-lint-report

The release artifact is unsigned because no private release-signing secrets were configured. CI validated the APK outputs; this does not replace installation and playback testing on a physical Android device. Artifacts are scheduled to expire on 2027-02-25.

## 2. Feature status

| Area | Current status | Evidence / limitation |
|---|---|---|
| Android foundation, Compose UI, navigation, dark theme | CI-VERIFIED | Debug and release builds pass in runs #244–#246. |
| Project management | IMPLEMENTED | Room-backed create/open/rename/duplicate/delete and project metadata. Physical-device workflow not yet audited. |
| Media import and library | IMPLEMENTED | Document picker, persisted URI access/private-copy handling, metadata and thumbnails. Device/provider edge cases still need testing. |
| Timeline editing | IMPLEMENTED | Tracks, gaps, trims, split, move/reorder, duplicate/delete, clip timing and speed calculations have code and unit coverage. |
| Visual track composition | IMPLEMENTED / PARTIAL | Main visual sequence plus picture-in-picture overlay sequences. Arbitrary freeform positioning and transitions are not complete. |
| Undo/redo | PARTIAL | Timeline snapshot history restores clip state. Photo Studio now scopes history, redo-branch deletion, undo, redo, and cleanup to the exact selected source URI; this fixes cross-image history leakage. Source-scoped changes through `c56f6cf` have not yet had CI evidence retrieved. Other non-photo non-timeline edit history is not state-restoring. |
| Audio editing | IMPLEMENTED | Concurrent audio tracks, volume and fade-in/fade-out via Media3 gain processing. Physical audio-sync validation remains. |
| Photo editing | IMPLEMENTED | Crop, filters, exposure/tone controls, highlights, shadows and sharpness with pixel-level unit tests. The unsharp-mask calculation and dimension overflow were corrected in #245. |
| Video effects | PARTIAL | Rotation, speed and color filters are present. Per-clip fade-in/fade-out is now implemented in the shared preview/export compositor path; cross-dissolve between adjacent clips remains unsupported. CI for this change is pending. |
| Text and image overlays | PARTIAL | Text overlays and visual PIP layers are composed; richer layer controls and arbitrary placement remain. |
| AI Studio | PARTIAL / HONESTLY LIMITED | Local luminance and frame-change heuristics only. No learned/generative provider is connected; speech-to-text/caption generation remains disabled. |
| Export | IMPLEMENTED / CI-VERIFIED | Media3 Transformer, WorkManager background job, progress, cancellation, immutable retry request, error handling and APK validation. Physical export quality and recovery still need device testing. |
| Accessibility, performance and security | PARTIAL | Basic contrast/touch-target work and bounded image analysis exist. Full accessibility, stress, privacy and security audits remain. |
| Release packaging | CI-VERIFIED, UNSIGNED | Debug and unsigned release APK artifacts build and validate. A production keystore and signed-release verification are still required. |

## 3. Recent code changes

- `36036ab`: added exact encoded-URI prefix predicates and source-specific Room DAO queries for photo history.
- `a9a956f`: routed photo snapshot recording, loading, undo, redo, and clearing through source-specific DAO operations.
- `6286280`: bound Photo Studio history observation and buttons to the selected image URI.
- `3911730`: centralized deterministic source URI token encoding.
- `52a20f7`: added tests for distinct URI tokens and the exact SQL prefix predicate.
- `c56f6cf`: stabilized the selected-source Flow key using a captured immutable URI value.
- `f7c8eaa`: configured the preview player with Media3 `MultipleInputVideoGraph.Factory` so multiple visual sequences can be composited in the editor preview.


- #244 cb12671: fixed WorkManager cancellation cleanup without reading unavailable WorkInfo.inputData; added ownership/path-safety tests for export request cleanup.
- #245 57eda58: corrected the four-neighbour unsharp-mask calculation; added uniform-region, exact-edge and pixel-count overflow tests.
- #246 e874fa5: separated general edit redo pruning from timeline snapshot redo pruning; removed unsupported SQLite UPDATE-target alias syntax from migration 10→11 and added SQL-contract tests.
- Current checkpoint update: makes legacy track-0 audio clips share one free audio track per project instead of accidentally assigning each clip to a different track; includes tests for free-track selection and full-track exhaustion.

## 4. Legacy CI backlog audit

The requested legacy commit SHAs were compared with current main; each is an ancestor of current main (behind_by = 0). Superseded commits must not be rerun.

- Already successful, not cancelled: #174, #177, #178.
- Cancelled and superseded by successful later CI: #175 → #176; #179 → #180; #181 and #183 → #185/#186; #188 → #189; #190/#191 → #192; #194 → #195; #196/#197 → #198 (with later track-position work in #208); #202 → #203; #204 → later successful migration/audio builds including #206 and #216.
- Failed but superseded: #182 and #184 → #185/#186; #205 → later successful builds including #206 and #216.

The earlier instruction incorrectly included #174, #177 and #178 among cancelled runs; GitHub records them as successful. All listed changes are retained in current main and covered by later successful runs; no obsolete commit rerun is needed.

## 5. Remaining work — do not mark complete

1. Verify the new per-clip video fade-in/fade-out implementation in CI, then validate fade rendering on a physical device. Cross-dissolve between adjacent clips remains a separate unsupported feature.
2. Validate legacy Room migration on Android API 26–current devices/emulators, including the current track-0 audio allocation behavior.
3. Extend state-restoring undo/redo to any remaining non-photo editing tools; photo edit state restoration is implemented in the current pending-CI change.
4. Validate timeline preview/export synchronization, audio mixing, cancellation and process-death recovery on physical devices.
5. Complete accessibility, large-project performance, storage-pressure and security/privacy audits.
6. Integrate AI only through a real, secure, consent-aware provider or on-device model; keep unavailable speech/cloud features disabled.
7. Generate and verify a production-signed release after the owner configures a private keystore. Never use the debug key for distribution.

## 6. Next action

1. Inspect the newest GitHub Actions runs for commits `36036ab` through `f7c8eaa`. The GitHub connector available in this session does not expose push-triggered run listings, so CI status remains unverified; do not infer success from empty commit-status responses.
2. If CI fails, inspect the failed job logs, fix the root cause, commit the repair, and verify the retry.
3. Physically validate multi-sequence preview after the `MultipleInputVideoGraph.Factory` change. Media3 documents multi-asset preview as an actively developing area; confirm playback, seeking, pause/resume, and overlays before treating it as verified.
4. After CI and device checks, continue with a real cross-dissolve between adjacent clips. Keep preview and export behavior identical and add deterministic opacity/composition tests.
5. Physical-device checks remain required for Room persistence, image switching, undo/redo, preview/export synchronization, audio, and process-death recovery.
