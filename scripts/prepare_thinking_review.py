"""Build the offline thinking-card review artifacts; never changes Android sources."""
from collections import Counter, deque
from fractions import Fraction
from functools import lru_cache
import hashlib
import heapq
from itertools import combinations, product
import json
from pathlib import Path
import re

from thinking_content import ROWS, CATEGORIES, REFERENCES

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "docs/archive/thinking"


def certify(rows):
    by_id = {r["id"]: r for r in rows}
    def check(key, actual, expected, method):
        assert actual == expected, (key, actual, expected)
        by_id[f"think-{key}"]["verification"] = dict(kind="状态搜索 / 穷举", status="通过", method=method, computed=str(actual))

    @lru_cache(None)
    def take(n, misere=False):
        if n == 0: return misere
        return any(not take(n-k,misere) for k in range(1,min(n,3)+1))
    check("take-away-1",[k for k in range(1,4) if not take(10-k)],[2],"枚举取子博弈的全部后继局面")
    check("take-away-2",take(12),False,"递归求解所有合法取子策略")
    check("take-away-3",[k for k in range(1,4) if not take(10-k,True)],[1],"末子输规则下递归求解")

    @lru_cache(None)
    def nim(heaps):
        return any(not nim(tuple(sorted(heaps[:i]+(n-k,)+heaps[i+1:]))) for i,n in enumerate(heaps) for k in range(1,n+1))
    for suffix,heaps,expected in [(1,(5,5),False),(2,(3,5),True),(3,(1,2,3),False)]:
        check(f"nim-{suffix}",nim(heaps),expected,"递归枚举该小型 Nim 的全部合法取法，不依赖异或结论")

    truth = [(a,b) for a,b in product((False,True),repeat=2) if (not b)==a and (a!=b)==b]
    check("truth-island-1",truth,[(False,True)],"穷举四种身份组合并核对发言真假")
    check("truth-island-2",[a for a in (False,True) if (not a)==a],[],"检查两种身份均不自洽")
    check("truth-island-3",[(a,b) for a,b in product((False,True),repeat=2) if ((not a) or (not b))==a],[(True,False)],"穷举发言者和沉默者身份")

    def crossing(weights, capacity):
        n=len(weights);all_mask=(1<<n)-1
        pending=deque([((0,0),0)]);seen={(0,0)}
        while pending:
            (mask,side),distance=pending.popleft()
            if mask==all_mask and side==1: return distance
            eligible=[i for i in range(n) if ((mask>>i)&1)==side]
            for size in range(1,len(eligible)+1):
                for group in combinations(eligible,size):
                    if sum(weights[i] for i in group)>capacity: continue
                    state=(mask ^ sum(1<<i for i in group),1-side)
                    if state not in seen:seen.add(state);pending.append((state,distance+1))
        return None
    check("river-2",crossing([1,1,1],2),3,"枚举船两岸、人员位置与全部合法载客组合")
    check("river-3",crossing([2,2,1,1],2),9,"带体重容量约束的最短渡河状态搜索")

    def wolf_crossing():
        def safe(mask):
            farmer=(mask&1)
            for a,b in [(1,2),(2,3)]:
                if ((mask>>a)&1)==((mask>>b)&1)!=farmer:return False
            return True
        pending=deque([(0,0)]);seen={0}
        while pending:
            mask,d=pending.popleft()
            if mask==15:return d
            for cargo in [None,1,2,3]:
                if cargo is not None and ((mask>>cargo)&1)!=(mask&1):continue
                nxt=mask^1^(0 if cargo is None else 1<<cargo)
                if nxt not in seen and safe(nxt):seen.add(nxt);pending.append((nxt,d+1))
    check("river-1",wolf_crossing(),7,"穷举人和狼羊菜的安全岸边状态")

    def bridge(times):
        target=(1<<len(times))-1
        heap=[(0,0,0)];best={(0,0):0}
        while heap:
            elapsed,mask,side=heapq.heappop(heap)
            if best[(mask,side)]!=elapsed:continue
            if mask==target and side==1:return elapsed
            eligible=[i for i in range(len(times)) if ((mask>>i)&1)==side]
            for size in (1,2):
                for group in combinations(eligible,size):
                    state=(mask^sum(1<<i for i in group),1-side)
                    cost=elapsed+max(times[i] for i in group)
                    if cost<best.get(state,10**9):best[state]=cost;heapq.heappush(heap,(cost,*state))
    for i,times,expected in [(1,[1,2,5,10],17),(2,[1,4,6],11),(3,[1,10],10)]:
        check(f"bridge-{i}",bridge(times),expected,"Dijkstra 搜索全部携灯往返与一人/两人同行策略")

    # Compare a finite-horizon dynamic program against the quoted expectations.
    # Tail truncation only validates approximate values; the card gives exact equations.
    def wait_expectation(pattern):
        probabilities={"":1.0};expectation=0.0
        for _ in range(250):
            expectation+=sum(probabilities.values())
            nxt={}
            for prefix,p in probabilities.items():
                for face in "HT":
                    suffix=prefix+face
                    if suffix.endswith(pattern):continue
                    keep=max((pattern[:k] for k in range(len(pattern)) if suffix.endswith(pattern[:k])),key=len)
                    nxt[keep]=nxt.get(keep,0)+p/2
            probabilities=nxt
        return round(expectation,7)
    for i,pattern,expected in [(1,"H",2),(2,"HH",6),(3,"HT",4)]:
        check(f"waiting-{i}",wait_expectation(pattern),expected,"逐轮计算 250 次内的生存概率和，与精确期望对照至 7 位小数")

    check("extremes-1",all(any(abs(a-b)==10 for a,b in combinations(c,2)) for c in combinations(range(1,21),11)),True,"穷举 167960 个十一数子集检查差十数对")
    check("extremes-3",all(any(a%b==0 or b%a==0 for a,b in combinations(c,2)) for c in combinations(range(1,11),6)),True,"穷举全部 210 个六数子集检查整除对")


def build():
    rows=sorted(ROWS,key=lambda r:(CATEGORIES.index(r["category"]),r["family"],r["id"]))
    certify(rows)
    assert len(rows)==300
    assert len({r["id"] for r in rows})==300
    assert len({re.sub(r"\W+","",r["body"]) for r in rows})==300
    assert Counter(r["category"] for r in rows)==Counter({c:30 for c in CATEGORIES})
    assert len({r["family"] for r in rows})==100
    for number,row in enumerate(rows,1):
        row["number"]=number
        for key in ("title","body","answer","explanation"):
            assert row[key].strip() and "\ufffd" not in row[key] and "<script" not in row[key],(row["id"],key)
        assert not re.search(r"上题|同上|上述|同一块|同样的.*模型|同样的百门",row["body"]),row["id"]
        row["contentHash"]=hashlib.sha256(json.dumps({k:row[k] for k in ("title","body","answer","explanation")},ensure_ascii=False,sort_keys=True).encode()).hexdigest()
    content_hash=hashlib.sha256(json.dumps(rows,ensure_ascii=False,sort_keys=True).encode()).hexdigest()
    bundle=dict(schemaVersion=1,revision="2026-10-25.1",title="YOURoom · 想一想 · 候选题库",contentHash=content_hash,
                count=len(rows),families=100,categories=CATEGORIES,
                notice="仅供审阅，尚未接入正式应用；相关题型参考链接不等同于逐条转载来源；数值复算不代表题干已经人工终审",
                questions=rows)
    DEST.mkdir(parents=True,exist_ok=True)
    raw=json.dumps(bundle,ensure_ascii=False,indent=2)+"\n"
    (DEST/"questions.json").write_text(raw,encoding="utf-8",newline="\n")
    payload=json.dumps(bundle,ensure_ascii=False,separators=(",",":")).replace("<","\\u003c").replace("\u2028","\\u2028").replace("\u2029","\\u2029")
    template=(DEST/"review-template.html").read_text(encoding="utf-8")
    assert template.count("__CORPUS_JSON__")==1
    (ROOT/"docs/archive/thinking/review.html").write_text(template.replace("__CORPUS_JSON__",payload),encoding="utf-8",newline="\n")
    report=dict(count=300,families=100,categories=dict(Counter(r["category"] for r in rows)),
                machineChecks=sum(r["verification"]["status"]=="通过" for r in rows),
                checksByKind=dict(Counter(r["verification"]["kind"] for r in rows)),
                textualReviewPending=sum(r["verification"]["status"]!="通过" for r in rows),
                difficulty=dict(Counter(r["difficulty"] for r in rows)),contentHash=content_hash,
                dataBytes=len(raw.encode()),applicationModified=False)
    (DEST/"validation.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8",newline="\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=="__main__":build()
