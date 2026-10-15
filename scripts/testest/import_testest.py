"""Upload TESTEST media and seed the converted exams. Usage:
python3 -I import_testest.py <api-base> <admin-user> <admin-password> <convert-out-dir>
e.g. python3 -I import_testest.py https://wordagram.hatip.dev/api admin '***' ./out
<convert-out-dir> is what convert.py wrote: testest-exams.json and media/. Media already on the server is
skipped, so a rerun only sends what is missing."""
import json, os, sys, time, urllib.error, urllib.request

base, user, pw, src = sys.argv[1:5]
UA = 'Mozilla/5.0'  # Cloudflare rejects urllib's default UA


def call(method, path, body=None, token=None, raw=None, ctype='application/json'):
    h = {'User-Agent': UA, 'Content-Type': ctype}
    if token: h['Authorization'] = 'Bearer ' + token
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(base + path, method=method, data=data, headers=h)
    with urllib.request.urlopen(r, timeout=120) as resp:
        return json.loads(resp.read() or b'{}')


def exists(key):
    r = urllib.request.Request(base + '/exam-media/' + key, method='HEAD', headers={'User-Agent': UA})
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status == 200
    except urllib.error.HTTPError:
        return False


token = call('POST', '/admin/login', {'username': user, 'password': pw})['token']
media_root = os.path.join(src, 'media')
keys = sorted(os.path.relpath(os.path.join(d, f), media_root)
              for d, _, fs in os.walk(media_root) for f in fs)
sent = 0
for i, key in enumerate(keys):
    time.sleep(0.7)  # the API allows about 100 requests a minute per IP; each file costs one or two
    if exists(key):
        continue
    with open(os.path.join(media_root, key), 'rb') as f:
        call('PUT', '/admin/exam-media/' + key, raw=f.read(), token=token, ctype='application/octet-stream')
    sent += 1
    time.sleep(0.7)
    if sent % 25 == 0: print(f'media {i + 1}/{len(keys)}', flush=True)
print(f'media: {sent} uploaded, {len(keys) - sent} already there')

exams = json.load(open(os.path.join(src, 'testest-exams.json')))
for i in range(0, len(exams), 5):
    batch = exams[i:i + 5]
    print([e['id'] for e in batch], call('POST', '/admin/official-exams/seed', {'exams': batch}, token))
