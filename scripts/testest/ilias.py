"""Minimal guest client for the TESTEST ILIAS 5.4 test player."""
import html, re, time, urllib.parse, urllib.request, http.cookiejar

BASE = 'https://web.meis.ee/testest/'
UA = 'Mozilla/5.0 (Macintosh) personal-study-client'


def text(s):
    s = re.sub(r'<script.*?</script>|<style.*?</style>', '', s, flags=re.S)
    s = re.sub(r'<br\s*/?>', '\n', s)
    s = html.unescape(re.sub(r'<[^>]+>', ' ', s))
    return re.sub(r'[ \t\xa0]+', ' ', s).strip()


class Session:
    def __init__(self, delay=0.7):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.delay = delay
        self.url = None

    def get(self, path, data=None):
        time.sleep(self.delay)
        url = path if path.startswith('http') else BASE + path.lstrip('./')
        body = urllib.parse.urlencode(data).encode() if data is not None else None
        req = urllib.request.Request(url, data=body, headers={'User-Agent': UA})
        with self.op.open(req, timeout=60) as r:
            self.url = r.geturl()
            return r.read().decode('utf-8', 'ignore')

    def get_bytes(self, url):
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        with self.op.open(req, timeout=120) as r:
            return r.read()

    def start_test(self, ref_id):
        self.get('ilias.php?baseClass=ilrepositorygui&reloadpublic=1&cmd=frameset&ref_id=1')
        info = self.get(f'ilias.php?ref_id={ref_id}&cmd=infoScreen&cmdClass=ilobjtestgui&cmdNode=il:al&baseClass=ilrepositorygui')
        form = form_with(info, 'startPlayer')
        name = 'cmd[startPlayer]'
        page = self.get(form['action'], {**form['hidden'], name: form['buttons'].get(name, 'Start')})
        if 'accessCodeConfirmed' in page:
            form = form_with(page, 'accessCodeConfirmed')
            page = self.get(form['action'], {**form['hidden'], 'cmd[accessCodeConfirmed]': 'Jätka'})
        return info, page


def form_with(page, marker):
    for m in re.finditer(r'<form[^>]*action="([^"]+)"[^>]*>(.*?)</form>', page, re.S):
        if marker in m.group(2):
            body = m.group(2)
            hidden = {a: html.unescape(b) for a, b in re.findall(
                r'<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"', body)}
            buttons = {a: html.unescape(b) for a, b in re.findall(
                r'<input[^>]*name="(cmd\[[^"]+\])"[^>]*value="([^"]*)"', body)}
            return {'action': html.unescape(m.group(1)), 'hidden': hidden, 'buttons': buttons, 'body': body}
    raise ValueError('no form with ' + marker)


def nav_href(page, nextcmd):
    m = re.search(r'href="([^"]+)"[^>]*data-nextcmd="%s"' % nextcmd, page)
    return html.unescape(m.group(1)) if m else None


def parse_question(page):
    """Question page -> dict with title, instruction, stem, options, media; raw html kept for checking."""
    i = page.find('id="ilc_Page"')
    j = page.find('tst_discard_solution_modal')
    block = page[i:j]
    title = re.search(r'<h1 class="ilc_page_title_PageTitle">(.*?)</h1>', block, re.S)
    heads = [text(h) for h in re.findall(r'<h3 class="ilc_Paragraph[^"]*">(.*?)</h3>', block, re.S)]
    paras = [text(p) for p in re.findall(r'<div class="ilc_Paragraph[^"]*">(.*?)</div>', block, re.S)]
    q = re.search(r'<div class="ilc_question_(\w+)">', block)
    stem = re.search(r'<div class="ilc_qtitle_Title">(.*?)</div>\s*<div class="ilc_answers', block, re.S)
    options = [(v, text(l)) for v, l in re.findall(
        r'<input type="(?:radio|checkbox)" name="multiple_choice_result[^"]*" value="(\d+)"[^>]*/>.*?<label[^>]*>(.*?)</label>',
        block, re.S)]
    media = re.findall(r'(?:src|href)="([^"]+\.(?:mp3|mp4|m4a|ogg|wav|png|jpe?g|gif))', block, re.I)
    return {
        'title': text(title.group(1)) if title else None,
        'instruction': heads,
        'paragraphs': paras,
        'type': q.group(1) if q else None,
        'stem': text(stem.group(1)) if stem else None,
        'options': options,
        'media': media,
        'inputs': sorted(set(re.findall(r'name="([^"]+)"', block))),
    }


def answer_and_go(s, page, value, nextcmd):
    """Submit `value` for the current question, then follow the nav link with data-nextcmd."""
    form = form_with(page, 'formtimestamp')
    nav = nav_href(page, nextcmd)
    data = {**form['hidden'], 'test_player_navigation_url': nav, 'cmd[submitSolution]': 'Save'}
    if value is not None:
        data['multiple_choice_result'] = value
    return s.get(form['action'], data)
