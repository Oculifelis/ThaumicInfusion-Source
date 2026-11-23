# 1.21.1 build

Same features. Nothing cut.

Changes: Essentia Block tints moved from item model definitions to color handlers in code (block from its block
entity, item from its first aspect); render-look mixin targets `RenderChunkRegion`; block entity save/load with
CompoundTag; attachments saved with plain codecs (save key layout differs from the 26.1.2 build, worlds don't carry
over between the two); spawner/lightning effects use `MobSpawnType`/`moveTo`; JEI page uses the JEI 19 API.
