# App signing

Everything below was verified on 2026-09-25 by reading the signer certificate
out of already-built APKs with `apksigner verify --print-certs`. Nothing here is
inferred.

## Two certificates exist

| | Original ("NimarkoGram") | Current ("LinkiGram") |
|---|---|---|
| Package | `app.linkigram.messenger` | `app.linkigram.messenger` |
| Version | 2.21 | 2.3 and later |
| DN | `CN=NimarkoGram Fork, OU=Dev, O=User, L=City, ST=Region, C=RU` | `CN=LinkiGram` |
| SHA-256 | `ce46ed301864cb802b9eba909d776707e42dc0b118c4c25ac7de35ce24535d49` | `627163ebca3d2b4d0ebe16a2c5aa3622b6eee0b90e425569da9924fca848b0b4` |
| SHA-1 | `a2064058853fec8fdb481956271741a7ee10d61d` | `f867ce335edd9fdbfdd4e1f5675194447eb5a697` |
| Alias | `androidkey` | `androidkey` |

## The original private key is gone

The key behind the `NimarkoGram Fork` certificate no longer exists on this
machine or in the repository. It was searched for across all local drives, the
full git history of every branch, agent logs, archives and the GitHub Actions
secrets. The `RELEASE_KEYSTORE_B64` secret was overwritten with a freshly
generated keystore on 2026-09-24T20:51Z while repairing CI, and no backup of
the previous payload was kept.

A private key cannot be recovered from a certificate or from a signed APK --
those only carry the public half. Signing 2.21's key is only possible if a copy
of the original `.keystore` turns up.

Note: a fingerprint circulated earlier for the original key
(`...77:67:07:42:DC:0B:11:8C:4C:25:AC:7D:E3:5C:E2:45:35:D:49`) is malformed --
31 byte pairs instead of 32. The value in the table above is read directly from
the APKs and is authoritative.

## Copies of the original signature still on disk

These four files are byte-identical builds of 2.21 and all carry the original
certificate. They are not usable for signing, but they are the reference for the
original app identity -- keep them:

```
C:\Users\dolbaeb\AppData\Local\hermes\cache\scratch\release_v221\LinkiGram-2.21.apk
C:\Users\dolbaeb\AppData\Local\hermes\cache\scratch\old_linkigram_2_21.apk
C:\Users\dolbaeb\AppData\Local\Cline\LinkiGram\apk-out\app.apk
C:\Users\dolbaeb\Downloads\linkigram-apk\app.apk
```

## Consequence for updates

Because the package name is unchanged but the certificate differs, Android
refuses to install 2.3+ as an update over 2.21 -- the signatures do not match
and the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Anyone moving
from 2.21 has to uninstall first, which discards local app data. There is no
workaround from inside the app; only restoring the original keystore would make
2.21 -> 2.3 a normal in-place update.

Treat the 2.21 -> 2.3 jump as a fresh install when writing release notes and
download-page instructions.

## What CI does

`.github/workflows/build.yml` decodes `RELEASE_KEYSTORE_B64` into
`TMessagesProj/config/release.keystore` and writes the `RELEASE_STORE_*` /
`RELEASE_KEY_*` values into a generated `private.properties`. Both signing configs
in `TMessagesProj_AppStandalone/build.gradle` read that path, so every build type
(including the release-like `standalone` one) is signed with the same key.

Rotating that secret changes the certificate of every future build, so treat it
as irreversible and back up the payload before overwriting.
