"""For every test in tests.json: take a guest pass without answering, finish it, and save the question pages,
the pass details and every correct-solution page as raw HTML. Resumable: a test with a done.json is skipped."""
import html, json, os, re, sys, traceback
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ilias import Session, form_with, nav_href

tests_file, out_root = sys.argv[1], sys.argv[2]
only = set(sys.argv[3].split(',')) if len(sys.argv) > 3 else None


def submit(s, page, nextcmd):
    form = form_with(page, 'formtimestamp')
    nav = nav_href(page, nextcmd)
    return s.get(form['action'], {**form['hidden'], 'test_player_navigation_url': nav, 'cmd[submitSolution]': 'Save'})


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


def scrape(t):
    out = os.path.join(out_root, t['ref'])
    if os.path.exists(os.path.join(out, 'done.json')): return 'cached'
    os.makedirs(out, exist_ok=True)
    s = Session(delay=0.6)
    info, page = s.start_test(t['ref'])
    open(f'{out}/info.html', 'w').write(info)
    n = 0
    while True:
        if 'id="taForm"' not in page:
            break
        open(f'{out}/q{n:03d}.html', 'w').write(page)
        save_media(s, page, out)
        n += 1
        if n > 300: raise RuntimeError('runaway')
        page = submit(s, page, 'nextQuestion' if nav_href(page, 'nextQuestion') else 'finishTest')
    # A confirmation step may sit between the last question and the results.
    for _ in range(3):
        if 'outUserPassDetails' in page: break
        m = re.search(r'name="(cmd\[(?:confirmFinish|finishTest)[^\]]*\])"[^>]*value="([^"]*)"', page)
        if not m: break
        form = form_with(page, m.group(1))
        page = s.get(form['action'], {**form['hidden'], m.group(1): html.unescape(m.group(2))})
    open(f'{out}/finish.html', 'w').write(page)
    m = re.search(r'href="([^"]*cmd=outUserPassDetails[^"]*)"', page)
    if not m: raise RuntimeError(f'no pass details after {n} pages; landed on {s.url}')
    details = s.get(html.unescape(m.group(1)))
    open(f'{out}/details.html', 'w').write(details)
    links = list(dict.fromkeys(html.unescape(u) for u in re.findall(r'href="([^"]*cmd=outCorrectSolution[^"]*)"', details)))
    for k, u in enumerate(links):
        open(f'{out}/sol{k:03d}.html', 'w').write(s.get(u))
    json.dump({**t, 'pages': n, 'solutions': len(links)}, open(f'{out}/done.json', 'w'), ensure_ascii=False)
    return f'{n} pages, {len(links)} solutions'


for t in json.load(open(tests_file)):
    if only and t['ref'] not in only: continue
    try:
        print(t['ref'], t['title'], '->', scrape(t), flush=True)
    except Exception as e:
        print(t['ref'], t['title'], 'FAILED', e, flush=True)
        traceback.print_exc()
