const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'youroom-matisse-avatars-'));
const proc=spawn(auditPaths.browser(),[
 '--headless=new','--remote-debugging-port=9381','--no-first-run','--no-default-browser-check',
 '--disable-extensions','--disable-background-networking','--disable-sync','--user-data-dir='+profile,'about:blank'
],{windowsHide:true,stdio:'ignore'});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let socket,seq=0,assertions=0;const pending=new Map(),errors=[],requests=[];
function check(ok,message){assertions++;if(!ok)throw new Error(message)}
async function main(){
 let info;for(let i=0;i<100;i++){try{info=(await fetch('http://127.0.0.1:9381/json/list').then(r=>r.json())).find(t=>t.type==='page');if(info)break}catch{}await pause(100)}
 check(info,'Chrome started');socket=new WebSocket(info.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
 socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}
  if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);
  if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}))});
 const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 const click=selector=>js(`document.querySelector(${JSON.stringify(selector)}).click()`);
 const view=width=>send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
 const shot=async(name,clip)=>{await pause(250);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,...(clip?{clip}: {})});fs.writeFileSync(path.join(auditPaths.output,'matisse-avatars-'+name+'.png'),Buffer.from(r.data,'base64'))};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await view(1360);
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/马蒂斯/matisse-avatar-options.html')).href});
 for(let i=0;i<100;i++){if(await js("document.readyState==='complete'&&document.querySelectorAll('[data-option]').length===5"))break;await pause(50)}
 await pause(500);
 check(await js("document.querySelectorAll('[role=radio]').length===5"),'Five distinct options');
 check(await js("(()=>{const ids=[...document.querySelectorAll('[id]')].map(x=>x.id);return new Set(ids).size===ids.length})()"),'Unique IDs');
 check(await js("[...document.querySelectorAll('use')].every(x=>document.querySelector(x.getAttribute('href')))"),'All SVG references resolve');
 const palette=new Set(['#D2DECD','#343E47','#AA3D36','#E3EAAB','#8F603D','none']);
 const colors=await js("[...document.querySelectorAll('symbol[id^=art-] [fill],symbol[id^=art-] [stroke]')].flatMap(x=>[x.getAttribute('fill'),x.getAttribute('stroke')]).filter(Boolean)");
 check(colors.every(c=>palette.has(c)),'Only original five colors');
 for(const id of ['a','b','c','d','e']){
  await click(`[data-option=${id}]`);
  check(await js(`document.querySelector('#native-art use').getAttribute('href')==='#art-${id}'`),'Live artwork '+id);
  check(await js("document.querySelectorAll('[role=radio][aria-checked=true]').length===1"),'Single selection '+id);
  check(await js("document.querySelectorAll('[data-option][tabindex=\"0\"]').length===1"),'Roving tab index '+id);
  check(await js(`document.querySelector('#selected-letter').textContent==='${id.toUpperCase()}'`),'Copy follows '+id);
 }
 await click('#next');check(await js("document.querySelector('#selected-letter').textContent==='A'"),'Arrow wrap forward');
 await click('#previous');check(await js("document.querySelector('#selected-letter').textContent==='E'"),'Arrow wrap backward');
 await js("document.querySelector('[data-option=e]').focus()");
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowRight',code:'ArrowRight'});
 check(await js("document.activeElement.dataset.option==='a'"),'Keyboard wraps and moves focus');
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'End',code:'End'});
 check(await js("document.activeElement.dataset.option==='e'"),'Keyboard End');
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Home',code:'Home'});
 check(await js("document.activeElement.dataset.option==='a'"),'Keyboard Home');
 await click('#period-numbers');check(await js("document.querySelector('#period-numbers').getAttribute('aria-checked')==='true'"),'Number switch works');
 for(const mode of ['light','dark']){
  if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode-toggle');
  check(await js(`document.querySelector('#dark-mode').getAttribute('aria-checked')==='${mode==='dark'}'`),'Mode switch synchronized '+mode);
  for(const width of [1360,1024,830,768,590,390,320]){
   await view(width);await pause(240);
   check(await js("document.documentElement.scrollWidth<=innerWidth"),'No page overflow '+mode+' '+width);
   check(await js("(()=>{const p=document.querySelector('.phone');return p.scrollWidth<=p.clientWidth+1})()"),'No phone overflow '+mode+' '+width);
   check(await js("(()=>{const r=document.querySelector('#native-art').getBoundingClientRect();return Math.abs(r.width-75)<.1&&Math.abs(r.height-75)<.1})()"),'75px art '+mode+' '+width);
   if(await js('document.documentElement.dataset.size')!=='actual')await click('#size-toggle');
   await pause(260);
   check(await js("[...document.querySelectorAll('.gallery .art')].every(x=>Math.abs(x.getBoundingClientRect().width-75)<.1)"),'True-size gallery '+mode+' '+width);
   await click('#size-toggle');
  }
  await view(1360);await pause(260);await shot(mode);
 }
 await view(390);await pause(260);await shot('mobile-dark');
 await click('#dark-mode');check(await js("document.documentElement.dataset.mode==='light'&&document.querySelector('#mode-toggle').getAttribute('aria-pressed')==='false'"),'Phone mode switch synchronized');
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 await click('[data-option=b]');
 check(await js("document.querySelector('#native-art').getAnimations().length===0"),'Reduced motion respected');
 check(errors.length===0,'No browser errors '+errors.join('\n'));
 check(requests.every(url=>url.startsWith('file:')||url.startsWith('data:')),'Offline only');
 console.log('OK '+assertions+' checks; five artwork options, all sizes, light/dark, keyboard and offline verified');
 await send('Browser.close');
}
main().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>{if(socket)socket.close();proc.kill()});
