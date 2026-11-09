#!/usr/bin/env python3
"""Lists texts in the code (Serbian / English pairs) that have no Polish or Turkish translation
in app/src/main/assets/lang/<code>.tsv. Usage: scripts/check-lang.py  (exit 1 if any is missing)."""
import os,re,sys
root=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","app","src","main","java")
calls=re.compile(r'(?<![A-Za-z0-9_])(Mib\.t|LoadingBanner\.t|s|t|tr|txt)\(')
def parse_str(src,i):
    # parse java string literal at i (src[i]=='"'), return (value,end)
    assert src[i]=='"'
    j=i+1; out=''
    while src[j]!='"':
        if src[j]=='\\':
            n=src[j+1]
            out+={'n':'\n','t':'\t','"':'"','\\':'\\',"'":"'"}.get(n,'\\'+n) if n!='u' else chr(int(src[j+2:j+6],16))
            j+= 6 if n=='u' else 2
        else:
            out+=src[j]; j+=1
    return out,j+1
def parse_arg(src,i):
    # returns (literal or None, end index at ',' or ')')
    depth=0; j=i; lits=[]; pure=True
    while True:
        c=src[j]
        if c=='"':
            v,j=parse_str(src,j); lits.append(v); continue
        if c in '([{': depth+=1
        elif c in ')]}':
            if depth==0: break
            depth-=1
        elif c==',' and depth==0: break
        elif not c.isspace() and c!='+': pure=False
        j+=1
    return (''.join(lits) if pure and lits else None), j
res={}
for dp,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.java'): continue
        src=open(os.path.join(dp,f),encoding='utf-8').read()
        for m in calls.finditer(src):
            i=m.end(); args=[]
            try:
                for k in range(3):
                    while src[i].isspace(): i+=1
                    v,i=parse_arg(src,i)
                    args.append(v)
                    if src[i]==')': break
                    i+=1
            except Exception: continue
            strs=[a for a in args]
            if m.group(1)=='Mib.t' and len(args)==3: sr,en=args[1],args[2]
            elif len(args)==2: sr,en=args
            else: continue
            if sr is None or en is None: continue
            res.setdefault(en,sr)
lang=os.path.join(root,"..","assets","lang")
missing=0
for code in ("pl","tr"):
    have=set()
    for line in open(os.path.join(lang,code+".tsv"),encoding="utf-8"):
        if line.startswith("#") or "\t" not in line: continue
        have.add(line.split("\t")[0].replace("\\n","\n").replace("\\t","\t"))
    for en in sorted(res):
        if en not in have:
            print(code+": "+repr(en)); missing+=1
print(str(len(res))+" texts, "+str(missing)+" missing translations")
sys.exit(1 if missing else 0)
