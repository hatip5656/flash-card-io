"""Walk the repository categories from the given roots; write every test (ref id, title, folder path) to JSON."""
import html, json, re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ilias import Session, text

roots, out = sys.argv[1].split(','), sys.argv[2]
s = Session(delay=0.5)
s.get('ilias.php?baseClass=ilrepositorygui&reloadpublic=1&cmd=frameset&ref_id=1')
NAV = {'1', '70', '71', '72', '73'}
tests, seen = [], set()


def walk(ref, path):
    if ref in seen: return
    seen.add(ref)
    page = s.get(f'ilias.php?ref_id={ref}&cmd=render&cmdClass=ilrepositorygui&cmdNode=il&baseClass=ilrepositorygui')
    i = page.find('il_center_col')
    body = page[i:page.find('Püsilink', i)]
    items = []
    for u, t in re.findall(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', body, re.S):
        u, t = html.unescape(u), text(t)
        if not t or t == 'Info': continue
        m = re.search(r'ref_id=(\d+)', u)
        if not m: continue
        items.append((m.group(1), t, 'test' if 'ilobjtestgui' in u else 'cat' if 'cmd=render' in u else 'other:' + u[:80]))
    for rid, t, kind in dict.fromkeys(items):
        if kind == 'test':
            if rid not in {x['ref'] for x in tests}:
                tests.append({'ref': rid, 'title': t, 'path': path})
        elif kind == 'cat' and rid not in NAV and rid != ref:
            walk(rid, path + [t])
        elif kind.startswith('other'):
            print('skipped', rid, t, kind)


for r in roots:
    walk(r, [{'71': 'Sõeltestid', '72': 'Diagnoostestid', '73': 'Tasemeeksami näidistestid'}.get(r, r)])
json.dump(tests, open(out, 'w'), ensure_ascii=False, indent=1)
for t in tests: print(t['ref'], ' / '.join(t['path']), '|', t['title'])
print(len(tests), 'tests')
