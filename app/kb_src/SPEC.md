# KB entry spec (one JSON file per crop: kb/<crop>.json = {"entries":[...]})
Each entry (exact class ids given in the task):
{
 "class_id": "groundnut__rust",
 "crop": "groundnut",
 "name_en": "Rust",
 "type": "fungal|bacterial|viral|pest|nutrient|healthy",
 "cause": "Puccinia arachidis (fungus)"   // one line, scientific name if applicable
 "symptoms": ["short farmer-friendly line", ... 3-5],
 "look_alikes": "one line: what it is confused with and how to tell apart (or null)",
 "cultural_actions": ["short imperative line", ...],       // field hygiene, resistant varieties, spacing, irrigation, rotation
 "treatment_options": [{"kind":"fungicide|insecticide|bio|nutrient","active_ingredient":"...","note":"FOLLOW PRODUCT LABEL; no dose","source_url":"...","cibrc_status":"registered_for_crop|not_found|unchecked"}],
 "when_to_act": "threshold / timing from the source (e.g. spray if >X% leaves, or at first appearance), or null if the source gives none",
 "ask_back": [{"question":"short yes/no question to confirm vs a look-alike","if_yes":"<class_id or 'consistent'>","if_no":"<class_id or 'uncertain'>"}],
 "sources": [{"title":"...","url":"...","publisher":"TNAU|ICAR|ICRISAT|CIB&RC|other"}],
 "caveats": "regional note, e.g. written for Tamil Nadu"
}
RULES: never write a dose, rate, concentration, ml/g/kg/ha, or days-to-harvest figure. Every statement must come from a page you actually opened (WebFetch). If a source does not support something, leave it out or null; never fill from memory. Prefer TNAU Agritech, ICAR, state agri universities, ICRISAT, CIB&RC. Skip commercial blogs unless nothing else exists and then mark publisher "other".
Healthy entries: type healthy; symptoms = what a healthy leaf looks like; cultural_actions = preventive scouting; no treatment.
Nutrient entries: say which deficiency signs the source lists, and "get a soil test (state soil testing lab/KVK) before applying any fertiliser"; no rates.
Write lines short (max ~18 words) - they will be read aloud.
