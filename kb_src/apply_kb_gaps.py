"""Merge the researched proposals (proposals/*.json) into assets/kb.json.
   python apply_kb_gaps.py <kb.json in> <proposals dir> <kb.json out> <report out>
Rules: existing treatments are kept (nothing already sourced is thrown away); new ones are added only if the chemical is not banned/restricted
(already checked by the research step) and carry a source URL; 'n/a' placeholders are dropped; cultural 'treatments' become cultural actions;
no doses; no-cure statements go to when_to_act; honest gaps go to caveats."""
import json, sys, os, re, glob, collections
kb_in, pdir, kb_out, rep_out = sys.argv[1:5]
D = json.load(open(kb_in, encoding='utf-8')); E = {e['class_id']: e for e in D['entries']}
P = {}
for f in sorted(glob.glob(os.path.join(pdir, '*.json'))):
    for c, v in json.load(open(f, encoding='utf-8')).items(): P[c] = v
CHECKED = "2026-10-09 against CIB&RC lists dated 31.03.2026 and 31.07.2026"
DOSE = re.compile(r'\d+(\.\d+)?\s*(ml|g|gm|kg|l|litre|liter|lit|ppm|%|per cent|percent|mg)\b', re.I)
rep = []; todo = collections.defaultdict(list)
def clean(s): return re.sub(r'\s+', ' ', (s or '').strip())
def add_unique(lst, s, key=lambda x: x.lower()):
    if s and key(s) not in {key(x) for x in lst}: lst.append(s); return True
    return False
CULT_SENT = {"cow dung paste on cut ends": "Cover the cut ends with cow dung paste after pruning.",
             "fresh cow dung slurry in the banana basin": "Some Indian pages suggest fresh cow dung slurry in the banana basin; this is a local practice, not a proven cure.",
             "plant oils on stored fruit (linseed, mustard or castor oil)": "For stored fruit, TNAU lists a coat of plant oil (linseed, mustard or castor oil) against black rot."}
for cid, p in P.items():
    e = E[cid]; log = []
    have = {t['active_ingredient'].lower() for t in e['treatment_options']}
    for t in p.get('treatment_options', []):
        ai = clean(t['active_ingredient']); lo = ai.lower()
        if lo.startswith('n/a') or 'not named' in lo: continue
        if t['kind'] == 'cultural':
            s = CULT_SENT.get(lo)
            if s and add_unique(e['cultural_actions'], s): log.append('cultural from treatment: ' + ai)
            continue
        if lo in have or not t.get('source_url'): continue
        st = t.get('cibrc_name_status', '')
        if st.startswith('REGISTERED_NAME'):
            status = 'name_on_cibrc_list_31.03.2026'; extra = ' Name is on the CIB&RC registered list (31.03.2026); crop and pest label claims not checked.'
        elif t['kind'] == 'nutrient': status = 'not_a_pesticide'; extra = ''
        elif t['kind'] == 'bio': status = 'traditional_or_biocontrol_not_a_listed_product'; extra = ''
        else: log.append('DROPPED (not on list): ' + ai); continue
        note = clean(t.get('note')) or 'FOLLOW PRODUCT LABEL; no dose'
        if not note.upper().startswith('FOLLOW PRODUCT LABEL'): note = 'FOLLOW PRODUCT LABEL; no dose. ' + note
        if 'carbofuran' in lo: note += ' Only the 3% granule (CG) form is still allowed in India (S.O. 4294(E), 2023).'
        e['treatment_options'].append({"kind": t['kind'], "active_ingredient": ai, "note": note + extra, "source_url": t['source_url'],
            "cibrc_status": status, "indian_source": bool(t.get('indian_source')), "cibrc_checked": CHECKED}); have.add(lo); log.append('treatment + ' + ai)
    for s in p.get('new_sources', []):
        if s.get('url') and s['url'] not in {x['url'] for x in e['sources']}: e['sources'].append({"title": clean(s['title']), "url": s['url'], "publisher": clean(s.get('publisher'))}); log.append('source + ' + s['url'][:60])
    for a in p.get('new_cultural_actions', []):
        a = clean(a)
        if a and len(e['cultural_actions']) < 12 and add_unique(e['cultural_actions'], a): log.append('cultural + ' + a[:50])
    nc = p.get('no_chemical')
    w = clean(p.get('when_to_act')) if p.get('when_to_act') else ''
    if nc:
        stmt = clean(nc['statement'])
        if stmt.lower() not in (e.get('when_to_act') or '').lower(): e['when_to_act'] = stmt + (' ' + e['when_to_act'] if e.get('when_to_act') else '')
        if nc.get('source_url') and nc['source_url'] not in {x['url'] for x in e['sources']}:
            e['sources'].append({"title": "Source for the no-cure statement", "url": nc['source_url'], "publisher": ""})
        log.append('no-chemical statement')
    if w and not e.get('when_to_act'): e['when_to_act'] = w; log.append('when_to_act set')
    # honest gap notes
    cav = e.get('caveats') or ''
    if not e['treatment_options'] and not nc and not e.get('when_to_act'):
        gap = 'No reliable Indian source with treatment advice was found for this problem. Ask your KVK before spraying anything.'
        if gap not in cav: e['caveats'] = (cav + ' ' + gap).strip(); log.append('gap caveat')
    elif e['treatment_options'] and not any(t['indian_source'] for t in e['treatment_options']):
        gap = 'The treatment names come from non-Indian sources. Check with your KVK first.'
        if gap not in cav and 'non-Indian' not in cav and 'No Indian' not in cav and 'Florida' not in cav: e['caveats'] = (cav + ' ' + gap).strip(); log.append('foreign caveat')
    for g in p.get('gaps', []): log.append('GAP: ' + g)
    rep.append((cid, log))
# mark treatments whose source is an Indian government / university / ICAR page
import urllib.parse
INDIAN = ('.gov.in', '.ac.in', '.icar.org.in', '.nic.in', 'celkau.in', 'ctcri.org', 'niphm.gov.in')
for e in E.values():
    for t in e['treatment_options']:
        host = urllib.parse.urlparse(t['source_url']).netloc.lower()
        if not t.get('indian_source') and any(host.endswith(d) or d in host for d in INDIAN): t['indian_source'] = True
# scan for dose-like text
bad = []
for e in E.values():
    for fld in ('cultural_actions',):
        for s in e[fld]:
            if DOSE.search(s): bad.append((e['class_id'], fld, s))
    for t in e['treatment_options']:
        if DOSE.search(t['note']): bad.append((e['class_id'], 'note', t['note']))
    if e.get('when_to_act') and DOSE.search(e['when_to_act']): bad.append((e['class_id'], 'when_to_act', e['when_to_act']))
json.dump(D, open(kb_out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
with open(rep_out, 'w', encoding='utf-8') as f:
    for cid, log in rep:
        f.write('## %s\n' % cid)
        for l in log: f.write('  - %s\n' % l)
    f.write('\nPOSSIBLE DOSE TEXT (review):\n')
    for b in bad: f.write('  %s %s: %s\n' % b)
print('classes touched', len(rep), '| dose-like strings', len(bad))
