"""parsed.json -> official_exams rows (seed format) + re-encoded media.
Usage: convert.py <parsed.json> <raw dir> <out dir>
Writes <out>/testest-exams.json and <out>/media/testest/<mm_x>/<file>."""
import json, os, re, shutil, subprocess, sys

parsed, raw, out = sys.argv[1:4]
tests = json.load(open(parsed))
media_out = os.path.join(out, 'media')
SOURCE = 'TESTEST (Integratsiooni Sihtasutus)'
SKILLS = {'lugemis': 'reading', 'kuulamis': 'listening', 'kirjutamis': 'writing', 'Sõeltest': 'grammar'}
SKILL_ET = {'reading': 'Lugemine', 'listening': 'Kuulamine', 'writing': 'Kirjutamine', 'grammar': 'Grammatika ja sõnavara'}
SKILL_TR = {'reading': 'Okuma', 'listening': 'Dinleme', 'writing': 'Yazma', 'grammar': 'Dilbilgisi ve kelime'}
SKILL_EN = {'reading': 'Reading', 'listening': 'Listening', 'writing': 'Writing', 'grammar': 'Grammar & vocabulary'}

media_map = {}


def duration(path):
    r = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except ValueError:
        return 0.0


def media_key(rel, ref):
    """mm_823/Some name kaks korda.mp3 -> testest/mm_823/some_name_kaks_korda.mp3, converting on first use."""
    if rel in media_map: return media_map[rel]
    folder, name = rel.split('/', 1)
    base, ext = os.path.splitext(name)
    safe = re.sub(r'[^a-z0-9]+', '_', base.lower().encode('ascii', 'ignore').decode() or 'file').strip('_') + ext.lower()
    key = f'testest/{folder}/{safe}'
    src = os.path.join(raw, ref, 'media', rel)
    dest = os.path.join(media_out, key)
    if not os.path.exists(dest):
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if ext.lower() == '.mp3':
            # Speech only: mono 64 kbps keeps it clear at about a third of the size.
            subprocess.run(['ffmpeg', '-loglevel', 'quiet', '-y', '-i', src, '-ac', '1', '-b:a', '64k', dest], check=True)
            # A few originals have a damaged frame that stops the decoder early; keep those as they are.
            if duration(dest) < 0.95 * duration(src):
                print('kept original (re-encode came out short):', rel)
                shutil.copyfile(src, dest)
        else:
            shutil.copyfile(src, dest)
    media_map[rel] = key
    return key


def split_media(q, ref):
    keys = [media_key(m, ref) for m in q['media']]
    option_imgs = {o['image'] for o in q.get('options', []) if o.get('image')}
    option_keys = {media_key(m, ref) for m in option_imgs}
    audio = [k for k in keys if k.endswith('.mp3')]
    images = []
    for f in q['figures']:
        k = media_key(f['media'], ref)
        if k.endswith('.mp3') or k in option_keys: continue
        images.append({'src': k, 'label': f['label']} if f['label'] else {'src': k})
    return audio, images


def task_label(title):
    m = re.search(r'(\d+)\.\s*ülesanne', title)
    return f'{m.group(1)}. ülesanne' if m else title


def first_number(title, gaps):
    """Official number of the first gap, when the page's question range has one number per gap."""
    m = re.search(r'(\d+)\.(?:\s*-\s*(\d+)\.)?\s*küsimus', title)
    if not m: return None
    start, end = int(m.group(1)), int(m.group(2) or m.group(1))
    return start if end - start + 1 == gaps else None


def gap_line(cloze, k, label):
    """The line of the cloze text holding gap k, with that gap shown as __(label)__ and others as ___."""
    for line in cloze.split('\n'):
        if f'[[{k}]]' in line:
            line = line.replace(f'[[{k}]]', f'__({label})__')
            return re.sub(r'\[\[\d+\]\]', '___', line).strip()
    return f'__({label})__'


def cloze_display(cloze, start):
    return re.sub(r'\[\[(\d+)\]\]', lambda m: f'__({start + int(m.group(1)) - 1 if start else m.group(1)})__', cloze)


def convert_pages(t, section_override=None):
    ref = t['ref']
    questions = []
    for g, q in enumerate(t['questions']):
        audio, images = split_media(q, ref)
        section = section_override or task_label(q['title'])
        group = f'{ref}-{g}'
        common = {'section': section, 'group': group, 'sourceTitle': q['title']}
        if audio: common['audio'] = audio
        if images: common['images'] = images
        if q['kind'] in ('SingleChoice', 'MultipleChoice'):
            it = q['items'][0]
            opts = [o['text'] for o in q['options']]
            opt_imgs = [media_key(o['image'], ref) if o.get('image') else None for o in q['options']]
            if any(opt_imgs):
                opts = [o or chr(65 + i) for i, o in enumerate(opts)]
            prompt = q['stem'] or ''
            context = q['context']
            if not prompt:
                # Instruction-only pages: the last line of the context is the question.
                prompt = q['title']
            questions.append({**common, 'type': 'multiple_choice', 'prompt': prompt, 'context': context or None,
                              'options': opts, **({'optionImages': opt_imgs} if any(opt_imgs) else {}),
                              'correctIndex': it['correct'][0], 'explanation': ''})
        else:
            start = first_number(q['title'], len(q['items']))
            cloze = q['clozeText']
            context = '\n\n'.join(x for x in (q['context'], q['stem'], cloze_display(cloze, start)) if x)
            for k, it in enumerate(q['items'], start=1):
                label = start + k - 1 if start else k
                prompt = gap_line(cloze, k, label)
                if re.fullmatch(r'[\d.\s]*__\(\d+\)__', prompt):
                    prompt = f'Lünk {label}'
                base = {**common, 'prompt': prompt, 'context': context, 'gap': label, 'explanation': ''}
                if it.get('kind') == 'text':
                    questions.append({**base, 'type': 'text_gap', 'options': [], 'accepted': it['accepted'],
                                      'correctIndex': -1})
                else:
                    questions.append({**base, 'type': 'multiple_choice', 'options': it['options'],
                                      'correctIndex': it['correct'][0]})
    return questions


def time_limit(t):
    """The duration the test's own info page states ("... kestab 25 minutit"), or None (untimed)."""
    info = open(os.path.join(raw, t['ref'], 'info.html'), encoding='utf-8', errors='ignore').read()
    body = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', info))
    m = re.search(r'(\d+)\s*minut', body)
    return int(m.group(1)) if m else None


NEXT_LEVEL = {'A2': 'B1', 'B1': 'B2', 'B2': 'C1', 'C1': None}


def level_of(t):
    m = re.search(r'\b(A2|B1|B2|C1)\b', t['title'])
    return m.group(1) if m else None


def skill_of(t):
    for k, v in SKILLS.items():
        if k in t['title']: return v
    return None


def variant_of(t):
    m = re.search(r'(\d)\. variant', t['title'])
    return int(m.group(1)) if m else None


exams = []
# Screening: parts 1 and 2 of a variant become one exam; part 2 counts only past the official gate.
screening = {}
for t in tests:
    if 'Sõeltest' in t['title']:
        part = 1 if '1. osa' in t['title'] else 2
        screening.setdefault(variant_of(t), {})[part] = t
for v, parts in sorted(screening.items()):
    qs = convert_pages(parts[1], '1. osa') + convert_pages(parts[2], '2. osa')
    exams.append({
        'id': f'testest-screening-{v}', 'level': 'ALL', 'kind': 'screening', 'skill': 'grammar', 'variant': v,
        'title': f'Sõeltest ({v}. variant)', 'titleTr': f'Seviye belirleme testi ({v}. varyant)',
        'titleEn': f'Placement test (variant {v})', 'source': SOURCE, 'sortOrder': v,
        'timeLimitMinutes': time_limit(parts[1]),
        'scoring': {
            'type': 'screening',
            # Official TESTEST rule: under 35 in part 1, part 2 is not needed.
            'gate': {'section': '1. osa', 'minScore': 35},
            # Our estimate (not published by TESTEST): first rule whose ranges all hold wins.
            'placement': [
                # Diagnostics start at A2, so an A1 estimate continues there.
                {'level': 'A1', 'recommendedLevel': 'A2', 'ranges': {'1. osa': [0, 19]}},
                {'level': 'A2', 'ranges': {'1. osa': [20, 34]}},
                {'level': 'B1', 'ranges': {'2. osa': [0, 9]}},
                {'level': 'B2', 'ranges': {'2. osa': [10, 17]}},
                {'level': 'C1', 'ranges': {'2. osa': [18, 25]}},
            ]},
        'questions': qs})

ORDER = {'A2': 1, 'B1': 2, 'B2': 3, 'C1': 4}
SK_ORDER = {'reading': 1, 'listening': 2, 'writing': 3}
for t in tests:
    if 'Sõeltest' in t['title']: continue
    lvl, sk, v = level_of(t), skill_of(t), variant_of(t)
    sample = 'näidis' in ' '.join(t['path'])
    kind = 'sample' if sample else 'diagnostic'
    eid = f'testest-{kind}-{lvl.lower()}-{sk}' + (f'-{v}' if v else '')
    if sample:
        title, tr, en = (f'{lvl} tasemeeksami näidistest: {SKILL_ET[sk].lower()}',
                         f'{lvl} sınav örneği: {SKILL_TR[sk].lower()}', f'{lvl} sample exam: {SKILL_EN[sk].lower()}')
    else:
        title, tr, en = (f'{lvl} diagnoostest: {SKILL_ET[sk].lower()} ({v}. variant)',
                         f'{lvl} tanı testi: {SKILL_TR[sk].lower()} ({v}. varyant)',
                         f'{lvl} diagnostic: {SKILL_EN[sk].lower()} (variant {v})')
    exams.append({'id': eid, 'level': lvl, 'kind': kind, 'skill': sk, 'variant': v, 'title': title, 'titleTr': tr,
                  'titleEn': en, 'source': SOURCE,
                  'sortOrder': ORDER[lvl] * 100 + SK_ORDER[sk] * 10 + (v or 0),
                  'timeLimitMinutes': time_limit(t),
                  'scoring': {'type': 'level_check', 'level': lvl, 'passMark': 0.6, 'nextLevel': NEXT_LEVEL[lvl]},
                  'questions': convert_pages(t)})

os.makedirs(out, exist_ok=True)
json.dump(exams, open(os.path.join(out, 'testest-exams.json'), 'w'), ensure_ascii=False, indent=1)
n = sum(len(e['questions']) for e in exams)
print(len(exams), 'exams,', n, 'items,', len(media_map), 'media files')
for e in exams:
    types = {}
    for q in e['questions']: types[q['type']] = types.get(q['type'], 0) + 1
    print(e['id'], len(e['questions']), types)


# ── Writing and speaking tasks (ILIAS learning modules): prompt, model answer, examiner comment ──

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from parse import rich, clean, figures_of, text  # noqa: E402


def lm_text(fragment):
    return clean(rich(re.sub(r'<a class="small" id="ilPageShowAdvContent".*?</a>', '', fragment, flags=re.S)))


def lm_media(fragment, ref):
    audio, images = [], []
    for f in figures_of(fragment):
        k = media_key(f['media'], ref)
        if k.endswith('.mp3'):
            audio.append(k)
        else:
            images.append({'src': k, 'label': f['label']} if f['label'] else {'src': k})
    return audio, images


def lm_page(html_, ref):
    title = re.search(r'<h1 class="ilc_page_title_PageTitle">(.*?)</h1>', html_, re.S)
    end = html_.find('ilc_page_bnav_BottomNavigation')
    if end > 0: end = html_.rfind('<div', 0, end)
    body = html_[html_.find('<!--COPage-PageTop-->'):end if end > 0 else None]
    acc = body.find('ilc_va_cntr_VAccordCntr')
    if acc > 0: acc = body.rfind('<div', 0, acc)  # split at the panel's opening tag
    prompt_html = body[:acc] if acc > 0 else body
    answer_html = body[acc:] if acc > 0 else ''
    answer_html = re.sub(r'<script.*?</script>', '', answer_html, flags=re.S)
    caption = re.search(r'<div class="ilc_va_ihcap_VAccordIHeadCap">(.*?)</div>', answer_html, re.S)
    caption = text(caption.group(1)) if caption else ''
    answer_html = re.sub(r'<div class="ilc_va_ihcap_VAccordIHeadCap">.*?</div>', '', answer_html, flags=re.S)
    # Drop the "click 'model answer and comment'" hint that only makes sense on the website
    prompt_html = re.sub(r'<div class="ilc_Paragraph[^"]*">(?:(?!</div>).)*?(?:klõpsa hiirega|Pildi suurendamiseks|Kommentaari lugemiseks)(?:(?!</div>).)*</div>', '', prompt_html, flags=re.S)
    p_audio, p_images = lm_media(prompt_html, ref)
    a_audio, a_images = lm_media(answer_html, ref)
    answer, comment = None, None
    if answer_html and 'Näidisvastus' not in caption:
        # A panel captioned just "Kommentaar": tips for the task, no model answer
        comment = re.sub(r'^\*\*\s*Kommentaar\s*\*\*\s*', '', lm_text(answer_html)).strip() or None
    elif answer_html:
        text_ = lm_text(answer_html)
        m = re.split(r'\*\*\s*Kommentaar\s*\*\*', text_, maxsplit=1)
        answer = re.sub(r'^\*\*\s*Näidisvastus\s*\*\*\s*', '', m[0])
        answer = re.sub(r'^Kuula näidisvastust[^\n]*\n?', '', answer.strip()).strip() or None
        comment = m[1].strip() if len(m) > 1 and m[1].strip() else None
    return {'title': text(title.group(1)) if title else '', 'prompt': lm_text(prompt_html),
            'audio': p_audio, 'images': p_images,
            'modelAnswer': answer, 'modelAudio': a_audio, 'modelImages': a_images, 'comment': comment}


def convert_lm(path):
    d = json.load(open(path))
    ref = 'lm' + d['ref']
    title = d['title'].replace('Eesti keele tasemetestid - ', '').strip()
    t = {'title': title, 'path': []}
    lvl, v = level_of(t), variant_of(t)
    skill = 'writing' if 'kirjutamis' in title else 'speaking'
    pages, seen = [], set()
    for p in d['pages']:
        page = lm_page(p['html'], ref)
        key = (page['title'], page['prompt'][:200])
        if key in seen or not page['prompt']: continue
        seen.add(key)
        pages.append(page)
    # Task pages are titled "N. ülesanne"; the module's overview page is titled after the module
    def number(p):
        m = re.match(r'(\d+)\.\s*ülesanne', p['title'])
        return int(m.group(1)) if m else None
    intro = next((p for p in pages if number(p) is None), None)
    tasks = sorted((p for p in pages if number(p) is not None), key=number)
    limit = re.search(r'(\d+)\s*minut', intro['prompt']) if intro else None
    kind = 'diagnostic' if v else 'sample'
    sk_et = {'writing': 'kirjutamine', 'speaking': 'rääkimine'}[skill]
    sk_tr = {'writing': 'yazma', 'speaking': 'konuşma'}[skill]
    sk_en = {'writing': 'writing', 'speaking': 'speaking'}[skill]
    suffix = f' ({v}. variant)' if v else ''
    return {
        'id': f'testest-{kind}-{lvl.lower()}-{skill}' + (f'-{v}' if v else ''),
        'kind': kind, 'skill': skill, 'level': lvl, 'variant': v, 'source': SOURCE,
        'title': f'{lvl} {"diagnoostest" if v else "tasemeeksami näidistest"}: {sk_et}{suffix}',
        'titleTr': f'{lvl} {"tanı testi" if v else "sınav örneği"}: {sk_tr}' + (f' ({v}. varyant)' if v else ''),
        'titleEn': f'{lvl} {"diagnostic" if v else "sample exam"}: {sk_en}' + (f' (variant {v})' if v else ''),
        'sortOrder': ORDER[lvl] * 100 + (40 if skill == 'writing' else 50) + (v or 0),
        'timeLimitMinutes': int(limit.group(1)) if limit else None,
        'intro': intro['prompt'] if intro else None,
        'tasks': tasks,
    }


import glob  # noqa: E402
tasks_out = [convert_lm(p) for p in sorted(glob.glob(os.path.join(raw, 'lm*', 'done.json')))]
json.dump(tasks_out, open(os.path.join(out, 'testest-tasks.json'), 'w'), ensure_ascii=False, indent=1)
print(len(tasks_out), 'writing/speaking sets,', sum(len(t['tasks']) for t in tasks_out), 'tasks')
for t in tasks_out:
    print(t['id'], len(t['tasks']), [bool(x['modelAnswer'] or x['modelAudio']) for x in t['tasks']], t['timeLimitMinutes'])
