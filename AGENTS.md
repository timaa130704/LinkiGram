# LinkiGram — Agent Instructions

## Working workflow

- Continue on the user's PC in its existing checkout, directly on `main`.
  Do not create feature branches, worktrees or PRs unless explicitly requested.
- Read `git status` and recent commits before editing. Preserve all existing
  user changes. Fetch and fast-forward only when local work can be preserved;
  never reset, overwrite or discard it to match the remote.
- Discover the actual local paths and tools. Cloud-only `/workspace` paths and
  dependencies are not expected to exist on the user's PC. If no checkout is
  present, clone `https://github.com/timaa130704/LinkiGram.git` locally.
- Build Android APKs through `.github/workflows/build.yml` in GitHub Actions.
  Do not run local Gradle builds; the project workflow uses CI for them.
- Before pushing Java changes, check delimiters and referenced symbols. Check
  XML well-formedness after resource edits. Preserve the ProGuard keep rules
  required by Python plugins, particularly `org.telegram.**` names.
- Diagnose Android behavior using logcat. A successful build or install does
  not establish that a feature works on-device.
- When APK installation is requested, download the relevant CI artifact, verify
  package/version/signature and use local `adb install -r` to preserve app data.
  Check the actual device connection; cloud ADB limitations do not apply to a PC.
- Keep X-CFG, wsbypass and landing outside the Liner task.
- Do not publish releases or send announcements without explicit authorization.

## Current handoff: Liner lyrics

The Liner fixes are on `main`. Implementation commits:

- `9ad6a922`: separate bounded Liner/public-provider workers; invalidate player
  callbacks on cancellation/dismissal; align UI and coordinator timeouts; treat
  provider errors/truncated SSE as incomplete results rather than complete cache
  entries; invalidate older disk caches; remove the obsolete `/v1/search` step.
- `1d5dbb09`: release the catalog visitor-cache monitor before network I/O so a
  cancelled request cannot block the next track's lookup.

Validation already completed:

- 24 offline checks in `Tools/test_liner.py`, compiling the production API,
  catalog, aggregation, cache and workers against platform/network fakes.
- Existing lyrics-parser checks in `tools/liner-lyrics/check.cjs`.
- CI run **40085356687** at source
  `1d5dbb09981f8160d0c8e9f821e8323d45c4ba75` passed Java/Kotlin compilation,
  ARM64 APK assembly and artifact upload.
- Artifact **linkigram-apk**, ID **12557427588**. APK ZIP integrity and
  apksigner cryptographic signature verification passed.
- Package `app.linkigram.messenger`, version `2.6.5beta`, code `202602089`,
  ARM64. APK SHA-256:
  `8c97fa17304e230ab84d4d592b7c84783adae5bc629b1d0e35988b4894e26d09`.
- Signing certificate SHA-256:
  `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4`.

Next steps on the PC:

1. Download the CI artifact above and install its verified APK through local ADB.
   Installation was requested but could not be performed from the cloud machine.
2. With the existing Liner login and online lyrics enabled, check Liner providers
   for `9mice, Егор Крид, тёмный принц, madk1d — Jealous`.
3. Switch tracks rapidly and inspect `LinkiLyrics` logs. Confirm searches do not
   stall behind old requests and previous-track callbacks do not replace new text.
4. Investigate actual HTTP/SSE/provider failures if Liner sources remain absent.
   `/v1/search` was confirmed to return 404. Official client request signing
   differs from this integration, but enforcement on the authenticated lyrics
   request has not been established. Do not assume it is the cause without logs.

Real-account provider recovery and on-device track switching remain unverified.
The offline harness does not exercise Android WebView parsing or encrypted token
storage. No release was published for these changes.

Offline checks with JDK 17 and an existing org.json JAR:

```sh
python3 Tools/test_liner.py --json-jar /path/to/org-json.jar
node tools/liner-lyrics/check.cjs
```

## Privacy

This file is public on GitHub. Include only project instructions and technical
handoffs. Never put credentials, private machine paths, device identifiers or
personal chat history here. Keep private notes in ignored `AGENTS.local.md`.
Do not rewrite Git history as part of this task.
