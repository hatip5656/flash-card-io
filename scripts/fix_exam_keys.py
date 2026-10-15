"""Correct wrong answer keys and typos in the seeded exams. Idempotent: a fix is applied only while the
question still has its old value. Usage: python3 -I fix_exam_keys.py <api-base> <admin-user> <admin-password>
e.g. python3 -I fix_exam_keys.py https://wordagram.hatip.dev/api admin '***'"""
import json, sys, urllib.request
base, user, pw = sys.argv[1:4]
UA = {'User-Agent': 'Mozilla/5.0', 'Content-Type': 'application/json'}  # Cloudflare rejects urllib's default UA

# (exam id, stored question index, field, old value, new value)
FIXES = [
    # B1 mock, task 1: ads B and C were mixed up in the key. C is the guarded office on Narva's main street
    # with parking in front; B is studying English in England.
    ('b1-reading-1', 0, 'correctIndex', 1, 2),
    ('b1-reading-1', 0, 'explanation', 'Doğru ilan: B', 'Doğru ilan: C'),
    ('b1-reading-1', 3, 'correctIndex', 1, 2),
    ('b1-reading-1', 3, 'explanation', 'Doğru ilan: B', 'Doğru ilan: C'),
    ('b1-reading-1', 5, 'correctIndex', 2, 1),
    ('b1-reading-1', 5, 'explanation', 'Doğru ilan: C', 'Doğru ilan: B'),
    # Gap 1 follows "looduslikult väga mitmekesine" (lakes and rivers); gap 2 follows the forests ("See teeb ...").
    ('b1-reading-2', 25, 'correctIndex', 10, 3),
    ('b1-reading-2', 25, 'explanation',
     "Lünk 1: Metsalar hakkında konuşuluyor ve Estonya'nın çok ormanlık olduğundan bahsediliyor. K cümlesi ('Bu, Estonya'yı Avrupa'nın en ormanlık ülkelerinden biri yapıyor') bu bağlama en uygun olanıdır.",
     "Lünk 1: Estonya küçük ama doğası çok çeşitli. D cümlesi ('Estonya'da 1000'den fazla göl ve binlerce kilometre nehir var') bu çeşitliliği örnekliyor."),
    ('b1-reading-2', 26, 'correctIndex', 3, 10),
    ('b1-reading-2', 26, 'explanation',
     "Lünk 2: Ormanlar ve hayvanlar hakkında konuşulduktan sonra göllerden bahsetmek mantıklıdır. D cümlesi ('Estonya'da 1000'den fazla göl ve binlerce kilometre nehir var') doğal bir geçiş sağlar.",
     "Lünk 2: Ormanlardan bahsedildikten sonra K cümlesi ('Bu, Estonya'yı Avrupa'nın en ormanlık ülkelerinden biri yapıyor') gelir; 'See' ormanları işaret eder."),
    # "Mis arsti juures?" is not a natural reply; asking what is wrong is.
    ('a2-reading-3', 3, 'options', ['Mis arsti juures?', 'Tervist! Mis viga on?', 'See on hea uudis.'],
     ['Mis arsti juures?', 'Oi! Mis viga on?', 'See on hea uudis.']),
    ('a2-reading-3', 3, 'correctIndex', 0, 1),
    ('a2-reading-3', 3, 'explanation',
     '"Mul on arsti juures aeg" = Yarın saat ikide doktor randevum var. Hangi doktor sorusu en doğal yanıttır.',
     '"Mul on arsti juures aeg" = Yarın saat ikide doktor randevum var. En doğal yanıt ne olduğunu sormaktır: "Mis viga on?" (Neyin var?).'),
    # aitama takes the partitive: teid, not teile.
    ('a2-reading-2', 1, 'prompt', '"Kas ma võin teile aidata?"', '"Kas ma võin teid aidata?"'),
    ('a2-reading-2', 1, 'explanation',
     '"Kas ma võin teile aidata?" = "Size yardımcı olabilir miyim?" sorusuna en uygun yanıt ürün arayışını belirten B seçeneğidir.',
     '"Kas ma võin teid aidata?" = "Size yardımcı olabilir miyim?" sorusuna en uygun yanıt ürün arayışını belirten B seçeneğidir.'),
]
# Turkish label left in the Estonian context of the letter task.
CONTEXT_REPLACE = [('a2-reading-full', range(22, 30), "Laura'nin mektubu:", 'Laura kiri:')]


def req(method, path, body=None, token=None):
    h = dict(UA)
    h['X-User-Id'] = '1'
    if token: h['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(base + path, method=method, data=data, headers=h)
    with urllib.request.urlopen(r) as resp: return json.loads(resp.read())


token = req('POST', '/admin/login', {'username': user, 'password': pw})['token']
exam_ids = sorted({f[0] for f in FIXES} | {c[0] for c in CONTEXT_REPLACE})
for exam_id in exam_ids:
    exam = req('GET', f'/official-exams/{exam_id}')
    questions = exam['questions']
    if isinstance(questions, str): questions = json.loads(questions)
    changed = 0
    for eid, qi, field, old, new in FIXES:
        if eid != exam_id: continue
        current = questions[qi].get(field)
        if current == new: continue
        if current != old:
            sys.exit(f'{exam_id} Q{qi} {field}: expected {old!r}, found {current!r}; not touching it')
        questions[qi][field] = new
        changed += 1
    for eid, indexes, old, new in CONTEXT_REPLACE:
        if eid != exam_id: continue
        for qi in indexes:
            ctx = questions[qi].get('context') or ''
            if old in ctx:
                questions[qi]['context'] = ctx.replace(old, new)
                changed += 1
    if not changed:
        print(exam_id, 'already fixed')
        continue
    seed = {'id': exam_id, 'level': exam['cefr_level'], 'title': exam['title'], 'titleTr': exam.get('title_tr'),
            'titleEn': exam.get('title_en'), 'source': exam.get('source'), 'questions': questions}
    print(exam_id, changed, 'changes', req('POST', '/admin/official-exams/seed', {'exams': [seed]}, token))
