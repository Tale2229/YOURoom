const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'youroom-morning-'));
const proc=spawn(auditPaths.browser(),[
 '--headless=new','--remote-debugging-port=9367','--no-first-run','--no-default-browser-check',
 '--disable-extensions','--disable-background-networking','--disable-sync','--user-data-dir='+profile,'about:blank'
],{windowsHide:true,stdio:'ignore'});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
let socket,seq=0,assertions=0;const pending=new Map(),errors=[],requests=[];
function check(ok,message){assertions++;if(!ok)throw new Error(message)}
async function main(){
 let info;for(let i=0;i<100;i++){try{info=(await fetch('http://127.0.0.1:9367/json/list').then(r=>r.json())).find(t=>t.type==='page');if(info)break}catch{}await pause(100)}
 check(info,'Chrome started');socket=new WebSocket(info.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
 socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}}
 if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);
 if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url)};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}))});
 const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 const click=selector=>js(`document.querySelector(${JSON.stringify(selector)}).click()`);
 const view=width=>send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
 const shot=async name=>{await pause(300);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(auditPaths.output,'晨钟暮鼓-'+name+'.png'),Buffer.from(r.data,'base64'))};
 const overflow=()=>js(`(()=>{const p=document.querySelector('.phone'),v=document.querySelector('#viewport');return document.documentElement.scrollWidth<=innerWidth&&p.scrollWidth<=p.clientWidth+1&&v.scrollWidth<=v.clientWidth+1})()`);
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await view(1180);
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/晨钟暮鼓/晨钟暮鼓-preview.html')).href});
 for(let i=0;i<100;i++){if(await js("document.readyState==='complete'&&document.querySelectorAll('.room').length===4"))break;await pause(50)}
 check(await js("document.title.includes('晨钟暮鼓')"),'Name');
 check(await js("document.querySelectorAll('[data-slot]').length===14"),'14 periods');
 check(await js("(()=>{const ids=[...document.querySelectorAll('[id]')].map(x=>x.id);return new Set(ids).size===ids.length})()"),'Unique IDs');
 check(await js("document.documentElement.dataset.edition==='3'&&document.querySelector('[data-edition-choice=\"3\"]').getAttribute('aria-pressed')==='true'"),'Approved final edition defaults');
 const values={};
 for(const edition of ['1','2','3']){
 await click('[data-edition-choice="'+edition+'"]');
 for(const mode of ['light','dark']){
  if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
  const ratios=await js("[...document.querySelectorAll('.contrast-row')].map(x=>[x.textContent,+x.dataset.ratio,+x.dataset.min])");
  check(ratios.length===19&&ratios.every(([,r,min])=>r>=min),mode+' contrast '+JSON.stringify(ratios));
  check(await js("[...document.querySelectorAll('.ratio i:last-child span')].every(x=>getComputedStyle(x).display!=='none'&&x.getBoundingClientRect().width>0)"),mode+' three detail swatches visible');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--launch-face')!==getComputedStyle(document.documentElement).getPropertyValue('--relaxed')"),mode+' launch and relaxed distinct');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--nav-active')!==getComputedStyle(document.documentElement).getPropertyValue('--favorite')"),mode+' blue navigation not vermilion');
  const colors=await js("(()=>{const css=getComputedStyle(document.documentElement),v=k=>css.getPropertyValue('--'+k).trim();return {rail:v('rail-selected'),corner:v('favorite'),primary:v('primary'),strip:v('period-active')||v('primary'),divider:v('list-divider')||v('rule'),paper:v('paper'),header:v('header-face')}})()");
  values[edition+'-'+mode]=colors;
  if(edition!=='1'){
   const first=values['1-'+mode];check(colors.rail===first.corner&&colors.corner===first.rail,mode+' exact four-action / corner swap');
   if(edition==='2')check(colors.primary!==colors.strip&&colors.divider!==first.divider,mode+' independent purple strip and warm divider');
   if(edition==='3')check(colors.primary===first.primary&&colors.strip===first.strip&&colors.divider===first.divider&&colors.paper===first.paper&&colors.header===first.header,mode+' final retains first-edition paper sky blue strips and neutral lines');
   check(await js("contrast(getComputedStyle(document.documentElement).getPropertyValue('--plan-star').trim(),getComputedStyle(document.documentElement).getPropertyValue('--paper').trim())>=4.5"),mode+' plan star contrast');
   if(edition==='2'&&mode==='light')check(await js("luminance(getComputedStyle(document.documentElement).getPropertyValue('--paper').trim())>luminance('#F5F6FA')&&luminance(getComputedStyle(document.documentElement).getPropertyValue('--header-face').trim())>luminance('#8EA4CA')"),'Paper and sky are brighter');
  }
  for(const page of ['rooms','planner','settings']){await click('[data-page="'+page+'"]');await pause(280);check(await overflow(),edition+' '+mode+' '+page+' overflow');await shot('v'+edition+'-'+mode+'-'+page)}
  await click('[data-page="rooms"]');
  await js("rooms[0].excluded=true;renderRooms()");await click('.room .more');await pause(280);
  check(await js("(()=>{const buttons=[...document.querySelectorAll('.room .action-rail button.marked')].slice(0,4);return buttons.length===4&&buttons.every(x=>getComputedStyle(x).color===getComputedStyle(buttons[0]).color)})()"),edition+' '+mode+' all four selected actions match');
  if(edition!=='1')check(await js("getComputedStyle(document.querySelector('.room .action-rail .marked')).color==='rgb('+(document.documentElement.dataset.mode==='light'?'159, 70, 59':'229, 156, 142')+')'"),mode+' selected icons rendered vermilion');
  await shot('v'+edition+'-'+mode+'-actions');await js("rooms[0].excluded=false;renderRooms()");
 }
 }
 await click('#mode');await pause(280);
 await click('[data-edition-choice="1"]');await pause(280);
 check(await js("getComputedStyle(document.querySelector('.app-head')).backgroundColor==='rgb(142, 164, 202)'"),'Original sky blue header');
 check(await js("getComputedStyle(document.querySelector('.corner')).borderTopColor==='rgb(159, 70, 59)'"),'Vermilion favorite');
 check(await js("document.querySelector('.impression').getAttribute('aria-label').includes('银锡钟')"),'New bell and drum art');
 await click('[data-edition-choice="2"]');await pause(280);
 check(await js("getComputedStyle(document.querySelector('.corner')).borderTopColor==='rgb(98, 84, 102)'"),'V2 corner actually uses old purple');
 check(await js("getComputedStyle(document.querySelector('.strip .available')).fill==='rgb(119, 96, 143)'&&getComputedStyle(document.querySelector('.room')).borderBottomColor==='rgb(232, 186, 180)'"),'V2 rendered purple strips and light vermilion dividers');
 await click('[data-page="rooms"]');await click('[data-slot="1"]');check(await js("state.slots.has(1)"),'Slot selection');
 await click('.room .more');check(await js("document.querySelector('.room').classList.contains('revealed')"),'Action rail');await shot('light-actions');
 await click('.room [data-action="tag"]');await click('#add-tag');await js("document.querySelector('#tag-name').value='测试'");await click('#tag-confirm');
 check(await js("document.querySelector('#dialog-exit').dataset.dirty==='true'"),'Changed tags show check');
 await click('#dialog-exit');check(await js("document.querySelector('#dialog-exit').classList.contains('confirming')&&!document.querySelector('#modal').hidden"),'Confirmation animation before close');await pause(550);
 check(await js("document.querySelector('#modal').hidden&&rooms[0].tags.includes('测试')"),'Commit tags');
 await click('.room .more');await click('.room [data-action="note"]');await js("document.querySelector('#note-input').value='靠窗自习';document.querySelector('#note-input').dispatchEvent(new Event('input'))");await click('#dialog-exit');await pause(550);check(await js("rooms[0].note==='靠窗自习'"),'Commit note');
 await click('[data-page="planner"]');await click('#expected');await click('[data-expected="3-305"]');await click('#dialog-exit');await pause(550);check(await js("state.expected.has('3-305')"),'Expected room');
 await click('#relaxed');check(await js("document.querySelector('#notice').textContent==='已关闭宽松模式'"),'Relaxed notice');
 await click('#add-range');check(await js("document.querySelector('#notice').textContent.includes('最多添加三个时段')"),'Capacity notice');
 await click('[data-range="0"]');await click('#delete-period');check(await js('state.ranges.length===2'),'Delete period');
 await click('#add-range');check(await js('state.ranges.length===3'),'Add period');
 await click('#generate');check(await js("document.querySelectorAll('.plan-room').length===2"),'Mock plan');
 await click('#other-plans');check(await js("!document.querySelector('#plan-options').hidden&&document.querySelector('#other-plans').getAttribute('aria-expanded')==='true'"),'Other plans');
 await click('[data-page="settings"]');await click('#style-toggle');await click('[data-style="1"]');check(await js('state.proportional'),'Proportional style');
 await click('#numbers');check(await js("state.numbers&&document.querySelectorAll('.numbers').length>0"),'Period numbers');
 await click('#campus-toggle');await click('[data-campus="沙河"]');check(await js("document.querySelector('#campus-value').textContent==='沙河'"),'Campus selection');
 await click('[data-page="planner"]');await click('#expected');await click('[data-expected="3-308"]');
 const snapshot="JSON.stringify({page:state.page,slots:[...state.slots],ranges:state.ranges,expected:[...state.expected],numbers:state.numbers,proportional:state.proportional,dirty:document.querySelector('#dialog-exit').dataset.dirty,checked:[...document.querySelectorAll('[data-expected]')].map(x=>x.getAttribute('aria-pressed'))})";
 const before=await js(snapshot);for(const edition of ['1','2','3']){await click('[data-edition-choice="'+edition+'"]');check(await js(snapshot)===before,'Edition '+edition+' preserves draft')}
 await click('#mode');check(await js(snapshot)===before,'Dark toggle preserves draft');await click('#mode');check(await js(snapshot)===before,'Light toggle preserves draft');await click('#dialog-exit');await pause(550);check(await js('state.expected.size===2'),'Draft remains committable');
 await click('#wave-preview');check(await js("document.querySelector('.room .strip').dataset.waveState==='waiting'"),'Wave waits');
 const bounds=await js("(()=>{const r=document.querySelector('.room .strip').getBoundingClientRect();return JSON.stringify([r.width,r.height])})()");
 await pause(420);check(await js("document.querySelector('.room .strip').dataset.waveState==='playing'"),'Wave plays');
 check(await js("document.querySelector('.room .strip path').getAttribute('d').includes('L')"),'Continuous wave curve');
 check(await js("(()=>{const r=document.querySelector('.room .strip').getBoundingClientRect();return JSON.stringify([r.width,r.height])})()")===bounds,'Wave layout unchanged');await pause(450);check(await js("!document.querySelector('.strip.wave')"),'Wave cleanup');
 for(const edition of ['1','2','3']){
 await click('[data-edition-choice="'+edition+'"]');
 for(const mode of ['light','dark']){
  if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
  for(const width of [390,320]){await view(width);for(const page of ['rooms','planner','settings']){await click('[data-page="'+page+'"]');await pause(280);check(await overflow(),width+' '+mode+' '+page+' overflow')}
   await shot('v'+edition+'-'+mode+'-mobile-'+width);await click('[data-page="planner"]');await click('#expected');check(await js("document.querySelector('.modal-card').scrollWidth<=document.querySelector('.modal-card').clientWidth+1"),'Narrow dialog');await click('#dialog-exit');
  }
 }
 }
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 check(await js("parseFloat(getComputedStyle(document.querySelector('.screen')).animationDuration)<.01"),'Reduced motion');await click('#wave-preview');await pause(30);check(await js("!document.querySelector('.strip.wave')"),'Reduced motion skips wave');
 await click('#reset');check(await js("state.page==='rooms'&&state.slots.size===3&&state.ranges.length===3&&!state.expected.size&&!state.numbers&&!state.proportional"),'Reset');
 const links=await js("[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href'))");for(const link of links)check(fs.existsSync(path.resolve('docs/colors/晨钟暮鼓',link)),'Local HTML link '+link);
 for(const name of ['晨钟暮鼓/晨钟暮鼓.md','配色方案图鉴.md']){for(const m of fs.readFileSync(path.join('docs/colors',name),'utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){if(/^(https?:|#)/.test(m[1]))continue;check(fs.existsSync(path.resolve('docs/colors',path.dirname(name),m[1])),'Local MD link '+m[1])}}
 check(!requests.some(u=>/^https?:/.test(u)),'Offline, no external requests');check(!errors.length,'No runtime errors '+errors.join('\n'));
 console.log(JSON.stringify({passed:assertions,colorModes:2,editions:3,widths:[1180,390,320],runtimeErrors:errors.length,externalRequests:requests.filter(u=>/^https?:/.test(u)).length}));
}
const watchdog=setTimeout(()=>{console.error('Preview check timeout');socket?.close();proc.kill();process.exit(1)},120000);
main().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>{clearTimeout(watchdog);socket?.close();proc.kill()});
