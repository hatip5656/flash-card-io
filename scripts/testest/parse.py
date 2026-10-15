"""Raw TESTEST pages -> structured tests with answer keys. Usage: parse.py <raw dir> <out json>
Each test: {ref, title, path, questions: [{qid, title, instruction, passage, stem, kind, media, points, items}]}
where each item is one scorable unit: {prompt, options[], correctIndex} or {prompt, kind:'text', answer}."""
import html, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ilias import text



def page_block(s, start_marker, end_marker):
    i = s.find(start_marker)
    j = s.find(end_marker, i)
    return s[i:j if j > 0 else None]


def media_of(block):
    found = []
    for u in re.findall(r'(?:src|href)="([^"]*data/integratsioon/mobs/[^"]+)"', block):
        rel = re.search(r'mobs/(.+?)(?:\?|$)', html.unescape(u)).group(1)
        if rel not in found: found.append(rel)
    return found


def strip_question(block):
    """Content of the page above the question widget: instruction headings, passages, media."""
    q = block.find('<div xmlns:xhtml="http://www.w3.org/1999/xhtml" class="ilc_question_Standard">')
    return block[:q] if q > 0 else block


def rich(s):
    """HTML -> text, keeping bold (the word to inflect in some tasks) as **word**."""
    s = re.sub(r'<strong>(.*?)</strong>', r'**\1**', s, flags=re.S)
    s = re.sub(r'<span[^>]*class="ilc_text_inline_Strong"[^>]*>(.*?)</span>', r'**\1**', s, flags=re.S)
    return text(s)


def clean(t):
    return re.sub(r'\n\s*\n+', '\n', re.sub(r'[ \t]*\n[ \t]*', '\n', t)).strip()


def figures_of(block):
    """Images with the label shown above them in their table column (picture tasks: 'A' over a photo).
    Images outside such tables come back with label None. Order follows the page."""
    labelled = {}
    for table in re.findall(r'<table.*?</table>', block, re.S):
        rows = [re.findall(r'<td[^>]*>(.*?)</td>', r, re.S) for r in re.findall(r'<tr[^>]*>(.*?)</tr>', table, re.S)]
        for above, row in zip(rows, rows[1:]):
            for col, cell in enumerate(row):
                imgs = media_of(cell)
                if not imgs or col >= len(above) or media_of(above[col]): continue
                label = clean(text(above[col]))
                if label and len(label) <= 3:
                    for m in imgs: labelled[m] = label
    return [{'media': m, 'label': labelled.get(m)} for m in media_of(block)]


def parse_qpage(s):
    block = page_block(s, 'id="ilc_Page"', 'tst_discard_solution_modal')
    title = text(re.search(r'<h1 class="ilc_page_title_PageTitle">(.*?)</h1>', block, re.S).group(1))
    above = strip_question(block)
    above = re.sub(r'^id="ilc_Page">|<h1 class="ilc_page_title_PageTitle">.*?</h1>|<a class="small" id="ilPageShowAdvContent".*?</a>', '', above, flags=re.S)
    kind = re.search(r'<div class="ilc_question_(\w+)">', block)
    kind = kind.group(1) if kind else None
    stem = re.search(r'<div class="ilc_qtitle_Title">(.*?)</div>', block, re.S)
    q = {'title': title, 'kind': kind, 'context': clean(rich(above)), 'stem': clean(rich(stem.group(1))) if stem else '',
         'media': media_of(block), 'figures': figures_of(block)}
    if kind == 'SingleChoice' or kind == 'MultipleChoice':
        q['options'] = []
        for m in re.finditer(r'<div class="ilc_qanswer_Answer">(.*?)</label>', block, re.S):
            label = re.search(r'<label[^>]*>(.*)', m.group(1), re.S).group(1)
            img = media_of(m.group(1))
            q['options'].append({'text': clean(rich(label)), 'image': img[0] if img else None})
    elif kind == 'ClozeTest':
        body = re.search(r'<div class="ilc_qanswer_Answer[^"]*ilAssClozeTest[^"]*">(.*)</div>\s*<div class="ilClearFloat">', block, re.S).group(1)
        gaps, parts, pos = [], [], 0
        for m in re.finditer(r'<select[^>]*name="gap_(\d+)"[^>]*>(.*?)</select>|<input[^>]*name="gap_(\d+)"[^>]*/?>', body, re.S):
            parts.append(body[pos:m.start()])
            pos = m.end()
            if m.group(1) is not None:
                opts = [clean(text(o)) for v, o in re.findall(r'<option value="(-?\d+)">(.*?)</option>', m.group(2), re.S) if v != '-1']
                gaps.append({'gap': int(m.group(1)), 'type': 'select', 'options': opts})
            else:
                gaps.append({'gap': int(m.group(3)), 'type': 'text'})
            parts.append(f'[[{len(gaps)}]]')
        parts.append(body[pos:])
        q['gaps'] = gaps
        q['clozeText'] = clean(rich(''.join(parts)))
    else:
        q['rawInputs'] = sorted(set(re.findall(r'name="([^"]+)"', block)))
    return q


def parse_solution(s):
    block = page_block(s, 'Õige vastus on', 'võimalikust punktist')
    pts = re.search(r'Said\s+([\d.,]+)\s+punkti\s+([\d.,]+)-st', s)
    sol = {'max_points': float(pts.group(2).replace(',', '.')) if pts else None}
    checked = []
    for m in re.finditer(r'<div class="ilc_qanswer_Answer">(.*?)</div>\s*</div>', block, re.S):
        if 'radiobutton_checked' in m.group(1) or 'checkbox_checked' in m.group(1):
            checked.append(m.group(1))
    sol['checked'] = []
    for m in re.finditer(r'<div class="ilc_qanswer_Answer">\s*<div>\s*<img[^>]*?(radiobutton|checkbox)_(checked|unchecked)[^>]*>\s*</div>\s*<div>(.*?)</div>\s*</div>', block, re.S):
        sol['checked'].append((m.group(2) == 'checked', clean(text(m.group(3))), media_of(m.group(3))))
    sol['gapAnswers'] = [clean(text(x)) for x in re.findall(r'<span class="ilc_qinput_TextInput solutionbox">(.*?)</span>', block, re.S)]
    sol['text'] = clean(text(block))[:4000]
    return sol


def details_rows(s):
    rows = []
    for m in re.finditer(r'evaluation=(\d+)&amp;cmd=outCorrectSolution', s):
        if m.group(1) not in rows: rows.append(m.group(1))
    return rows


def main(raw, out):
    tests = []
    for ref in sorted(os.listdir(raw), key=lambda x: int(x) if x.isdigit() else 0):
        d = os.path.join(raw, ref)
        if ref.startswith('lm') or not os.path.exists(os.path.join(d, 'done.json')): continue
        meta = json.load(open(os.path.join(d, 'done.json')))
        pages, seen = [], set()
        for f in sorted(x for x in os.listdir(d) if re.match(r'q\d+\.html', x)):
            s = open(os.path.join(d, f)).read()
            key = page_block(s, 'id="ilc_Page"', 'tst_discard_solution_modal')
            key = re.sub(r'il_wac_[a-z]+=[^&"]+', '', key)
            if key in seen: continue  # the player sometimes shows the same page twice
            seen.add(key)
            pages.append(parse_qpage(s))
        qids = details_rows(open(os.path.join(d, 'details.html')).read())
        sols = [parse_solution(open(os.path.join(d, f'sol{k:03d}.html')).read()) for k in range(meta['solutions'])]
        problems = []
        if len(pages) != len(sols):
            problems.append(f'{len(pages)} pages vs {len(sols)} solutions')
        for k, (q, sol) in enumerate(zip(pages, sols)):
            q['qid'] = qids[k] if k < len(qids) else None
            q['points'] = sol['max_points']
            items = []
            if q['kind'] in ('SingleChoice', 'MultipleChoice'):
                correct = [i for i, c in enumerate(sol['checked']) if c[0]]
                if len(sol['checked']) != len(q['options']) or not correct:
                    problems.append(f"{q['title']}: solution options {len(sol['checked'])} vs {len(q['options'])}, correct {correct}")
                items.append({'prompt': q['stem'], 'options': q['options'], 'correct': correct})
            elif q['kind'] == 'ClozeTest':
                if len(sol['gapAnswers']) != len(q['gaps']):
                    problems.append(f"{q['title']}: {len(sol['gapAnswers'])} gap answers vs {len(q['gaps'])} gaps")
                for g, ans in zip(q['gaps'], sol['gapAnswers']):
                    if g['type'] == 'select':
                        ci = g['options'].index(ans) if ans in g['options'] else -1
                        if ci < 0: problems.append(f"{q['title']} gap {g['gap']}: answer {ans!r} not in {g['options']}")
                        items.append({'gap': len(items) + 1, 'options': g['options'], 'correct': [ci]})
                    else:
                        items.append({'gap': len(items) + 1, 'kind': 'text', 'accepted': [a.strip() for a in ans.split(' või ') if a.strip()]})
            else:
                problems.append(f"{q['title']}: unhandled kind {q['kind']} inputs {q.get('rawInputs')}")
                q['solutionText'] = sol['text']
            q['items'] = items
        tests.append({**meta, 'questions': pages, 'problems': problems})
        print(ref, meta['title'], len(pages), 'pages', sum(len(q.get('items', [])) for q in pages), 'items', problems[:3])
    json.dump(tests, open(out, 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
