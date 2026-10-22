# Thor variants for all playable classes

Status: concept review, not installed in the game.

Native reference was rendered from the game's equipment catalog using ThorClassReference.java. Each class has frame 0 and frame 3 facing right. Read left to right, first group classes 0–4, second group 5–9.

| Class | Iron Man weapon/head/body IDs | Design constraint |
| --- | --- | --- |
| Gunner | 28 / 26 / 27 | Compact gun and native firing anchor |
| Miss 6 | 21 / 19 / 20 | Long gun; feminine Thor face |
| Electrician | 25 / 23 / 24 | Native emitter silhouette |
| King kong | 26 / 24 / 25 | Broader native body and raised arm |
| Rocketer | 27 / 25 / 26 | Keep launcher silhouette |
| Granos | 23 / 21 / 22 | Keep handheld weapon and hand anchor |
| Chicky | 23 / 21 / 22 | Preserve class-specific weapon contour |
| Tarzan | 24 / 22 / 23 | Preserve curved weapon and compact stance |
| Apache | 24 / 22 / 23 | Throwing stance, Thor hammer |
| Magenta | 21 / 19 / 20 | Original laser gun; feminine Thor face |

Shared identity: silver winged helmet, exposed face and blond hair, dark silver armor with round discs, short red cape. Native head-heavy chibi scale. Upward aim tilts the head and torso with the weapon.

Production requires per-class pose/offset checks against native equipment, two distinct walking phases, both facing directions, and separate hurt/death/victory handling. A generated overview is not evidence of pixel-perfect alignment or a usable runtime atlas. Preserve native weapons during actual composition unless separately approved.
