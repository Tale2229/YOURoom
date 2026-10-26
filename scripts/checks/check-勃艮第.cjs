const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'youroom-burgundy-'));
const proc = spawn(auditPaths.browser(), [
  '--headless=new', '--remote-debugging-port=9361', '--no-first-run', '--no-default-browser-check',
  '--disable-extensions', '--disable-background-networking', '--disable-sync', '--user-data-dir='+profile, 'about:blank'
], {windowsHide:true, stdio:'ignore'});
const pause = ms => new Promise(r => setTimeout(r,ms));
let socket, seq=0, assertions=0;
const pending=new Map(), errors=[], requests=[];
function check(ok,message){assertions++;if(!ok)throw new Error(message);}
async function main(){
  let info;
  for(let i=0;i<100;i++){try{const targets=await fetch('http://127.0.0.1:9361/json/list').then(r=>r.json());info=targets.find(t=>t.type==='page');if(info)break;}catch{}await pause(100);}
  check(info,'Chrome started');
  socket=new WebSocket(info.webSocketDebuggerUrl);
  await new Promise((res,rej)=>{socket.onopen=res;socket.onerror=rej;});
  socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}}
    if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);
    if(m.method==='Network.requestWillBeSent')requests.push(m.params.request.url);
  };
  const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
  const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw new Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
  const click=selector=>js(`document.querySelector(${JSON.stringify(selector)}).click()`);
  const shot=async name=>{await pause(300);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(auditPaths.output,`勃艮第-${name}.png`),Buffer.from(r.data,'base64'));};
  const viewport=width=>send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
  const overflow=()=>js(`(()=>{const p=document.querySelector('.phone'),v=document.querySelector('#viewport');return document.documentElement.scrollWidth<=innerWidth&&p.scrollWidth<=p.clientWidth+1&&v.scrollWidth<=v.clientWidth+1})()`);
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await viewport(1180);
  await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/勃艮第/勃艮第-preview.html')).href});
  for(let i=0;i<100;i++){if(await js(`document.readyState==='complete'&&document.querySelectorAll('.room').length===4`))break;await pause(50);}
  check(await js(`document.title.includes('勃艮第')`),'Theme name');
  check(await js(`document.querySelectorAll('[data-slot]').length===14`),'14 slots');
  check(await js(`(()=>{const a=[...document.querySelectorAll('[id]')].map(x=>x.id);return new Set(a).size===a.length})()`),'Unique IDs');
  check(await js("document.documentElement.dataset.edition==='3'"),'Third edition defaults');
  const saturations={};
  for(const edition of ['1','2','3']){
    await click('[data-edition-choice="'+edition+'"]');
    check(await js('document.documentElement.dataset.edition')===edition,'Edition selected');
    for(const mode of ['light','dark']){
      if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
      const ratios=await js(`[...document.querySelectorAll('.contrast-row>code')].map(x=>parseFloat(x.textContent))`);
      check(ratios.length===(edition==='3'?13:12)&&ratios.every(x=>x>=4.5),`v${edition} ${mode} contrast ${ratios}`);
      const values=await js(`['header-face','nav-face','primary','heading-face'].map(k=>{const rgb=getComputedStyle(document.documentElement).getPropertyValue('--'+k).trim().slice(1).match(/../g).map(v=>parseInt(v,16)/255);const hi=Math.max(...rgb),lo=Math.min(...rgb),light=(hi+lo)/2;return hi===lo?0:(hi-lo)/(1-Math.abs(2*light-1));})`);
      if(edition==='1')saturations[mode]=values;
      else check(values.every((v,i)=>v<saturations[mode][i]),mode+' structural and repeated colors are less saturated');
      check(await js("getComputedStyle(document.documentElement).getPropertyValue('--launch-face').trim()!==getComputedStyle(document.documentElement).getPropertyValue('--relaxed').trim()"),mode+' launch remains distinct');
      for(const page of ['rooms','planner','settings']){
        await click(`[data-page="${page}"]`);await pause(300);
        check(await overflow(),`v${edition} ${mode} ${page} overflow`);await shot(`v${edition}-${mode}-${page}`);
      }
    }
  }
  await click('[data-edition-choice="1"]');await click('#mode');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--primary').trim()==='#800020'&&getComputedStyle(document.documentElement).getPropertyValue('--header-face').trim()==='#800020'"),'First edition intact');
  await click('[data-edition-choice="2"]');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--primary').trim()==='#624550'&&getComputedStyle(document.documentElement).getPropertyValue('--launch-face').trim()==='#800020'"),'Daily details muted and original wine kept on launch');
  await click('[data-edition-choice="3"]');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--header-face').trim()==='#713F3D'&&getComputedStyle(document.documentElement).getPropertyValue('--primary').trim()==='#64433B'"),'Third edition has warm oak structure, not grey purple');
  check(await js("getComputedStyle(document.querySelector('.corner')).borderTopColor==='rgb(139, 52, 65)'&&getComputedStyle(document.querySelector('.plan-name svg')).color==='rgb(139, 52, 65)'"),'Favorite marker and plan star use independent ruby');
  check(await js("getComputedStyle(document.documentElement).getPropertyValue('--favorite').trim()!==getComputedStyle(document.documentElement).getPropertyValue('--icon').trim()"),'Favorites do not borrow brown settings icon color');
  await click('[data-page="rooms"]');
  await click('[data-slot="1"]');check(await js(`document.querySelector('[data-slot="1"]').getAttribute('aria-pressed')==='true'`),'Select slot');
  await click('.room .more');check(await js(`document.querySelector('.room').classList.contains('revealed')`),'Reveal rail');
  await shot('light-actions');
  await click('.room [data-action="tag"]');
  check(await js(`!document.querySelector('#modal').hidden&&document.querySelector('#dialog-title').textContent==='教室标签'`),'Tags dialog');
  await click('#add-tag');await js(`document.querySelector('#tag-name').value='测试'`);await click('#tag-confirm');
  check(await js(`document.querySelector('#dialog-exit').dataset.dirty==='true'`),'Edited tags need confirmation');
  await click('#dialog-exit');await pause(550);
  check(await js(`document.querySelector('#modal').hidden&&rooms[0].tags.includes('测试')`),'Tag confirmation commits');
  await click('.room .more');await click('.room [data-action="note"]');
  await js(`document.querySelector('#note-input').value='靠窗自习';document.querySelector('#note-input').dispatchEvent(new Event('input'))`);
  await click('#dialog-exit');await pause(550);
  check(await js(`rooms[0].note==='靠窗自习'`),'Note edit');
  await click('[data-page="planner"]');
  await click('#expected');await click('[data-expected="3-305"]');await click('#dialog-exit');await pause(550);
  check(await js(`document.querySelector('#expected-count').textContent==='1'`),'Expected-room selection');
  await click('#relaxed');check(await js(`document.querySelector('#notice').textContent==='已关闭宽松模式'&&document.querySelector('#notice').dataset.kind==='status'`),'Loose mode notice');
  await click('#add-range');check(await js(`document.querySelector('#notice').textContent==='!　最多添加三个时段'&&document.querySelector('#notice').dataset.kind==='error'`),'Capacity notice');
  await click('[data-range="0"]');await click('#delete-period');
  check(await js('state.ranges.length===2'),'Delete range');
  await click('#add-range');check(await js('state.ranges.length===3'),'Add legal range');
  await click('#generate');check(await js(`document.querySelectorAll('.plan-room').length===2`),'Mock plan');
  await click('#other-plans');check(await js(`!document.querySelector('#plan-options').hidden&&document.querySelector('#other-plans').getAttribute('aria-expanded')==='true'`),'Other plans');
  await click('[data-page="settings"]');await click('#style-toggle');await click('[data-style="1"]');
  check(await js(`state.proportional&&document.querySelector('#style-value').textContent==='等比例'`),'Proportional strips');
  await click('#numbers');check(await js(`state.numbers&&document.querySelectorAll('.numbers').length>0`),'Period numbering');
  await click('#campus-toggle');await click('[data-campus="沙河"]');check(await js(`document.querySelector('#campus-value').textContent==='沙河'`),'Default campus mock');
  await click('#wave-preview');await pause(100);
  check(await js(`document.querySelector('.room .strip').classList.contains('wave')&&state.page==='rooms'`),'Replay wave');
  check(await js(`getComputedStyle(document.querySelector('.strip.wave rect')).animationName==='wave'`),'Wave animates');
  await click('[data-page="planner"]');await click('#expected');await click('[data-expected="3-308"]');
  const stateSnapshot=`JSON.stringify({page:state.page,slots:[...state.slots],ranges:state.ranges,expected:[...state.expected],numbers:state.numbers,proportional:state.proportional,dirty:document.querySelector('#dialog-exit').dataset.dirty,checked:[...document.querySelectorAll('[data-expected]')].map(x=>x.getAttribute('aria-pressed'))})`;
  const before=await js(stateSnapshot);
  for(const edition of ['1','2','3']){
    await click('[data-edition-choice="'+edition+'"]');check(await js(stateSnapshot)===before,'Edition preserves workspace and pending changes');
  }
  await click('#dialog-exit');await pause(550);check(await js('state.expected.size===2'),'Pending dialog can still commit');
  for(const width of [320,390]){
    await viewport(width);
    for(const page of ['rooms','planner','settings']){await click(`[data-page="${page}"]`);await pause(300);check(await overflow(),`${width} ${page} overflow`);}
    await shot(`mobile-${width}`);
    await click('[data-page="planner"]');await click('#expected');
    check(await js(`document.querySelector('.modal-card').scrollWidth<=document.querySelector('.modal-card').clientWidth+1`),`${width} modal overflow`);
    await click('#dialog-exit');
  }
  await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  check(await js(`parseFloat(getComputedStyle(document.querySelector('.screen')).animationDuration)<.01`),'Reduced motion');
  await click('#reset');check(await js(`state.slots.size===3&&state.ranges.length===3&&!state.expected.size&&state.page==='rooms'&&!state.numbers&&!state.proportional`),'Reset state');
  check(!requests.some(u=>/^https?:/.test(u)),'No external requests');
  check(!errors.length,'No runtime errors '+errors.join('\n'));
  for(const name of fs.readdirSync('docs/colors').filter(n=>n.endsWith('.md'))){
    const md=fs.readFileSync(path.join('docs/colors',name),'utf8');
    for(const m of md.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){if(/^(https?:|mailto:|#)/.test(m[1]))continue;check(fs.existsSync(path.resolve('docs/colors',decodeURIComponent(m[1].split('#')[0]))),name+' broken link '+m[1]);}
  }
  console.log(JSON.stringify({passed:assertions,colorModes:2,editions:3,widths:[1180,390,320],runtimeErrors:errors.length,externalRequests:requests.filter(u=>/^https?:/.test(u)).length}));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>{socket?.close();proc.kill();});
