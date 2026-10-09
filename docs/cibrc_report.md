# Chemical check against the official CIB&RC lists

Checked on 2026-10-06 against the files you saved: *List of pesticides as on 31.03.2026* (registered names), *Banned, refused registration and restricted in use* (updated 31.07.2026) and *Pesticide formulations registered as on 31.03.2026*.

## What the check can and cannot say

- It confirms a chemical's **name** is on the registered list, or is banned or restricted for a crop.
- It does **not** confirm the product is registered for **this crop and this disease or pest**. That needs the CIB&RC *Major Uses of Pesticides* list, which was not among the files. Doses stay out of the app until a KVK has checked the entries.

## Counts (all 251 options)

| Result | Count |
| --- | --- |
| registered | 198 |
| traditional_or_biocontrol | 18 |
| banned | 17 |
| not_on_list | 7 |
| not_a_pesticide | 5 |
| group_name | 3 |
| restricted_conflict | 3 |

## Removed from the app (26 options)

| Class | Option removed | Why |
| --- | --- | --- |
| chilli__powdery_mildew | Dinocap | Banned in India |
| groundnut__rust | Tridemorph | Banned in India |
| mango__cutting_weevil | Carbaryl | Banned in India |
| mango__cutting_weevil | Dimethoate | Official list bans it on this kind of crop |
| mango__gall_midge | Dimethoate | Official list bans it on this kind of crop |
| mango__powdery_mildew | Tridemorph | Banned in India |
| mango__powdery_mildew | Dinocap (karathane) | Banned in India |
| pest__flea_beetle | Malathion dust | Official list bans it on this kind of crop |
| pest__flea_beetle | Carbaryl | Banned in India |
| pest__gall_midge_dasineura | Dimethoate | Official list bans it on this kind of crop |
| pest__gall_midge_dasineura | Dimethoate | Official list bans it on this kind of crop |
| pest__mango_borer | Carbaryl | Banned in India |
| pest__mango_borer | Dichlorvos on cotton wool in cleaned stem holes | Banned in India |
| pest__mango_borer | Malathion on cotton wick in cleaned stem holes | Official list bans it on this kind of crop |
| pest__mango_seed_weevil | Dimethoate | Official list bans it on this kind of crop |
| pest__mango_seed_weevil | Carbaryl | Banned in India |
| pest__oriental_fruit_fly | Malathion in methyl eugenol trap or in bait | Official list bans it on this kind of crop |
| pest__planthopper | Carbaryl | Banned in India |
| pest__planthopper | Dichlorvos | Banned in India |
| pest__red_spider_mite | Dicofol | Banned in India |
| pest__white_grub | Phorate | Banned in India |
| rice__rice_tungro | Phorate | Banned in India |
| sugarcane__rust | Tridemorph | Banned in India |
| sugarcane__yellow | Dimecron | Banned in India |
| sugarcane__yellow | Phorate | Banned in India |
| tomato__yellow_leaf_curl_virus | Dimethoate (whitefly vector) | Official list bans it on this kind of crop |

## Edited

- chilli__murda_complex: Dimethoate removed from mixed option (banned on raw-eaten vegetables)

## Left in, but a KVK should look at them

- Dimethoate (still in pest__thrips, pest__corn_borer): the list bans it on fruits and vegetables eaten raw, so it must not be used on those.
- Chlorpyrifos: banned on ber, citrus and tobacco only.
- Mancozeb: banned on guava, jowar and tapioca only.
- Quinalphos: banned on jute, cardamom and sorghum only.
- Not found on the registered list: Chlorfenapyr, Propargite (inside a mixed option), Bordeaux mixture, petroleum oil, Teepol, protein-bait sprays. Bordeaux mixture is a traditional copper preparation, not a listed product name.
- Group names with no product: strobilurin fungicides, triazole fungicides, copper fungicides.
- Every kept option now has `cibrc_status` and `cibrc_checked` in `kb.json` and `kb_hi.json`. The app shows a short line under each chemical name.
