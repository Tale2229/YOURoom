const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),vm=require('node:vm');
const {spawn}=require('node:child_process'),{pathToFileURL}=require('node:url');
const html=fs.readFileSync('docs/design/dialogs/dialogs-design-preview.html','utf8');
new vm.Script(html.match(/<script>([\s\S]*?)<\/script>/)[1],{filename:'dialogs-design-preview.html'});
const pause=ms=>new Promise(r=>setTimeout(r,ms)),pending=new Map(),errors=[],network=[];
let browser,socket,serial=0;const out=path.resolve('artifacts/qa/dialogs-designs-qa');
async function send(method,params={}){const id=++serial;const p=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return p;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function screenshot(name,selector){await pause(250);const clip=selector?await run(s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};},selector):{x:0,y:0,width:1440,height:1250,scale:1};const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip});fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(r.data,'base64'));}
async function main(){
 fs.mkdirSync(out,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9618','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/dialogs-designs-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<120;i++){try{target=(await(await fetch('http://127.0.0.1:9618/json/list')).json()).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}assert(target);
 socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')errors.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/dialogs/dialogs-design-preview.html')).href});
 for(let i=0;i<80;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".specimen").length>0'))break;await pause(100);}

 const summary=await evaluate('({cases:CASES.length,families:new Set(CASES.map(c=>c.family)).size,themes:THEMES.length,ids:CASES.map(c=>c.id)})');
 assert.equal(summary.cases,39);assert.equal(summary.families,18);assert.equal(summary.themes,12);
 assert(await evaluate('!CASES.some(c=>["S09","G01","G02"].includes(c.id))'));
 assert.equal(await evaluate('document.querySelectorAll(".stage").length'),78);
 assert(await evaluate('Array.from(document.querySelectorAll(".specimen")).every(x=>{const a=x.querySelectorAll(".stage");return a.length===2&&a[0].innerHTML===a[1].innerHTML})'));
 await screenshot('desktop-comparison');
 const layoutErrors=[];let layouts=0,contrastPairs=0;
 for(const width of [1440,1180,390,320]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<700});
  for(const d of ['compare','A','B']){
   await run(d=>{design=d;gallery();},d);
   for(const scale of [1,1.3]){
    await run(s=>{fontScale=s;themeApply();},scale);
    for(const t of await evaluate('THEMES.map(t=>t.id)'))for(const m of ['light','dark']){
     await run((t,m)=>{theme=t;mode=m;themeApply();},t,m);layouts++;
     const bad=await evaluate('({page:document.documentElement.scrollWidth>innerWidth+1,stages:Array.from(document.querySelectorAll(".stage")).filter(x=>x.scrollWidth>x.clientWidth+2).map(x=>x.closest("[data-case]").dataset.case+"/"+x.closest("[data-design]").dataset.design)})');
     if(bad.page||bad.stages.length)layoutErrors.push({width,d,scale,t,m,...bad});
     const contrastErrors=await evaluate('(()=>{const p=THEMES.find(t=>t.id===theme)[mode],v=k=>getComputedStyle(document.documentElement).getPropertyValue("--"+k).trim();return [[p.paper,p.ink],[p.control,p.ink],[p.low,p.ink],[p.paper,v("muted")],[p.control,v("muted")],[p.low,v("muted")],[p.primary,p.onPrimary],[v("dangerWash"),v("error")]].filter(([bg,fg])=>contrast(bg,fg)<4.5).map(([bg,fg])=>({bg,fg,c:contrast(bg,fg)}))})()');
     contrastPairs+=8;if(contrastErrors.length)throw Error(JSON.stringify({t,m,contrastErrors}));
    }
   }
  }
 }
 assert.deepEqual(layoutErrors,[]);
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await evaluate('fontScale=1;theme="MATISSE";mode="light";design="compare";category="all";gallery();themeApply()');
 for(const d of ['A','B'])for(const id of summary.ids){
  await run((id,d)=>{viewerDesign=d;show(id);},id,d);
  assert.equal(await evaluate('document.getElementById("viewer-stage").dataset.design'),d);
  assert(await evaluate('document.querySelector("#viewer-stage>.modal,#viewer-stage>.float-context")!==null'));
 }
 // Twin samples must preserve fields, actions, order, text and disabled states.
 await evaluate('document.getElementById("viewer").close();gallery()');
 const twins=await evaluate('Array.from(document.querySelectorAll(".specimen")).map(x=>{const get=d=>Array.from(x.querySelectorAll("[data-design="+d+"] .stage input,[data-design="+d+"] .stage button,[data-design="+d+"] .stage textarea")).map(el=>({tag:el.tagName,action:el.dataset.action,nav:el.dataset.nav,text:el.textContent,value:el.value,disabled:el.disabled}));return {id:x.dataset.case,equal:JSON.stringify(get("A"))===JSON.stringify(get("B"))}})');
 assert(twins.every(x=>x.equal),JSON.stringify(twins));
 await evaluate('document.querySelector("[data-case=Q02] [data-design=B] [data-nav=Q03]").click()');assert.equal(await evaluate('active.id'),'Q03');assert.equal(await evaluate('viewerDesign'),'B');
 await evaluate('document.querySelector("#viewer-stage .chip-delete").click()');assert.equal(await evaluate('active.id'),'Q05');assert.equal(await evaluate('document.getElementById("viewer-stage").dataset.design'),'B');assert(await evaluate('document.querySelector("#viewer-stage .parent-window")!==null'));
 await screenshot('B-nested-tag','#viewer');
 await evaluate('document.getElementById("viewer-design").value="A";document.getElementById("viewer-design").dispatchEvent(new Event("change",{bubbles:true}))');assert.equal(await evaluate('document.getElementById("viewer-stage").dataset.design'),'A');
 await screenshot('A-nested-tag','#viewer');
 await evaluate('document.querySelector("#viewer-stage [data-action=close]").click()');assert.equal(await evaluate('active.id'),'Q03');
 await evaluate('show("R04");document.querySelector("#viewer-stage textarea").value="靠窗";document.querySelector("#viewer-stage textarea").dispatchEvent(new Event("input",{bubbles:true}))');
 await evaluate('document.getElementById("viewer-design").value="B";document.getElementById("viewer-design").dispatchEvent(new Event("change",{bubbles:true}))');assert.equal(await evaluate('document.querySelector("#viewer-stage textarea").value'),'靠窗');
 await evaluate('show("A05");document.querySelector("#viewer-stage [data-nav=A06]").click()');assert.equal(await evaluate('active.id'),'A06');
 assert(await evaluate('document.querySelector("#viewer-stage [data-action=verify]").disabled'));
 await evaluate('document.querySelectorAll("#viewer-stage input").forEach((x,i)=>{x.value=i?"synthetic":"2023000000";x.dispatchEvent(new Event("input",{bubbles:true}))})');
 assert.equal(await evaluate('document.querySelector("#viewer-stage [data-action=verify]").disabled'),false);
 await evaluate('document.querySelector("#viewer-stage [data-action=verify]").click()');assert(await evaluate('document.getElementById("toast").textContent.includes("不发送")'));
 await evaluate('show("S07");const s=document.querySelector("#viewer-stage [data-scenario]");s.value="2";s.dispatchEvent(new Event("change",{bubbles:true}))');assert(await evaluate('!document.querySelector("#viewer-stage .duration-actions").hidden'));
 for(const [g] of await evaluate('GROUPS')){await run(g=>{category=g;gallery();},g);assert.equal(await evaluate('document.querySelectorAll(".specimen").length'),await run(g=>CASES.filter(c=>c.group===g).length,g));}
 await evaluate('category="all";kind="all";query="cachecleardialog";gallery()');assert.equal(await evaluate('document.querySelectorAll(".specimen").length'),7);
 await evaluate('query="";category="settings";design="compare";document.getElementById("campus").value="shahe";document.getElementById("campus").dispatchEvent(new Event("change",{bubbles:true}))');assert(await evaluate('Array.from(document.querySelectorAll(".campus")).every(x=>x.textContent==="沙河")'));
 await evaluate('document.getElementById("viewer").close();category="query";theme="MATISSE";mode="light";campus="west";gallery();themeApply();document.getElementById("toast").hidden=true');await screenshot('query-AB-final');
 await evaluate('category="settings";gallery()');await screenshot('settings-AB');
 await evaluate('category="account";theme="MORNING_BELL";mode="dark";gallery();themeApply()');await screenshot('account-dark-AB');
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await evaluate('fontScale=1.3;themeApply();viewerDesign="B";show("A05")');await screenshot('phone-B-confirm','#viewer');
 await evaluate('viewerDesign="A";show("P02")');await screenshot('phone-A-expected','#viewer');
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({...summary,layouts,contrastPairs,errors,network,twins},null,2));
 console.log(JSON.stringify({...summary,layouts,contrastPairs,browserErrors:errors.length,httpRequests:network.length,output:out}));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{try{if(socket?.readyState===1)await send('Browser.close');}catch{}socket?.close();if(browser&&!browser.killed)browser.kill();});
