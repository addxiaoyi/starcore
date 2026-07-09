import re, glob, json
def leaves(path):
    out={}; stack=[]
    for raw in open(path,encoding='utf-8').read().split('\n'):
        if not raw.strip() or raw.lstrip().startswith('#'): continue
        if raw.lstrip().startswith('- '): continue
        ind=len(raw)-len(raw.lstrip())
        m=re.match(r'^(\s*)([^:#]+):(.*)$',raw)
        if not m: continue
        key=m.group(2).strip(); val=m.group(3).strip()
        while stack and stack[-1][0]>=ind: stack.pop()
        path_k='.'.join([s[1] for s in stack]+[key])
        stack.append((ind,key))
        if val and val!='|' and val!='>' and not val.startswith('#'):
            out[path_k]=val
    return out
zh=leaves('src/main/resources/lang/messages_zh_cn.yml')
en=leaves('src/main/resources/lang/messages_en_us.yml')
langkeys=set(zh)|set(en)
top_ns={k.split('.')[0] for k in langkeys}

# Precise: first string-literal arg of msg(/format(/getMessage(
pat=re.compile(r'(?:\bmsg|\.format|\bformat|getMessage)\s*\(\s*"([a-zA-Z0-9_.\-]+)"')
refs={}
for f in glob.glob('src/main/java/**/*.java',recursive=True):
    txt=open(f,encoding='utf-8',errors='replace').read()
    for i,line in enumerate(txt.split('\n'),1):
        for k in pat.findall(line):
            if '.' in k and k.split('.')[0] in top_ns:
                refs.setdefault(k,(f,i))
undef={k:v for k,v in refs.items() if k not in langkeys}
from collections import Counter
print("refs(first-arg, ns-filtered):",len(refs))
print("REFERENCED-BUT-UNDEFINED:",len(undef))
print("by prefix:",json.dumps(dict(Counter(k.split('.')[0] for k in undef).most_common()),ensure_ascii=False))
json.dump({k:list(v) for k,v in undef.items()},open('undef.json','w',encoding='utf-8'),ensure_ascii=False)
json.dump({'zh':list(zh),'en':list(en),'refs':list(refs)},open('sets.json','w',encoding='utf-8'),ensure_ascii=False)
