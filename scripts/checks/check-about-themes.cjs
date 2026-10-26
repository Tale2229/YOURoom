const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const output=path.resolve('artifacts/qa/about-themes-qa');
const pending=new Map();let browser,socket,serial=0;
const exceptions=[],network=[];
async function send(method,params={}){const id=++serial;const result=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return result;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function screenshot(name,selector){await pause(700);const clip=selector?await run(s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};},selector):{x:0,y:0,width:1440,height:1550,scale:1};const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip});fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(r.data,'base64'));}
async function main(){
 fs.mkdirSync(output,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9453','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/about-themes-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<100;i++){try{target=(await(await fetch('http://127.0.0.1:9453/json/list')).json()).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}
 assert(target);socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')exceptions.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result);}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/about/about-themes-preview.html')).href});
 for(let i=0;i<70;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".about-sheet").length===12'))break;await pause(100);}
 assert.equal(await evaluate('document.querySelectorAll(".study:not([hidden])").length'),12);
 const content=await run(()=>[...document.querySelectorAll('.about-sheet')].map(sheet=>({
  missing:[DESCRIPTION,DISCLAIMER,AUTHOR,VERSION,...DATA,...LOCAL].filter(text=>!sheet.textContent.includes(text)),
  descriptionInCover:sheet.querySelector('.a-cover .tagline')?.textContent===DESCRIPTION&&sheet.querySelector('.a-cover .disclaimer')?.textContent===DISCLAIMER,
  coverLinks:sheet.querySelectorAll('.a-cover a,.shortcut').length,oldDescription:sheet.querySelectorAll('.a-description').length,
  dataStillPresent:sheet.querySelector('[data-section=data]')?.textContent.includes('数据与使用'),
  localLast:sheet.querySelector('[data-section=local] li:last-child')?.textContent,
  oldY:sheet.querySelector('.folio-end')!==null,pig:sheet.querySelectorAll('.pig-original svg').length,
  pigFills:[...sheet.querySelectorAll(".pig-original path,.pig-original ellipse")].map(el=>getComputedStyle(el).fill),
  links:[...sheet.querySelectorAll('a[target=_blank]')].map(a=>a.href)
 })));
 for(const c of content){assert.deepEqual(c.missing,[]);assert(c.descriptionInCover);assert.equal(c.coverLinks,0);assert.equal(c.oldDescription,0);assert(c.dataStillPresent);assert.equal(c.localLast,'清除缓存需使用登录账密校验，不可恢复，请谨慎操作');assert.equal(c.oldY,false);assert.equal(c.pig,1);assert.deepEqual(c.pigFills,["rgb(255, 168, 167)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(230, 80, 143)","rgb(230, 80, 143)","rgb(255, 125, 134)","rgb(45, 45, 43)","rgb(45, 45, 43)","rgb(45, 45, 43)","rgb(45, 45, 43)"]);assert.deepEqual(c.links,['https://github.com/Tale2229/YOURoom','https://github.com/Tale2229/YOURoom/issues']);}
 const contrastFailures=await run(()=>{const bad=[];for(const t of THEMES)for(const m of ['light','dark']){const p=palette(t,m);for(const [a,b]of [['paper','ink'],['paper','muted'],['low','ink'],['low','muted'],['control','ink'],['control','muted'],['strong','on-strong'],['paper','primary']]){const ratio=contrast(p[a],p[b]);if(ratio<4.5)bad.push({theme:t.id,mode:m,a,b,ratio});}}return bad;});
 assert.deepEqual(contrastFailures,[]);
 await screenshot('desktop-first-row-light');
 const problems=[];let states=0;
 for(const width of [1440,1050,768,390,320]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<500});
  for(const m of ['light','dark'])for(const scale of [1,1.3])for(const v of ['full','window']){
   await run((m,s,v)=>{mode=m;textScale=s;view=v;update();},m,scale,v);await pause(25);
   const issue=await run(()=>{const issues=[];if(document.documentElement.scrollWidth>innerWidth+1)issues.push('document horizontal overflow');for(const sheet of document.querySelectorAll('.about-sheet')){const r=sheet.getBoundingClientRect();for(const el of sheet.querySelectorAll('.product-name,.tagline,.paragraphs,.link-row,.info-heading,.author-name,.version,.eyebrow,.pig-original')){const b=el.getBoundingClientRect();if(b.left<r.left-1||b.right>r.right+1||el.scrollWidth>el.clientWidth+1)issues.push(sheet.getAttribute('aria-label')+': '+el.className+' overflow');}const pig=sheet.querySelector('.pig-original');const size=innerWidth<=360?58.5:63;if(Math.abs(pig.getBoundingClientRect().width-size)>.1)issues.push('pig not 1.5x size');const shapes=[...pig.querySelectorAll('path,ellipse')];const fills=shapes.map(el=>getComputedStyle(el).fill);if(JSON.stringify(fills)!==JSON.stringify(["rgb(255, 168, 167)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(255, 210, 177)","rgb(230, 80, 143)","rgb(230, 80, 143)","rgb(255, 125, 134)","rgb(45, 45, 43)","rgb(45, 45, 43)","rgb(45, 45, 43)","rgb(45, 45, 43)"]))issues.push('pig original colors changed');if(shapes.some(el=>getComputedStyle(el).stroke!=='none'))issues.push('pig unexpectedly outlined');if(getComputedStyle(sheet.querySelector('.close-about')).color!==getComputedStyle(sheet.querySelector('.a-cover')).color)issues.push('close icon not readable on cover');}return issues;});
   if(issue.length)problems.push({width,m,scale,v,issue});states++;
  }
 }
 assert.deepEqual(problems,[]);
 await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
 await run(()=>{mode='dark';textScale=1;view='full';update();});await screenshot('desktop-first-row-dark');
 for(const id of await evaluate('THEMES.map(t=>t.id)')){
  await run(id=>{filter=id;mode='light';update();},id);
  assert.equal(await evaluate('document.querySelectorAll(".study:not([hidden])").length'),1);
  assert.equal(await evaluate('document.querySelector(".study:not([hidden])").dataset.theme'),id);
 }
 await run(()=>{filter='MATISSE';mode='light';view='full';update();});await screenshot('matisse-full','.study[data-theme=MATISSE]');
 await run(()=>{const study=document.querySelector('.study[data-theme=MATISSE]');study.querySelector('.close-about').click();});
 assert.equal(await evaluate('document.querySelector(".study[data-theme=MATISSE] .about-sheet").hidden'),true);
 assert.equal(await evaluate('document.querySelector(".study[data-theme=PINE] .about-sheet").hidden'),false);
 await run(()=>{mode='dark';update();});assert.equal(await evaluate('document.querySelector(".study[data-theme=MATISSE] .about-sheet").hidden'),true);
 await run(()=>document.querySelector('.study[data-theme=MATISSE] .reopen button').click());
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await run(()=>{mode='light';view='window';update();});await screenshot('mobile-matisse-window','.study[data-theme=MATISSE]');
 await run(()=>{document.querySelector('.study[data-theme=MATISSE] .reading-body').scrollTop=99999;});await screenshot('mobile-matisse-footer','.study[data-theme=MATISSE]');
 await run(()=>{filter='RED_BEAN';textScale=1.3;view='full';update();});await screenshot('mobile-red-bean-large','.study[data-theme=RED_BEAN]');
 assert.deepEqual(exceptions,[]);assert.deepEqual(network,[]);
 console.log(JSON.stringify({ok:true,themes:12,copyAndPosition:true,fixedOriginalPig:true,pigScale:1.5,contrastPairs:192,responsiveStates:states,themeLayouts:states*12,themeFilters:12,independentCloseReopen:true,automaticNetworkRequests:0,screenshots:fs.readdirSync(output).filter(f=>f.endsWith('.png'))},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{try{if(socket)await send('Browser.close');}catch{}if(socket)socket.close();if(browser)browser.kill();});
