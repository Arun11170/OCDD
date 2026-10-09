# Training data: sources and licences  (checked 6 Oct 2026)

Why this file exists: the hackathon requires entries to be original work that does not violate anyone's
intellectual property. Our model learns from other people's photos, so each dataset, its licence and what we
must do about it is listed here. "VERIFY" means I could not open the licence page myself (the page was not
reachable from my tools); open the link, copy the licence name into this table, and attribute the authors.

| Dataset (folder in data\raw) | Source | Licence | Status | Used for |
|---|---|---|---|---|
| PlantDoc | github.com/pratikkayal/PlantDoc-Dataset (Singh et al., CoDS-COMAD 2020, doi 10.1145/3371158.3371196) | CC BY 4.0 (stated in the repo's LICENSE and README) | Confirmed. Needs attribution. | Field photos of tomato, apple, potato, maize, etc. |
| Groundnut | Mendeley Data 22p2vcbxfk v3 | CC BY 4.0 (as noted when downloaded) | VERIFY, then attribute. | Groundnut diseases |
| Chilli (COLD) | doi 10.17632/tf9dtfz9m6 | CC BY-NC (as noted when downloaded) | VERIFY. NON-COMMERCIAL. | Chilli diseases |
| Okra | Mendeley Data nh7zk4hv8z v1 (SRM Urban Farm, Chengalpattu / Thanjavur; Read me.txt in the download) | CC BY-NC 4.0 (as noted when downloaded) | VERIFY. NON-COMMERCIAL. | Okra diseases |
| Okra YVMV | Kaggle manojgadde/yellow-vein-mosaic-disease | not checked | VERIFY | Okra yellow vein mosaic |
| Rice | Mendeley Data vwv3nry3wr v1 | "not stated" when downloaded | VERIFY. If no licence is found, ask the authors or drop it. | Rice diseases |
| Rice (small) | Mendeley Data z4hx2v3ywc v1 | not checked | VERIFY | Rice diseases |
| Tomato (GTLD) | Mendeley Data 2bdfjb99k5 v1 | not checked | VERIFY | Tomato diseases |
| Tomato (Maharashtra) | Mendeley Data rv3kxfv47y v1 | not checked | VERIFY | Tomato field photos |
| PlantVillage | Kaggle abdallahalidev/plantvillage-dataset and Hugging Face BrandonFors/Plant-Diseases-PlantVillage-Dataset (original: Hughes and Salathe 2015, arXiv:1511.08060) | check the Kaggle and Hugging Face pages | VERIFY. Cite the original paper. | Apple, grape, potato, tomato, capsicum etc. (lab photos) |
| IP102 | Kaggle rtlmhjbn/ip02-dataset (Wu et al., CVPR 2019) | research use per the authors; check the page | VERIFY. Cite the paper. | Pest classes |
| Mango | Kaggle aryashah2k/mango-leaf-disease-dataset (MangoLeafBD) | check the page | VERIFY | Mango diseases |
| Sugarcane | Kaggle nirmalsankalana/sugarcane-leaf-disease-dataset | check the page | VERIFY | Sugarcane diseases |
| Maize | Kaggle smaranjitghose/corn-or-maize-leaf-disease-dataset | check the page (it is built from PlantVillage and PlantDoc, so their terms also apply) | VERIFY | Maize diseases |
| Banana | Kaggle sujaykapadnis/banana-disease-recognition-dataset | check the page | VERIFY | Banana diseases |
| Cotton, wheat | download failed or not used | n/a | Not in the model. | n/a |

## What this means
1. Hackathon / research: all of the above can be used if the authors are credited and each licence is followed.
2. A real, commercial release: CC BY-NC data (chilli, okra, and any other set that turns out NC) may not be used for a
   product sold for money. Before that, replace those sets, get written permission, or keep the app free and non-commercial.
3. The trained model is built from this data, so it carries the same conditions. Say so in the report.
4. We never redistribute the photos. Only the trained model (4-17 MB) ships in the app.
5. The model started from ImageNet-pretrained MobileNetV3 weights (torchvision). Mention this in the report.

## To finish (about 20 minutes)
Open each link above, write the exact licence name in the "Licence" column, change VERIFY to Confirmed, and copy the
citation (authors, year, link) into the report's references. Remove any dataset with no licence and no reply from the authors.


## Added for the lesion finder and extra field photos
| Dataset | What it is used for | Licence | Credit |
|---|---|---|---|
| PlantSeg (Zenodo 16665770) | lesion outlines to train the damage highlighter; healthy/diseased field photos | CC BY 4.0 | Wei, T. et al., "PlantSeg: A Large-Scale In-the-wild Dataset for Plant Disease Segmentation", 2024 |
| PlantWild (Hugging Face uqtwei2/PlantWild), optional | extra in-the-wild disease photos | CC BY-NC-ND 4.0 (non-commercial, no derivatives of the dataset itself) | Wei, T. et al., 2024 |
| IP102 (already in data/raw/ip102) | pest photos | research / non-commercial use; check the current terms | Wu, X. et al., CVPR 2019 |

Only models trained on the data ship in the app, never the photos themselves. Licence text was read from each dataset's own page on 2026-10-07; re-check before any commercial release.
