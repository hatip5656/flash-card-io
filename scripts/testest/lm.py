"""Save every page (and its media) of the writing/speaking learning modules. Usage: lm.py <ref,ref,...> <out dir>"""
import html, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ilias import Session, text


def save_media(s, page, out):
    """Media links are signed with a short TTL, so fetch them while the page is fresh. Saved under the
    mob path (mm_823/name.mp3) so pages can be mapped back to their files."""
    for u in dict.fromkeys(re.findall(r'(?:src|href)="([^"]*data/integratsioon/mobs/[^"]+)"', page)):
        u = html.unescape(u)
        rel = re.search(r'mobs/(.+?)(?:\?|$)', u).group(1)
        dest = os.path.join(out, 'media', rel)
        if os.path.exists(dest): continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        url = u if u.startswith('http') else 'https://web.meis.ee/testest/' + u.lstrip('./')
        url = url.replace(' ', '%20')
        open(dest, 'wb').write(s.get_bytes(url))



refs, out_root = sys.argv[1].split(','), sys.argv[2]
s = Session(delay=0.6)
s.get('ilias.php?baseClass=ilrepositorygui&reloadpublic=1&cmd=frameset&ref_id=1')
for ref in refs:
    out = os.path.join(out_root, 'lm' + ref)
    if os.path.exists(os.path.join(out, 'done.json')): continue
    os.makedirs(out, exist_ok=True)
    first = s.get(f'ilias.php?baseClass=ilLMPresentationGUI&ref_id={ref}&cmd=resume')
    queue, seen, pages = [None], set(), []
    title = re.search(r'<title>(.*?)</title>', first, re.S)
    while queue:
        oid = queue.pop(0)
        if oid in seen: continue
        seen.add(oid)
        p = first if oid is None else s.get(
            f'ilias.php?ref_id={ref}&obj_id={oid}&cmd=layout&cmdClass=illmpresentationgui&cmdNode=11n&baseClass=ilLMPresentationGUI')
        save_media(s, p, out)
        i = p.find('id="ilLMPageContent"')
        body = p[i:p.find('Püsilink', i)] if i > 0 else ''
        pages.append({'obj_id': oid, 'html': body, 'text': re.sub(r'\s*\n\s*', '\n', text(body))})
        for o in re.findall(rf'ref_id={ref}&amp;obj_id=(\d+)', p):
            if o not in seen: queue.append(o)
    json.dump({'ref': ref, 'title': text(title.group(1)) if title else '', 'pages': pages},
              open(os.path.join(out, 'done.json'), 'w'), ensure_ascii=False, indent=1)
    print(ref, len(pages), 'pages', flush=True)
