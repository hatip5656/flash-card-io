"""Upload TESTEST media (audio and pictures) into the exam_media table. Usage:
python3 -I import_testest.py <api-base> <admin-user> <admin-password> <convert-out-dir>
e.g. python3 -I import_testest.py https://wordagram.hatip.dev/api admin '***' ./out
The exams and tasks themselves are seeded by Liquibase (028/029 changesets, see liquibase_seed.py); media is
binary, so it goes through the admin upload instead. Keys already stored are skipped, so a rerun only sends
what is missing."""
import json, os, sys, time, urllib.request

base, user, pw, src = sys.argv[1:5]
UA = 'Mozilla/5.0'  # Cloudflare rejects urllib's default UA


def call(method, path, body=None, token=None, raw=None, ctype='application/json'):
    h = {'User-Agent': UA, 'Content-Type': ctype}
    if token: h['Authorization'] = 'Bearer ' + token
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(base + path, method=method, data=data, headers=h)
    with urllib.request.urlopen(r, timeout=120) as resp:
        return json.loads(resp.read() or b'{}')


token = call('POST', '/admin/login', {'username': user, 'password': pw})['token']
media_root = os.path.join(src, 'media')
keys = sorted(os.path.relpath(os.path.join(d, f), media_root)
              for d, _, fs in os.walk(media_root) for f in fs)
CHUNK = 900 * 1024  # the proxy in front of the API rejects request bodies over 1 MB
sent = 0
for i, key in enumerate(keys):
    time.sleep(0.7)  # the API allows about 100 requests a minute per IP
    with open(os.path.join(media_root, key), 'rb') as f:
        data = f.read()
    stored = call('GET', '/admin/exam-media-exists/' + key, token=token)
    if stored.get('exists') and stored.get('bytes') == len(data):
        continue
    for attempt in range(3):
        try:
            # The first chunk replaces whatever is stored, so a retry starts the file cleanly
            for off in range(0, len(data), CHUNK):
                q = '?append=true' if off else ''
                call('PUT', '/admin/exam-media/' + key + q, raw=data[off:off + CHUNK], token=token,
                     ctype='application/octet-stream')
                time.sleep(0.7)
            break
        except OSError as e:  # timeouts and dropped connections
            print(f'{key}: {e}; retrying', flush=True)
            time.sleep(5)
    check = call('GET', '/admin/exam-media-exists/' + key, token=token)
    if check.get('bytes') != len(data):
        sys.exit(f'{key}: stored {check.get("bytes")} bytes, expected {len(data)}')
    sent += 1
    if sent % 25 == 0: print(f'media {i + 1}/{len(keys)}', flush=True)
print(f'media: {sent} uploaded, {len(keys) - sent} already there')
