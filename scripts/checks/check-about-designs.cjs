const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const output=path.resolve('artifacts/qa/about-design-qa');
const pending=new Map();let browser,socket,serial=0;
const exceptions=[],network=[];
async function send(method,params={}){const id=++serial;const result=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return result;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function screenshot(name,selector){await pause(700);const clip=selector?await run(s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};},selector):{x:0,y:0,width:1440,height:1700,scale:1};const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip});fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(r.data,'base64'));}
async function main(){
 fs.mkdirSync(output,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9451','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/about-design-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<100;i++){try{target=(await(await fetch('http://127.0.0.1:9451/json/list')).json()).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}
 assert(target);socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')exceptions.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result);}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/about/about-design-preview.html')).href});
 for(let i=0;i<70;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".about-sheet").length===3'))break;await pause(100);}
 assert.equal(await evaluate('document.querySelectorAll(".study").length'),3);
 const content=await run(()=>[...document.querySelectorAll('.about-sheet')].map(sheet=>({
  missing:[DESCRIPTION,DISCLAIMER,AUTHOR,VERSION,...DATA,...LOCAL].filter(text=>!sheet.textContent.includes(text)),
  links:[...sheet.querySelectorAll('a[target=_blank]')].map(a=>({href:a.href,target:a.target,rel:a.rel})),
  version:sheet.querySelector('.version').textContent
 })));
 for(const c of content){assert.deepEqual(c.missing,[]);assert.deepEqual(c.links.map(l=>l.href),['https://github.com/Tale2229/YOURoom','https://github.com/Tale2229/YOURoom/issues']);for(const l of c.links){assert.equal(l.target,'_blank');assert(l.rel.includes('noopener'));}}
 const contrastFailures=await run(()=>{const bad=[];for(const t of THEMES)for(const m of ['light','dark']){const p=palette(t,m);for(const [a,b]of [['paper','ink'],['paper','muted'],['control','ink'],['control','muted'],['low','ink'],['low','muted'],['strong','on-strong'],['accent','on-accent']]){const ratio=contrast(p[a],p[b]);if(ratio<4.5)bad.push({theme:t.id,mode:m,a,b,ratio});}}return bad;});
 assert.deepEqual(contrastFailures,[]);
 await screenshot('desktop-three-designs-light');
 for(const id of ['a','b','c'])await screenshot('matisse-'+id+'-light','.study[data-design='+id+']');
 const problems=[];let cases=0;
 for(const width of [1440,1180,390,320]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<500});
  for(const t of await evaluate('THEMES.map(t=>t.id)'))for(const m of ['light','dark'])for(const scale of [1,1.3]){
   await run((id,m,s)=>{selectedTheme=id;mode=m;textScale=s;update();},t,m,scale);await pause(20);
   const issue=await run(()=>{const issues=[];if(document.documentElement.scrollWidth>innerWidth+1)issues.push('document horizontal overflow');for(const sheet of document.querySelectorAll('.about-sheet')){const r=sheet.getBoundingClientRect();for(const el of sheet.querySelectorAll('.product-name,.paragraphs,.link-row,.b-link,.info-heading,.author-name,.version,.shortcut,.eyebrow')){const b=el.getBoundingClientRect();if(b.left<r.left-1||b.right>r.right+1||el.scrollWidth>el.clientWidth+1)issues.push(sheet.getAttribute('aria-label')+': '+el.className+' overflow');}}return issues;});
   if(issue.length)problems.push({width,t,m,scale,issue});cases++;
  }
 }
 assert.deepEqual(problems,[]);
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await run(()=>{selectedTheme='MORNING_BELL';mode='dark';textScale=1;update();});
 await screenshot('desktop-three-designs-dark');
 await run(()=>{selectedTheme='MATISSE';mode='light';view='window';update();});
 for(const id of ['a','b','c']){
  await run(id=>{const sheet=document.querySelector('.study[data-design='+id+']');sheet.querySelector('.reading-body').scrollTop=99999;},id);
  const visible=await run(id=>{const study=document.querySelector('.study[data-design='+id+']'),button=study.querySelector('.close-about'),phone=study.querySelector('.phone');const a=button.getBoundingClientRect(),b=phone.getBoundingClientRect();return a.top>=b.top&&a.bottom<=b.bottom;},id);assert(visible);
  await run(id=>document.querySelector('.study[data-design='+id+'] .close-about').click(),id);
  assert.equal(await run(id=>document.querySelector('.study[data-design='+id+'] .about-sheet').hidden,id),true);
  await run(id=>document.querySelector('.study[data-design='+id+'] .reopen button').click(),id);
  assert.equal(await run(id=>document.querySelector('.study[data-design='+id+'] .about-sheet').hidden,id),false);
 }
 await run(()=>{const select=document.getElementById('design-select');select.value='c';select.onchange({target:select});});
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await run(()=>{document.querySelector('.design-c .reading-body').scrollTop=0;});await screenshot('mobile-c-window','.study[data-design=c]');
 await run(()=>{document.querySelector('.design-c [data-jump=local]').click();});await pause(700);
 assert(await evaluate('document.querySelector(".design-c .reading-body").scrollTop>0'));
 await screenshot('mobile-c-local','.study[data-design=c]');
 await run(()=>{view='full';textScale=1.3;update();});await screenshot('mobile-c-large','.study[data-design=c]');
 assert.deepEqual(exceptions,[]);assert.deepEqual(network,[]);
 console.log(JSON.stringify({ok:true,designs:3,themes:12,contrastPairs:192,responsiveStates:cases,copyAndLinks:true,closeAndReopen:true,inSheetNavigation:true,automaticNetworkRequests:0,screenshots:fs.readdirSync(output).filter(f=>f.endsWith('.png'))},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{try{if(socket)await send('Browser.close');}catch{}if(socket)socket.close();if(browser)browser.kill();});
