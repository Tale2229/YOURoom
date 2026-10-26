const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),vm=require('node:vm');
const {spawn}=require('node:child_process'),{pathToFileURL}=require('node:url');
const html=fs.readFileSync('docs/design/dialogs/dialogs-preview.html','utf8');
new vm.Script(html.match(/<script>([\s\S]*?)<\/script>/)[1],{filename:'dialogs-preview.html'});
const pause=ms=>new Promise(r=>setTimeout(r,ms)),pending=new Map(),errors=[],network=[];
let browser,socket,serial=0;const out=path.resolve('artifacts/qa/dialogs-qa');
async function send(method,params={}){const id=++serial;const p=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return p;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function screenshot(name,selector){await pause(250);const clip=selector?await run(s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};},selector):{x:0,y:0,width:1440,height:1250,scale:1};const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip});fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(r.data,'base64'));}
async function main(){
 fs.mkdirSync(out,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9617','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/dialogs-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<120;i++){try{target=(await(await fetch('http://127.0.0.1:9617/json/list')).json()).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}assert(target);
 socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')errors.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/dialogs/dialogs-preview.html')).href});
 for(let i=0;i<80;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".specimen").length>0'))break;await pause(100);}
 const summary=await evaluate('({cases:CASES.length,families:new Set(CASES.map(c=>c.family)).size,themes:THEMES.length,guide:GUIDE.length,ids:CASES.map(c=>c.id)})');
 assert.equal(summary.cases,42);assert.equal(summary.themes,12);assert.equal(summary.guide,43);assert.equal(new Set(summary.ids).size,42);
 assert(await evaluate('CASES.every(c=>!c.parent||CASES.some(p=>p.id===c.parent))'));
 for(const source of await evaluate('Array.from(new Set(CASES.map(c=>c.source)))'))assert(fs.existsSync('app/src/main/java/com/example/youroom/ui/'+source));
 await screenshot('desktop-query');
 const layoutErrors=[];let layouts=0;
 for(const width of [1600,1180,390,320]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<700});
  for(const scale of [1,1.3]){
   await run(s=>{fontScale=s;themeApply();},scale);
   for(const t of await evaluate('THEMES.map(t=>t.id)'))for(const m of ['light','dark']){
    await run((t,m)=>{theme=t;mode=m;themeApply();},t,m);layouts++;
    const bad=await evaluate('({page:document.documentElement.scrollWidth>innerWidth+1,stages:Array.from(document.querySelectorAll(".stage")).filter(x=>x.scrollWidth>x.clientWidth+2).map(x=>x.closest("[data-case]").dataset.case)})');
    if(bad.page||bad.stages.length)layoutErrors.push({width,scale,t,m,...bad});
   }
  }
 }
 assert.deepEqual(layoutErrors,[]);
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await evaluate('fontScale=1;theme="MATISSE";mode="light";themeApply()');
 for(const id of summary.ids){await run(id=>show(id),id);assert(await evaluate('document.getElementById("viewer").open'));assert(await evaluate('document.querySelector("#viewer-stage>.modal,#viewer-stage>.float-context")!==null'));}
 await evaluate('document.getElementById("viewer").close()');
 // Group/type/search filtering must be reversible and source names searchable.
 for(const [g] of await evaluate('GROUPS')){await run(g=>{category=g;gallery();},g);assert.equal(await evaluate('document.querySelectorAll(".specimen").length'),await run(g=>CASES.filter(c=>c.group===g).length,g));}
 await evaluate('category="all";kind="confirm";gallery()');assert.equal(await evaluate('document.querySelectorAll(".specimen").length'),await evaluate('CASES.filter(c=>c.kind==="confirm").length'));
 await evaluate('kind="all";query="cachecleardialog";gallery()');assert.equal(await evaluate('document.querySelectorAll(".specimen").length'),7);
 await evaluate('query="not-a-real-dialog";gallery()');assert(await evaluate('document.querySelector(".no-results")!==null'));
 await evaluate('query="";gallery();campus="shahe";gallery()');assert(await evaluate('Array.from(document.querySelectorAll(".campus")).every(x=>x.textContent==="沙河")'));
 await evaluate('campus="west";gallery();show("Q02")');
 await evaluate('document.querySelector("#viewer-stage [data-nav=Q03]").click()');assert.equal(await evaluate('active.id'),'Q03');
 await evaluate('document.querySelector("#viewer-stage .chip-delete").click()');assert.equal(await evaluate('active.id'),'Q05');
 assert(await evaluate('document.querySelector("#viewer-stage .parent-window")!==null'));await screenshot('nested-tag-confirm','#viewer');
 await evaluate('document.querySelector("#viewer-stage [data-action=close]").click()');assert.equal(await evaluate('active.id'),'Q03');
 await evaluate('show("S03");document.querySelector("#viewer-stage [data-nav=S08]").click()');assert.equal(await evaluate('active.id'),'S08');await screenshot('nested-template-confirm','#viewer');
 await evaluate('show("A05");document.querySelector("#viewer-stage [data-nav=A06]").click()');assert.equal(await evaluate('active.id'),'A06');
 assert(await evaluate('document.querySelector("#viewer-stage [data-action=verify]").disabled'));
 await evaluate('document.querySelectorAll("#viewer-stage input").forEach((x,i)=>{x.value=i?"synthetic":"2023000000";x.dispatchEvent(new Event("input",{bubbles:true}))})');
 assert.equal(await evaluate('document.querySelector("#viewer-stage [data-action=verify]").disabled'),false);
 await evaluate('document.querySelector("#viewer-stage [data-action=verify]").click()');assert(await evaluate('document.getElementById("toast").textContent.includes("不发送")'));
 await evaluate('show("R04");const note=document.querySelector("#viewer-stage textarea");note.value="靠窗";note.dispatchEvent(new Event("input",{bubbles:true}))');assert.equal(await evaluate('document.querySelector("#viewer-stage .counter").textContent'),'2/20');
 await evaluate('show("S07");const s=document.querySelector("#viewer-stage [data-scenario]");s.value="2";s.dispatchEvent(new Event("change",{bubbles:true}))');assert(await evaluate('!document.querySelector("#viewer-stage .duration-actions").hidden'));
 await evaluate('show("G01");document.querySelector("#viewer-stage [data-chapter=\\"3\\"]").click()');assert.equal(await evaluate('active.chapter'),3);
 assert(await evaluate('document.querySelector("#viewer-stage .chapter-list").textContent.includes("关于与帮助")'));
 await evaluate('document.querySelector("#viewer-stage [data-nav=G01]").click()');assert.equal(await evaluate('active.id'),'G01');
 await evaluate('show("S09")');assert.equal(await evaluate('document.querySelectorAll("#viewer-stage .about-logo svg path").length'),40);await screenshot('about-current','#viewer');
 await evaluate('document.querySelector("#viewer-stage [data-action=pig]").click()');await pause(80);assert(await evaluate('document.querySelector("#viewer-stage .pig").classList.contains("squint")'));await pause(450);assert.equal(await evaluate('document.querySelector("#viewer-stage .pig").classList.contains("squint")'),false);
 await evaluate('document.getElementById("viewer").close();category="account";mode="dark";theme="MORNING_BELL";gallery();themeApply()');await screenshot('account-dark');
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await evaluate('category="query";fontScale=1.3;gallery();themeApply();show("Q05")');await screenshot('phone-confirm','#viewer');
 assert.equal(await evaluate('document.documentElement.scrollWidth>innerWidth+1'),false);
 assert(await evaluate('Array.from(document.images).every(i=>i.complete&&i.naturalWidth>0)'));
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({...summary,layouts,errors,network,notes:'HTML prototype only; no Android writes or account requests'},null,2));
 console.log(JSON.stringify({...summary,layouts,browserErrors:errors.length,httpRequests:network.length,output:out}));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{try{if(socket?.readyState===1)await send('Browser.close');}catch{}socket?.close();if(browser&&!browser.killed)browser.kill();});
