import re,glob,json
def leaves(path):
    out={}; stack=[]
    for raw in open(path,encoding='utf-8'):
        if not raw.strip() or raw.lstrip().startswith('#'): continue
        if raw.lstrip().startswith('- '): continue
        ind=len(raw)-len(raw.lstrip())
        m=re.match(r'^(\s*)([^:#]+):(.*)$',raw.rstrip('\n'))
        if not m: continue
        key=m.group(2).strip(); val=m.group(3).strip()
        while stack and stack[-1][0]>=ind: stack.pop()
        path_k='.'.join([s[1] for s in stack]+[key])
        stack.append((ind,key))
        if val and val not in ('|','>','') and not val.startswith('#'):
            out[path_k]=val
    return set(out)
zh=leaves('src/main/resources/lang/messages_zh_cn.yml')
en=leaves('src/main/resources/lang/messages_en_us.yml')
allk=zh|en
# check the suspicious ones
for k in ["command.nation.claim-failed","command.nation.city-create-failed","command.amount-positive","command.diplomacy.propose-failed"]:
    print(k, "in_zh" if k in zh else "-", "in_en" if k in en else "-")
print("--- keys containing claim-failed ---")
for k in sorted(allk):
    if "claim-failed" in k: print("  ",k)
