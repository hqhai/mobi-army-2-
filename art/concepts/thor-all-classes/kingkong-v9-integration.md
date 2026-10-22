# King Kong Thor v9 integration

Approved source: kingkong-native-ten-thor-v9-electric.png, copied unchanged into skins/thor-classes/kingkong-v9.png. Only VIP class3 / head30103 selects this asset. Stats, price, ownership and server catalog unchanged.

The JSON entry now defines ten source rectangles, a fixed 30x32 native canvas, left-facing source and no separate Iron Man weapon overlay. Native frames0–9 are preserved including hurt, collapse and celebration. Both look directions use the same source with horizontal mirroring. No per-frame auto-trim or rescaling, which would enlarge the collapsed pose or cause foot drift.

Runtime converts source RGB to RGBA and removes navy backdrop before nearest-neighbor sampling. Silver armor and electric-blue eyes/banana backpack come from approved artwork. Lightning is baked into the sprite; this change does not add an animated lightning or projectile effect.

Validation: desktop distZip passed; KingKongV9Check rendered every frame in both directions with native original above, saved kingkong-v9-runtime.png. Local launcher uses runtime/kingkong-v9. No remote package publication or push in this change.
