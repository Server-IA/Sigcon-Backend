import json, unicodedata, re
def norm(s):
    if not s: return ''
    s = unicodedata.normalize('NFKD', s)
    s = ''.join(c for c in s if not unicodedata.combining(c))
    s = s.lower().replace('–','-').replace('—','-')
    s = re.sub(r'[^a-z0-9_]+', '_', s)
    return s.strip('_')
g = json.load(open(r'C:\Users\creds\AppData\Local\Temp\glosario.json', encoding='utf-8'))
seen = set()
for p in g:
    k = (norm(p['module']), norm(p['submodule']))
    if k not in seen:
        seen.add(k)
        print(f"  ('{k[0]}','{k[1]}'),  # {p['module']} / {p['submodule']}")
