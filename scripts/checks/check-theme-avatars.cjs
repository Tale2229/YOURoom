const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const sourceThemes=require('./read-avatar-palettes.cjs');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'youroom-theme-avatars-'));
const proc=spawn(auditPaths.browser(),[
 '--headless=new','--remote-debugging-port=9383','--no-first-run','--no-default-browser-check',
 '--disable-extensions','--disable-background-networking','--disable-sync','--user-data-dir='+profile,'about:blank'
],{windowsHide:true,stdio:'ignore'});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let socket,seq=0,assertions=0;const pending=new Map(),errors=[],requests=[],failures=[];
function check(ok,message){assertions++;if(!ok)throw new Error(message)}
async function main(){
 let info;for(let i=0;i<100;i++){try{info=(await fetch('http://127.0.0.1:9383/json/list').then(r=>r.json())).find(t=>t.type==='page');if(info)break}catch{}await pause(100)}
 check(info,'Chrome started');socket=new WebSocket(info.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
 socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}
  if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);
  if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url);
  if(m.method==='Network.loadingFailed')failures.push(m.params.errorText);
 };
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}))});
 const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 const click=selector=>js(`document.querySelector(${JSON.stringify(selector)}).click()`);
 const view=width=>send('Emulation.setDeviceMetricsOverride',{width,height:1080,deviceScaleFactor:1,mobile:false});
 const shot=async name=>{await pause(260);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(auditPaths.output,'theme-avatars-'+name+'.png'),Buffer.from(r.data,'base64'))};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await view(1440);
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/theme-avatars-preview.html')).href});
 for(let i=0;i<100;i++){if(await js("document.readyState==='complete'&&document.querySelectorAll('.study').length===12"))break;await pause(100)}
 check(await js("document.querySelectorAll('.study').length===12"),'Twelve settings pages');
 check(await js("[...document.querySelectorAll('.avatar')].every(x=>x.complete&&x.naturalWidth===1254&&x.naturalHeight===1254)"),'Twelve full images load');
 check(await js("(()=>{const ids=[...document.querySelectorAll('[id]')].map(x=>x.id);return new Set(ids).size===ids.length})()"),'Unique IDs');
 check(await js("[...document.querySelectorAll('use')].every(x=>document.querySelector(x.getAttribute('href')))"),'SVG references valid');
 const data=await js("JSON.parse(document.querySelector('#theme-data').textContent)");
 for(let i=0;i<12;i++)for(const mode of ['light','dark'])for(const [key,value]of Object.entries(data[i][mode]))check(value===sourceThemes[i][mode][key],`Exact native palette ${i} ${mode} ${key}`);
 for(const mode of ['light','dark']){
  if(mode==='dark')await click('#mode-toggle');
  check(await js(`[...document.querySelectorAll('.dark-switch')].every(x=>x.getAttribute('aria-checked')==='${mode==='dark'}')`),'All mode switches '+mode);
  const colors=await js(`(()=>{const normalize=v=>{const n=document.createElement('i');n.style.color=v;document.body.append(n);const c=getComputedStyle(n).color;n.remove();return c};return [...document.querySelectorAll('.study')].map((card,i)=>{const t=JSON.parse(document.querySelector('#theme-data').textContent)[i]['${mode}'],color=(s,p)=>getComputedStyle(card.querySelector(s))[p];return [color('.phone','backgroundColor')===normalize(t.surface),color('.app-head','backgroundColor')===normalize(t.header),color('.nav','backgroundColor')===normalize(t.navigation),color('.nav-item.active','backgroundColor')===normalize(t.navigationSelected),color('.nav-item.active','color')===normalize(t.onNavigationSelected),color('.frame','backgroundColor')===normalize(t.impressionFrame),color('.row>.icon','color')===normalize(t.settingsIcon),color('.settings-foot','borderTopColor')===normalize(t.listDivider)]})})()`);
  colors.forEach((results,i)=>results.forEach((ok,j)=>check(ok,`Rendered color ${mode} ${i} ${j}`)));
  for(const width of [1440,1024,768,660,390,320]){
   await view(width);await pause(80);
   check(await js("document.documentElement.scrollWidth<=innerWidth"),'No document overflow '+mode+' '+width);
   check(await js("[...document.querySelectorAll('.phone')].every(p=>p.scrollWidth<=p.clientWidth+1)"),'No phone overflow '+mode+' '+width);
   check(await js("[...document.querySelectorAll('.avatar')].every(x=>{const r=x.getBoundingClientRect();return Math.abs(r.width-75)<.01&&Math.abs(r.height-75)<.01})"),'75px art '+mode+' '+width);
   check(await js("[...document.querySelectorAll('.frame')].every(x=>{const r=x.getBoundingClientRect();return Math.abs(r.width-95)<.01&&Math.abs(r.height-95)<.01})"),'10px frame '+mode+' '+width);
  }
  await view(1440);
  check(await js("[...document.querySelectorAll('.settings')].every(x=>x.scrollHeight<=x.clientHeight+1)"),'Default settings entirely visible '+mode);
  await shot(mode);
  if(mode==='dark'){await view(390);await shot('mobile-dark');}
 }
 await click('#reset');await view(390);
 await click('[data-card="0"] .dark-switch');check(await js("states[0].dark&&!states[1].dark"),'Independent dark mode');
 await click('[data-card="0"] .numbers-switch');check(await js('states[0].numbers'),'Number switch');
 await click('[data-card="0"] .disclosure');check(await js("document.querySelector('#strip-0').inert===false"),'Disclosure reveals focusable options');
 await click('[data-card="0"] [data-value="等比例"]');check(await js("states[0].strip==='等比例'&&document.querySelector('[data-card=\"0\"] .value').textContent==='等比例'"),'Strip choice updates');
 await js("document.querySelector('[data-card=\"0\"] [data-value=\"等比例\"]').focus()");await send('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowUp',code:'ArrowUp'});check(await js("states[0].strip==='等间距'"),'Radio keyboard works');
 await click('[data-card="0"] .disclosure');check(await js("document.querySelector('#strip-0').inert===true"),'Collapsed options inert');
 await click('[aria-controls="campus-0"]');await click('[data-card="0"] [data-value="沙河"]');check(await js("states[0].campus==='沙河'"),'Campus choice');
 await click('[data-card="0"] .previous');check(await js("states[0].index===11&&document.querySelector('[data-card=\"0\"] h2').textContent==='蒙德里安'"),'Theme wraps backward');
 await click('[data-card="0"] .next');check(await js('states[0].index===0&&states[0].dark'),'Theme wraps forward retaining dark mode');
 await js("document.querySelector('[data-card=\"0\"] .frame').focus()");await click('[data-card="0"] .frame');
 check(await js("document.querySelector('#lightbox').open"),'Large image opens');
 for(let i=0;i<12;i++){
  check(await js(`document.querySelector('#lightbox-title').textContent===${JSON.stringify(data[i].name+(data[i].id==='BURGUNDY'?' · B · 杯下铺布':''))}`),'Lightbox title '+i);
  await js("document.querySelector('#full-art').decode()");
  check(await js("document.querySelector('#full-art').naturalWidth===1254"),'Lightbox image '+i);
  await click('#next-art');
 }
 check(await js('artIndex===0'),'Lightbox wraps');
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
 await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await pause(80);
 check(await js("!document.querySelector('#lightbox').open"),'Escape closes');
 check(await js("document.activeElement.matches('.frame')"),'Focus restored');
 await click('#reset');await shot('mobile-light');
 const burgundy=data.findIndex(t=>t.id==='BURGUNDY');
 check(await js(`burgundyChoice===2&&document.querySelector('[data-card="${burgundy}"] .avatar').getAttribute('src')==='勃艮第/avatar-generated-v4.png'`),'Confirmed B is the default settings image');
 check(await js("document.querySelectorAll('.card-variants:not([hidden])').length===1"),'Only burgundy displays variants');
 for(const mode of ['light','dark']){
  if(mode==='dark')await click('#mode-toggle');
  for(let variant=0;variant<4;variant++){
   const selector=`[data-card="${burgundy}"] .variant-choice[data-variant="${variant}"]`;
   await click(selector);await js(`document.querySelector('[data-card="${burgundy}"] .avatar').decode()`);
   check(await js(`document.querySelector('[data-card="${burgundy}"] .avatar').getAttribute('src')==='勃艮第/avatar-generated-v${variant+2}.png'`),'Variant image '+mode+' '+variant);
   check(await js(`document.querySelectorAll('[data-card="${burgundy}"] .variant-choice[aria-checked=true]').length===1`),'Single active variant '+mode+' '+variant);
   await click(`[data-card="${burgundy}"] .frame`);
   check(await js(`document.querySelector('#full-art').getAttribute('src')==='勃艮第/avatar-generated-v${variant+2}.png'&&document.querySelector('#original-link').getAttribute('href')==='勃艮第/avatar-generated-v${variant+2}.png'`),'Large image and original follow variant '+mode+' '+variant);
   const next=(variant+1)%4;
   await click(`#dialog-variants [data-variant="${next}"]`);
   check(await js(`document.querySelector('[data-card="${burgundy}"] .avatar').getAttribute('src')==='勃艮第/avatar-generated-v${next+2}.png'`),'Dialog selection updates settings '+mode+' '+variant);
   await click('#close-lightbox');
  }
  for(const width of [1440,1024,768,660,390,320]){
   await view(width);
   check(await js("document.documentElement.scrollWidth<=innerWidth"),'Variant layout no overflow '+mode+' '+width);
  }
 }
 await js(`document.querySelector('[data-card="${burgundy}"] .variant-choice[data-variant="0"]').focus()`);
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'End',code:'End'});
 check(await js('burgundyChoice===3'),'Variant keyboard End');
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowRight',code:'ArrowRight'});
 check(await js('burgundyChoice===0'),'Variant keyboard wrap');
 await click(`[data-card="${burgundy}"] .next`);check(await js(`document.querySelector('[data-card="${burgundy}"] .card-variants').hidden`),'Variants hidden on other themes');
 await click(`[data-card="${burgundy}"] .previous`);check(await js(`!document.querySelector('[data-card="${burgundy}"] .card-variants').hidden`),'Variants restored returning to burgundy');
 await view(1440);await click(`[data-card="${burgundy}"] [data-variant="2"]`);
 await js(`document.querySelector('[data-card="${burgundy}"]').scrollIntoView({block:'start'})`);await shot('burgundy-options-desktop');
 await view(390);await js(`document.querySelector('[data-card="${burgundy}"]').scrollIntoView({block:'start'})`);await shot('burgundy-options-mobile');
 await click('#reset');check(await js('burgundyChoice===2'),'Reset restores confirmed B variant');
 await view(390);await js("document.querySelector('[data-card=\"10\"]').scrollIntoView({block:'start'})");
 await js("document.querySelector('[data-card=\"10\"] .avatar').decode()");
 check(await js("document.querySelector('[data-card=\"10\"] .avatar').getAttribute('src')==='晨钟暮鼓/avatar-generated-v7.png'"),'Morning bell horizontal mist sky artwork');
 await shot('morning-bell-mobile');
 await js("document.querySelector('[data-card=\"8\"]').scrollIntoView({block:'start'})");
 await js("document.querySelector('[data-card=\"8\"] .avatar').decode()");
 check(await js("document.querySelector('[data-card=\"8\"] .avatar').getAttribute('src')==='入骨相思/avatar-generated-v4.png'"),'Red bean curved background artwork');
 await shot('red-bean-mobile');
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await click('[data-card="0"] .next');
 check(await js("document.querySelector('.avatar').getAnimations().length===0"),'Reduced motion');
 check(errors.length===0,'No runtime errors '+errors.join('\n'));check(failures.length===0,'No failed resources '+failures.join('\n'));
 check(requests.every(url=>url.startsWith('file:')||url.startsWith('data:')),'Fully offline');
 console.log(JSON.stringify({passed:assertions,themes:12,modes:2,viewports:6,images:'12 loaded, 75×75 display',nativeColors:'exact match',runtimeErrors:errors.length}));
 await send('Browser.close');
}
main().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>{if(socket)socket.close();proc.kill()});
