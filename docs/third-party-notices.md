# Third-party notices

## Zen Browser icons

The Space and Folder icons in `app/src/main/res/drawable/sideraven_zen_*.xml` are Zen Browser's
selectable icons (https://github.com/zen-browser/desktop, Mozilla Public License 2.0), as converted
to Android vector drawables by Z-Sync (https://github.com/Kjell6/Z-Sync, MIT; the icons themselves
remain MPL 2.0). Recoloured to black for tinting.

## Zen Sync protocol (Z-Sync)

The Zen Sync protocol code in `app/src/main/java/pt/dweep/sideraven/sync/` (record encryption, Hawk
signing, BSO ids, storage client, `spaces` record decoding) is ported from Z-Sync
(https://github.com/Kjell6/Z-Sync), Copyright (c) 2026 Kjell Behrends, MIT License. Its contract
fixtures are vendored under `app/src/testSideraven/resources/zen-contract/`.
