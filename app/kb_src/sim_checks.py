import json,math,random
J=json.load(open("checks.json",encoding="utf-8")); T=J["tables"]; feats=[f["id"] for f in J["features"]]
kb={e["class_id"]:e for e in json.load(open("kb.json",encoding="utf-8"))["entries"]}
NOISE=0.06
def pe(p): return NOISE+(1-2*NOISE)*p
def lik(c,f,yes):
    p=T.get(c,{}).get(f)
    if p is None: return 0.5
    p=pe(p); return p if yes else 1-p
def post(prior,ans):
    w={c:p for c,p in prior.items()}
    for f,y in ans: 
        for c in w: w[c]*=lik(c,f,y)
    s=sum(w.values()); return {c:v/s for c,v in w.items()}
def H(d): return -sum(p*math.log2(p) for p in d.values() if p>0)
def gain(prior,ans,f):
    cur=post(prior,ans); h0=H(cur)
    py=sum(cur[c]*lik(c,f,True) for c in cur)
    if py<=0 or py>=1: return 0
    h=0
    for y,pr in ((True,py),(False,1-py)):
        n={c:cur[c]*lik(c,f,y)/pr for c in cur}; h+=pr*H(n)
    return h0-h
def run(prior,truth,rng,maxq=6):
    ans=[];asked=set()
    for q in range(maxq):
        cur=post(prior,ans)
        if max(cur.values())>=0.92: break
        best=max((f for f in feats if f not in asked),key=lambda f:gain({c:p for c,p in prior.items()},ans,f))
        g=gain(prior,ans,best)
        if g<0.03: break
        asked.add(best)
        p=T.get(truth,{}).get(best); y=(rng.random()<p) if p is not None else (rng.random()<0.5)
        ans.append((best,y))
    cur=post(prior,ans); top=max(cur,key=cur.get)
    return top,cur[top],len(ans)
rng=random.Random(1)
classes=[c for c in T]
for ptrue in (0.5,0.25):
    ok=0;n=0;qs=0;conf_wrong=0;byc={}
    for c in classes:
        crop=c.split("__")[0]
        same=[k for k in classes if k.split("__")[0]==crop and k!=c]
        for trial in range(12):
            rest=same[:] ; rng.shuffle(rest); rest=rest[:4]
            others=rng.sample([k for k in classes if k!=c and k not in rest],2)
            cand=rest+others
            w=[rng.random()+0.2 for _ in cand]; s=sum(w)
            prior={c:ptrue}; prior.update({k:(1-ptrue)*x/s for k,x in zip(cand,w)})
            top,conf,nq=run(prior,c,rng)
            n+=1; ok+=(top==c); qs+=nq
            if top!=c and conf>=0.8: conf_wrong+=1
            byc.setdefault(c,[0,0]); byc[c][0]+=(top==c); byc[c][1]+=1
    print("prior on truth %.2f: accuracy %.3f  avg questions %.2f  confident-wrong(>=0.8) %.3f"%(ptrue,ok/n,qs/n,conf_wrong/n))
    worst=sorted(byc.items(),key=lambda kv:kv[1][0]/kv[1][1])[:10]
    print("  weakest:",[(k,"%.2f"%(a/b)) for k,(a,b) in worst])
