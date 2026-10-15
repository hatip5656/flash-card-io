"""Upload exam question tags to the backend. Usage: python3 -I apply_exam_tags.py <api-base> <admin-user> <admin-password>
e.g. python3 -I apply_exam_tags.py https://wordagram.hatip.dev/api admin '***'"""
import json, sys, glob, os, urllib.request
base, user, pw = sys.argv[1:4]
UA = {'User-Agent': 'Mozilla/5.0', 'Content-Type': 'application/json'}  # Cloudflare rejects urllib's default UA
def req(method, path, body, token=None):
    h = dict(UA)
    if token: h['Authorization'] = 'Bearer ' + token
    r = urllib.request.Request(base + path, method=method, data=json.dumps(body).encode(), headers=h)
    with urllib.request.urlopen(r) as resp: return json.loads(resp.read())
token = req('POST', '/admin/login', {'username': user, 'password': pw})['token']
here = os.path.dirname(os.path.abspath(__file__))
for f in sorted(glob.glob(os.path.join(here, "exam-tags", "*.json"))):
    exam_id = os.path.basename(f)[:-5]
    tags = [{k: t[k] for k in ('questionIndex', 'skill', 'grammarLessonIds', 'keyWords')} for t in json.load(open(f))]
    print(exam_id, req('PUT', f'/admin/official-exams/{exam_id}/tags', {'tags': tags}, token))
