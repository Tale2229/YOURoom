const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),vm=require('node:vm');
const {spawn}=require('node:child_process'),{pathToFileURL}=require('node:url');
const html=fs.readFileSync('docs/design/dialogs/dialogs-design-preview.html','utf8');
new vm.Script(html.match(/<script>([\s\S]*?)<\/script>/)[1],{filename:'dialogs-design-preview.html'});
const pause=ms=>new Promise(r=>setTimeout(r,ms)),pending=new Map(),errors=[],network=[];
let browser,socket,serial=0;const out=path.resolve('artifacts/qa/dialog-choices-qa');
async function send(method,params={}){const id=++serial;const p=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return p;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function screenshot(name,selector){await pause(250);const clip=selector?await run(s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};},selector):{x:0,y:0,width:1440,height:1250,scale:1};const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip});fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(r.data,'base64'));}
async function main(){
 fs.mkdirSync(out,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9621','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/dialog-choices-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<120;i++){try{target=(await(await fetch('http://127.0.0.1:9621/json/list')).json()).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}assert(target);
 socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')errors.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/dialogs/dialogs-design-preview.html')).href});
 for(let i=0;i<80;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".specimen").length>0'))break;await pause(100);}


 const downloadPath=path.join(out,'downloads');fs.mkdirSync(downloadPath,{recursive:true});
 await send('Browser.setDownloadBehavior',{behavior:'allow',downloadPath});
 await evaluate('localStorage.removeItem(CHOICE_STORAGE);location.reload()');
 for(let i=0;i<80;i++){await pause(100);if(await evaluate('typeof choices!=="undefined"&&document.querySelectorAll(".specimen").length===39&&choices.size===0'))break;}
 assert.equal(await evaluate('choices.size'),0);
 assert.equal(await evaluate('document.querySelectorAll(".choice-control").length'),39);
 assert(await evaluate('document.getElementById("export-choices").disabled'));
 assert(await evaluate('document.getElementById("copy-choices").disabled'));
 await evaluate('document.querySelector("[data-case=Q02] [data-pick-value=A]").click();document.querySelector("[data-case=Q03] [data-pick-value=B]").click();document.querySelector("[data-case=R04] [data-pick-value=B]").click()');
 assert.deepEqual(await evaluate('choiceSummary()'),{total:39,selected:3,A:1,B:2,unselected:36});
 assert.equal(await evaluate('choicePayload().mixedFamilyChoices.length'),1);
 assert(await evaluate('document.querySelector("[data-case=Q02] [data-design=A]").dataset.chosen==="true"'));
 assert(await evaluate('document.querySelector("[data-case=Q03] [data-pick-value=B]").getAttribute("aria-pressed")==="true"'));
 await evaluate('show("R04");const note=document.querySelector("#viewer-stage textarea");note.value="NO_EXPORT_NOTE";note.dispatchEvent(new Event("input",{bubbles:true}));document.querySelector("#viewer-meta [data-pick-value=A]").click()');
 assert.equal(await evaluate('document.querySelector("#viewer-stage textarea").value'),'NO_EXPORT_NOTE');
 assert.equal(await evaluate('choices.get("R04")'),'A');
 await evaluate('document.getElementById("viewer-design").value="B";document.getElementById("viewer-design").dispatchEvent(new Event("change",{bubbles:true}))');
 assert.equal(await evaluate('choices.get("R04")'),'A');
 assert.equal(await evaluate('document.querySelector("#viewer-stage textarea").value'),'NO_EXPORT_NOTE');
 await evaluate('document.querySelector("#viewer-meta [data-pick-value=B]").click();show("A06");document.querySelectorAll("#viewer-stage input").forEach((x,i)=>{x.value=i?"DO_NOT_EXPORT_SENSITIVE_SAMPLE_42":"9999999999";x.dispatchEvent(new Event("input",{bubbles:true}))})');
 const payload=await evaluate('choicePayload()');
 assert(!JSON.stringify(payload).includes('DO_NOT_EXPORT_SENSITIVE_SAMPLE_42'));assert(!JSON.stringify(payload).includes('9999999999'));assert(!JSON.stringify(payload).includes('NO_EXPORT_NOTE'));
 assert.equal(payload.selections.length,39);assert.equal(payload.selections.filter(x=>x.variant===null).length,36);
 assert(!payload.selections.some(x=>['S09','G01','G02'].includes(x.id)));
 assert(payload.selections.every(x=>x.title&&x.source&&x.path&&x.family));
 await evaluate('document.getElementById("viewer").close();category="room";design="B";gallery();theme="MORNING_BELL";mode="dark";themeApply()');
 assert.equal(await evaluate('choices.size'),3);
 assert(await evaluate('document.querySelector("[data-case=R04] [data-pick-value=B]").getAttribute("aria-pressed")==="true"'));
 await evaluate('document.querySelector("[data-case=R04] [data-pick-value=\\"\\"]").click()');assert.equal(await evaluate('choices.size'),2);
 await evaluate('setChoice("R04","B");category="all";design="compare";theme="MATISSE";mode="light";gallery();themeApply()');
 await screenshot('selection-desktop');
 const beforeFiles=new Set(fs.readdirSync(downloadPath));
 await evaluate('document.getElementById("export-choices").click()');
 let downloaded;
 for(let i=0;i<80;i++){await pause(100);downloaded=fs.readdirSync(downloadPath).find(f=>!beforeFiles.has(f)&&f.endsWith('.json'));if(downloaded)break;}
 assert(downloaded);assert.match(downloaded,/^YOURoom-dialog-choices-\d{14}\.json$/);
 const saved=JSON.parse(fs.readFileSync(path.join(downloadPath,downloaded),'utf8'));
 assert.equal(saved.format,'YOURoom.dialog-design-choices');assert.equal(saved.schemaVersion,1);
 assert.deepEqual(saved.summary,{total:39,selected:3,A:1,B:2,unselected:36});
 assert.equal(saved.selections.find(x=>x.id==='Q02').variant,'A');assert.equal(saved.selections.find(x=>x.id==='Q03').variant,'B');
 await send('Page.reload');
 for(let i=0;i<80;i++){await pause(100);if(await evaluate('typeof choices!=="undefined"&&choices.size===3&&document.querySelectorAll(".specimen").length===39'))break;}
 assert.equal(await evaluate('choices.get("Q02")'),'A');assert.equal(await evaluate('choices.get("Q03")'),'B');
 assert(await evaluate('document.getElementById("selection-save-status").textContent.includes("恢复")'));
 await evaluate('setChoice("Q02","B");setChoice("R01","A")');
 const changed=await evaluate('Array.from(choices.entries())');
 const root=await send('DOM.getDocument');const input=await send('DOM.querySelector',{nodeId:root.root.nodeId,selector:'#import-choices-file'});
 await send('DOM.setFileInputFiles',{nodeId:input.nodeId,files:[path.join(downloadPath,downloaded)]});
 for(let i=0;i<40;i++){if(await evaluate('document.getElementById("selection-import-confirm").open'))break;await pause(100);}
 assert(await evaluate('document.getElementById("selection-import-confirm").open'));assert.deepEqual(await evaluate('Array.from(choices.entries())'),changed);
 await screenshot('import-confirm','#selection-import-confirm');
 await evaluate('document.getElementById("cancel-choice-import").click()');assert.deepEqual(await evaluate('Array.from(choices.entries())'),changed);
 await send('DOM.setFileInputFiles',{nodeId:input.nodeId,files:[path.join(downloadPath,downloaded)]});
 for(let i=0;i<40;i++){if(await evaluate('document.getElementById("selection-import-confirm").open'))break;await pause(100);}
 await evaluate('document.getElementById("apply-choice-import").click()');
 assert.deepEqual(await evaluate('choiceSummary()'),saved.summary);assert.equal(await evaluate('choices.get("Q02")'),'A');
 assert.equal(await evaluate('document.getElementById("import-choices-file").value'),'');
 const base=await evaluate('choicePayload()');
 const broken=[
  {...base,format:'wrong'},
  {...base,schemaVersion:99},
  {...base,catalogueVersion:'old'},
  {...base,selections:[...base.selections,base.selections[0]]},
  {...base,selections:[{id:'Q02',variant:'C'}]},
  {...base,selections:[{id:'S09',variant:'A'}]},
  {...base,selections:[{id:'__proto__',variant:'A'}]},
  {...base,selections:[{id:'Q02',variant:'A'},{id:'Q02',variant:'B'}]},
 ];
 const stable=await evaluate('Array.from(choices.entries())');
 for(const bad of broken){
  await run(async bad=>{await prepareChoiceImport(new File([JSON.stringify(bad)],'bad.json',{type:'application/json'}));},bad);
  assert.deepEqual(await evaluate('Array.from(choices.entries())'),stable);
  assert.equal(await evaluate('document.getElementById("selection-import-confirm").open'),false);
 }
 await evaluate('prepareChoiceImport(new File(["{bad json"],"invalid.json"))');assert.deepEqual(await evaluate('Array.from(choices.entries())'),stable);
 await evaluate('prepareChoiceImport(new File(["x".repeat(1024*1024+1)],"too-large.json"))');assert.deepEqual(await evaluate('Array.from(choices.entries())'),stable);
 // Untrusted metadata is ignored; exports always use the current catalog.
 await run(async base=>{base.selections[0].title='<img src=x onerror=alert(1)>';await prepareChoiceImport(new File([JSON.stringify(base)],'<unsafe>.json'));},base);
 assert.equal(await evaluate('document.querySelectorAll("#selection-import-confirm img").length'),0);
 await evaluate('document.getElementById("apply-choice-import").click()');
 assert.equal(await evaluate('choicePayload().selections[0].title'),base.selections[0].title);
 // Clipboard success is stubbed: do not change the system clipboard in this test.
 await evaluate('Object.defineProperty(navigator,"clipboard",{configurable:true,value:{writeText:async text=>{window.copiedChoiceJSON=text}}});document.getElementById("copy-choices").click()');
 await pause(50);assert.deepEqual((JSON.parse(await evaluate('window.copiedChoiceJSON'))).summary,saved.summary);
 await evaluate('Object.defineProperty(navigator,"clipboard",{configurable:true,value:{writeText:()=>Promise.reject(Error("denied"))}});document.getElementById("copy-choices").click()');
 await pause(50);assert(await evaluate('document.getElementById("selection-copy-fallback").open'));
 assert(await evaluate('(()=>{const t=document.getElementById("selection-copy-text");return t.readOnly&&t.selectionStart===0&&t.selectionEnd===t.value.length})()'));
 assert.equal(JSON.parse(await evaluate('document.getElementById("selection-copy-text").value')).summary.selected,3);
 await screenshot('manual-copy','#selection-copy-fallback');await evaluate('document.getElementById("close-selection-copy").click()');
 await evaluate('window.realSetItem=Storage.prototype.setItem;Storage.prototype.setItem=function(){throw Error("quota")};setChoice("P02","A")');
 assert.equal(await evaluate('choices.get("P02")'),'A');assert(await evaluate('document.getElementById("selection-save-status").textContent.includes("未允许自动保存")'));
 await evaluate('Storage.prototype.setItem=window.realSetItem;saveChoices();updateChoiceUI()');
 await evaluate('CASES.forEach((c,i)=>setChoice(c.id,i%2?"B":"A"))');assert.equal(await evaluate('choices.size'),39);
 assert.equal(await evaluate('choicePayload().selections.filter(x=>x.variant===null).length'),0);
 const overflows=[];
 for(const width of [1440,1180,390,320]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:width<700});
  for(const d of ['compare','A','B'])for(const s of [1,1.3]){
   await run((d,s)=>{design=d;fontScale=s;category="all";query="";gallery();themeApply();},d,s);
   const overflow=await evaluate('document.documentElement.scrollWidth>innerWidth+1||Array.from(document.querySelectorAll(".stage,.choice-control,.selection-tools")).some(x=>x.scrollWidth>x.clientWidth+2)');
   if(overflow)overflows.push({width,d,s});
  }
 }
 assert.deepEqual(overflows,[]);
 await evaluate('theme="MORNING_BELL";mode="dark";design="compare";category="query";fontScale=1.3;gallery();themeApply();document.getElementById("toast").hidden=true');await screenshot('phone-selection');
 await evaluate('show("Q02")');assert.equal(await evaluate('document.querySelector("#viewer-meta [data-pick-value="+choices.get("Q02")+"]").getAttribute("aria-pressed")'),'true');await screenshot('phone-viewer-choice','#viewer');
 await evaluate('document.getElementById("viewer").close();CASES.forEach(c=>setChoice(c.id,null))');
 assert.equal(await evaluate('choices.size'),0);assert(await evaluate('document.getElementById("export-choices").disabled'));
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({cases:39,downloaded,validationFailuresRejected:10,layouts:24,errors,network},null,2));
 console.log(JSON.stringify({cases:39,downloaded,validationFailuresRejected:10,layouts:24,browserErrors:errors.length,httpRequests:network.length,output:out}));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{try{if(socket?.readyState===1)await send('Browser.close');}catch{}socket?.close();if(browser&&!browser.killed)browser.kill();});
