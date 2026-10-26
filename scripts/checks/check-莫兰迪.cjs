const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'youroom-morandi-'));
const proc = spawn(auditPaths.browser(), [
  '--headless=new', '--remote-debugging-port=9364', '--no-first-run', '--no-default-browser-check',
  '--disable-extensions', '--disable-background-networking', '--disable-sync', '--user-data-dir='+profile, 'about:blank'
], {windowsHide:true, stdio:'ignore'});
const pause = ms => new Promise(r => setTimeout(r,ms));
let socket, seq=0, assertions=0;
const pending=new Map(), errors=[], requests=[];
function check(ok,message){assertions++;if(!ok)throw new Error(message);}
async function main(){
  let info;
  for(let i=0;i<100;i++){try{const targets=await fetch('http://127.0.0.1:9364/json/list').then(r=>r.json());info=targets.find(t=>t.type==='page');if(info)break;}catch{}await pause(100);}
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
  const shot=async name=>{await pause(300);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(auditPaths.output,`莫兰迪-${name}.png`),Buffer.from(r.data,'base64'));};
  const viewport=width=>send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
  const overflow=()=>js(`(()=>{const p=document.querySelector('.phone'),v=document.querySelector('#viewport');return document.documentElement.scrollWidth<=innerWidth&&p.scrollWidth<=p.clientWidth+1&&v.scrollWidth<=v.clientWidth+1})()`);
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await viewport(1180);
  await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/莫兰迪/莫兰迪-preview.html')).href});
  for(let i=0;i<100;i++){if(await js(`document.readyState==='complete'&&document.querySelectorAll('.room').length===4`))break;await pause(50);}
  check(await js(`document.title.includes('莫兰迪')`),'Theme name');
  check(await js(`document.querySelectorAll('[data-slot]').length===14`),'14 slots');
  check(await js(`(()=>{const a=[...document.querySelectorAll('[id]')].map(x=>x.id);return new Set(a).size===a.length})()`),'Unique IDs');
  check(await js("document.documentElement.dataset.edition==='4'&&document.querySelectorAll('[data-edition-choice]').length===4"),'Fourth edition defaults and all four choices exist');
  const original={},art=await js("document.querySelector('.theme-frame').innerHTML");
  for(const edition of ['1','2','3','4']){
    await click('[data-edition-choice="'+edition+'"]');
    check(await js('document.documentElement.dataset.edition')===edition,'Edition selected');
    for(const mode of ['light','dark']){
      if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
      const ratios=await js("[...document.querySelectorAll('.contrast-row>code')].map(x=>parseFloat(x.textContent))");
      check(ratios.length===20&&ratios.slice(0,18).every(x=>x>=4.5)&&ratios.slice(18).every(x=>x>=3),'v'+edition+' '+mode+' text and switch contrast '+ratios);
      const colors=await js("(()=>{const s=getComputedStyle(document.documentElement);return Object.fromEntries([...s].filter(k=>k.startsWith('--')).map(k=>[k,s.getPropertyValue(k).trim()]))})()");
      if(edition==='1')original[mode]=colors;
      else {
        const allowed=edition==='2'?['--header-face','--nav-face','--nav-ink']:edition==='4'?['--header-face','--nav-face','--nav-ink','--heading-face','--wheel','--period-idle','--list-rule']:['--header-face','--nav-face','--nav-ink','--heading-face'];
        check(Object.keys(colors).filter(k=>!allowed.includes(k)).every(k=>colors[k]===original[mode][k]),mode+' only intended structural roles change');
        check(colors['--accent']===original[mode]['--accent']&&colors['--favorite']===original[mode]['--favorite'],mode+' both roses unchanged');
        check(colors['--header-face']!==(original[mode]['--header-face'])&&colors['--nav-face']!==(original[mode]['--nav-face']),mode+' both target planes differ');
        if(edition==='3')check(colors['--heading-face']!==original[mode]['--heading-face'],mode+' third edition also bridges the planning heading');
      }
      check(await js("document.querySelector('.theme-frame').innerHTML")===art,'Artwork unchanged');
      check(await js("getComputedStyle(document.documentElement).getPropertyValue('--launch-face').trim()!==getComputedStyle(document.documentElement).getPropertyValue('--relaxed').trim()"),mode+' launch differs from rose loose mode');
      check(await js("getComputedStyle(document.documentElement).getPropertyValue('--favorite').trim()!==getComputedStyle(document.documentElement).getPropertyValue('--icon').trim()"),mode+' favorites do not reuse settings icon ink');
      check(await js("document.querySelector('[data-color-text=header-face]').textContent===getComputedStyle(document.documentElement).getPropertyValue('--header-face').trim()"),mode+' palette shows current header');
      check(await js("document.querySelector('[data-color-text=nav-face]').textContent===getComputedStyle(document.documentElement).getPropertyValue('--nav-face').trim()"),mode+' palette shows current navigation');
      check(await js("document.querySelector('[data-color-text=heading-face]').textContent===getComputedStyle(document.documentElement).getPropertyValue('--heading-face').trim()"),mode+' palette shows current planning heading');
      for(const page of ['rooms','planner','settings']){
        await click(`[data-page="${page}"]`);await pause(300);
        check(await overflow(),'v'+edition+' '+mode+' '+page+' overflow');await shot('v'+edition+'-'+mode+'-'+page);
      }
    }
  }
  await click('#mode');
  check(await js("(()=>{const v=k=>getComputedStyle(document.documentElement).getPropertyValue('--'+k).trim();return v('header-face')==='#D1D5D6'&&v('nav-face')==='#535D64'&&v('heading-face')==='#DCE0E0'&&luminance(v('header-face'))>luminance(v('nav-face'))&&contrast(v('nav-active'),v('nav-face'))>=3})()"),'Neutral pearl top, silver heading and graphite bottom, original rose stays legible');
  check(await js("getComputedStyle(document.querySelector('.corner')).borderTopColor==='rgb(136, 95, 107)'&&getComputedStyle(document.querySelector('.plan-name svg')).color==='rgb(136, 95, 107)'"),'Favorite marker and plan star retain original dusty rose');
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
  check(await js("document.querySelector('.strip.wave').dataset.waveState==='waiting'"),'Wave waits 250ms');
  const stableStripSize="(()=>{const r=document.querySelector('.room .strip').getBoundingClientRect();return JSON.stringify({width:r.width,height:r.height,y:r.y})})()";
  const stripBounds=await js(stableStripSize);
  await js("window.__waveJob=waveJobs.get(document.querySelector('.room .strip'))");
  await click('#wave-preview');
  check(await js("waveJobs.get(document.querySelector('.room .strip'))===window.__waveJob"),'Repeated wave requests do not restart or overlap');
  await pause(250);
  check(await js("document.querySelector('.strip.wave')?.dataset.waveState==='playing'"),'Wave advances to playing');
  check(await js("[...document.querySelectorAll('.strip.wave path')].some(p=>[...p.getAttribute('d').matchAll(/L[-\\d.]+ ([-\\d.]+)/g)].slice(0,17).some(m=>+m[1]<1.9))"),'Continuous upper edge rises');
  check(await js("[...document.querySelectorAll('.strip.wave path')].every(p=>p.getAttribute('d').endsWith(' 8Z'))"),'Bottom remains fixed');
  check(await js(stableStripSize)===stripBounds,'Wave keeps layout fixed');
  await shot('light-wave');
  await pause(550);
  check(await js("!document.querySelector('.strip.wave')"),'Single pass returns to flat');

  await click('[data-page="planner"]');await click('#expected');await click('[data-expected="3-308"]');
  const stateSnapshot=`JSON.stringify({page:state.page,slots:[...state.slots],ranges:state.ranges,expected:[...state.expected],numbers:state.numbers,proportional:state.proportional,dirty:document.querySelector('#dialog-exit').dataset.dirty,checked:[...document.querySelectorAll('[data-expected]')].map(x=>x.getAttribute('aria-pressed'))})`;
  const before=await js(stateSnapshot);
  await click('[data-edition-choice="1"]');check(await js(stateSnapshot)===before,'First edition preserves workspace and pending dialog');
  await click('[data-edition-choice="2"]');check(await js(stateSnapshot)===before,'Second edition preserves workspace and pending dialog');
  await click('[data-edition-choice="3"]');check(await js(stateSnapshot)===before,'Third edition preserves workspace and pending dialog');
  await click('#mode');check(await js(stateSnapshot)===before,'Dark mode preserves workspace and pending dialog');
  await click('#mode');check(await js(stateSnapshot)===before,'Returning to light preserves pending changes');
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
  await click('#wave-preview');await pause(30);
  check(await js("!document.querySelector('.strip.wave')"),'Reduced motion skips wave playback');
  await click('#reset');check(await js(`state.slots.size===3&&state.ranges.length===3&&!state.expected.size&&state.page==='rooms'&&!state.numbers&&!state.proportional`),'Reset state');

  // Local color laboratory: defaults are untouched, trials are scoped and reversible.
  await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  await viewport(1180);await click('[data-edition-choice="3"]');await click('[data-page="planner"]');await pause(300);
  check(await js("document.querySelectorAll('.lab-row').length===5&&document.querySelectorAll('.lab-hex').length===5"),'Five independently adjustable candidates');
  check(await js("labCandidates.every(c=>!labCurrent()[c.id].enabled)&&!document.querySelector('#lab-fields').disabled"),'No trial enabled by default');
  const appSnapshot=()=>js("JSON.stringify({page:state.page,slots:[...state.slots],ranges:state.ranges,expected:[...state.expected],numbers:state.numbers,proportional:state.proportional,goal:state.goal,plan:state.plan})");
  const appBefore=await appSnapshot();
  const paintSignature=()=>js("JSON.stringify({divider:getComputedStyle(document.querySelector('.room')).borderBottomColor,idle:getComputedStyle(document.querySelector('.room .strip .idle')).fill,active:getComputedStyle(document.querySelector('.room .strip .available')).fill,wheel:getComputedStyle(document.querySelector('.wheel-band')).backgroundColor,frame:getComputedStyle(document.querySelector('.theme-frame')).backgroundColor})");
  const originalPaint=await paintSignature();
  const roseSignature=()=>js("JSON.stringify(['--accent','--on-accent','--relaxed','--on-relaxed','--favorite','--nav-active','--on-nav-active','--rail-selected','--selection-fill','--on-selection'].map(k=>getComputedStyle(document.documentElement).getPropertyValue(k).trim()))");
  const originalRoses=await roseSignature();
  const inputHex=(id,value)=>js(`(()=>{const e=document.querySelector('#lab-hex-'+${JSON.stringify(id)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}))})()`);
  const assertReadout=()=>js("document.querySelector('#lab-status').textContent");
  await click('[data-lab-preset="subtle"]');await pause(350);
  check(await js("Object.keys(labCurrent()).filter(k=>labCurrent()[k].enabled).sort().join(',')==='divider,idle'"),'Suggested preset enables only divider and idle bars');
  check(await js("getComputedStyle(document.querySelector('.room')).borderBottomColor==='rgb(205, 197, 178)'&&getComputedStyle(document.querySelector('.plan-room')).borderBottomColor==='rgb(205, 197, 178)'"),'Suggested divider paints search and planner separators');
  check(await js("getComputedStyle(document.querySelector('.room .strip .idle')).fill==='rgb(205, 197, 178)'"),'Suggested idle bar color actually paints');
  check(await js("getComputedStyle(document.querySelector('.room .strip .available')).fill==='rgb(70, 89, 99)'"),'Active bars stay unchanged with idle preset');
  check(await roseSignature()===originalRoses,'Preset protects every rose role');
  check(await appSnapshot()===appBefore,'Presets preserve app state');
  await shot('lab-suggested-planner');
  await inputHex('divider','#BFB4A0');await pause(80);
  check(await js("labCurrent().divider.hex==='#BFB4A0'&&document.querySelector('#lab-enable-divider').checked"),'HEX changes apply and enable candidate');
  await inputHex('divider','#GGGGGG');
  check(await js("document.querySelector('#lab-hex-divider').getAttribute('aria-invalid')==='true'&&labCurrent().divider.hex==='#BFB4A0'"),'Invalid HEX preserves last valid color');
  check((await assertReadout()).includes('HEX'),'Invalid color feedback is explicit');
  await inputHex('divider','b7ab95');await js("document.querySelector('#lab-hex-divider').dispatchEvent(new Event('blur'))");await pause(80);
  check(await js("labCurrent().divider.hex==='#B7AB95'&&document.querySelector('#lab-hex-divider').value==='#B7AB95'"),'HEX accepts lowercase and missing hash');
  await js("(()=>{const p=document.querySelector('#lab-color-idle');p.value='#c2b6a1';p.dispatchEvent(new Event('input',{bubbles:true}))})()");await pause(300);
  check(await js("labCurrent().idle.hex==='#C2B6A1'&&document.querySelector('#lab-hex-idle').value==='#C2B6A1'"),'Native color picker synchronizes HEX');
  const tunedPaint=await paintSignature(),lightTrial=await js("JSON.stringify(colorLab.light)");
  // Keyboard hold is A/B, not a destructive reset.
  await js("document.querySelector('#lab-hold').focus();document.querySelector('#lab-hold').dispatchEvent(new KeyboardEvent('keydown',{key:' ',bubbles:true}))");
  await pause(80);check(await paintSignature()===originalPaint,'Holding Space shows exact original colors');
  check(await js("colorLab.holding&&document.querySelector('#lab-hold').getAttribute('aria-pressed')==='true'"),'Compare hold exposes pressed state');
  await js("document.querySelector('#lab-hold').dispatchEvent(new KeyboardEvent('keyup',{key:' ',bubbles:true}))");await pause(350);
  check(await paintSignature()===tunedPaint,'Releasing Space restores exact trial');
  await js("document.querySelector('#lab-hold').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));window.dispatchEvent(new Event('blur'))");await pause(350);
  check(await js("!colorLab.holding"),'Losing focus cannot leave comparison stuck');
  // Trusted pointer input, including capture and release.
  await js("document.querySelector('#lab-hold').scrollIntoView({block:'center'})");await pause(100);
  const holdPoint=await js("(()=>{const r=document.querySelector('#lab-hold').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()");
  await send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,buttons:1,...holdPoint});await pause(80);
  check(await js("colorLab.holding"),'Pointer down enters comparison');
  await send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,buttons:0,...holdPoint});await pause(350);
  check(await js("!colorLab.holding"),'Pointer release restores trial');
  await js("window.scrollTo(0,0)");
  await click('[data-edition-choice="1"]');await pause(350);
  check(await js("document.querySelector('#lab-fields').disabled&&!document.querySelector('#lab-return').hidden"),'Original editions disable the lab with a return shortcut');
  check(await paintSignature()===originalPaint,'First edition remains visually untouched for local candidates');
  await click('#lab-return');await pause(350);
  check(await js("document.documentElement.dataset.edition==='3'&&JSON.stringify(colorLab.light)")===lightTrial,'Returning to third keeps edits');
  check(await paintSignature()===tunedPaint,'Returning to third reapplies the exact trial');
  await click('#mode');await pause(350);
  check(await js("labCandidates.every(c=>!labCurrent()[c.id].enabled)"),'Dark trial starts independently');
  await inputHex('wheel','#46453D');await pause(350);
  check(await js("getComputedStyle(document.querySelector('.wheel-band')).backgroundColor==='rgb(70, 69, 61)'"),'Wheel is independently editable in dark mode');
  const darkTrial=await js("JSON.stringify(colorLab.dark)");
  await click('#mode');await pause(350);
  check(await js("JSON.stringify(colorLab.light)")===lightTrial,'Dark edits do not overwrite light trial');
  check(await paintSignature()===tunedPaint,'Light trial resumes');
  await inputHex('active','#C2B6A1');await pause(350);
  check((await assertReadout()).includes('辨识度偏低'),'Matching active and idle bars warns about lost distinction');
  check(await js("document.querySelector('#lab-status').dataset.level==='warning'"),'Low-contrast warning is not color-only');
  await inputHex('active','#71695B');await inputHex('wheel','#E4DFD2');await inputHex('frame','#D5CBB7');await pause(350);
  check(await js("getComputedStyle(document.querySelector('.room .strip .available')).fill==='rgb(113, 105, 91)'"),'Active bar paint can change without recoloring time text');
  check(await js("getComputedStyle(document.querySelector('.wheel-row.middle')).color==='rgb(70, 89, 99)'"),'Time numerals keep their baseline ink');
  check(await js("getComputedStyle(document.querySelector('.theme-frame')).backgroundColor==='rgb(213, 203, 183)'"),'Only the artwork frame gets the chosen warm tone');
  check(await roseSignature()===originalRoses,'All five candidates still protect both rose colors');
  await click('[data-page="settings"]');await shot('lab-all-settings');
  await click('#reset');await pause(350);
  check(await js("labCurrent().frame.enabled&&labCurrent().frame.hex==='#D5CBB7'"),'Demo reset does not erase trial colors');
  await click('#lab-reset');await pause(350);
  check(await paintSignature()===originalPaint,'Trial reset restores exact original paint');
  check(await js("['light','dark'].every(m=>labCandidates.every(c=>!colorLab[m][c.id].enabled&&colorLab[m][c.id].hex===c[m]))"),'Trial reset restores defaults for both modes');
  for(const width of [320,390]){
    await viewport(width);await js("document.querySelector('#color-lab').scrollIntoView({block:'start'})");await pause(200);
    check(await js("document.documentElement.scrollWidth<=innerWidth&&document.querySelector('#color-lab').scrollWidth<=document.querySelector('#color-lab').clientWidth+1"),width+' color lab does not overflow');
    await shot('lab-mobile-'+width);
  }
  await viewport(1180);await js("window.scrollTo(0,0)");await click('[data-lab-preset="subtle"]');
  await click('#wave-preview');await pause(370);
  check(await js("document.querySelector('.strip.wave')&&getComputedStyle(document.querySelector('.room .strip .idle')).fill==='rgb(205, 197, 178)'"),'Custom bar color survives continuous wave drawing');
  await pause(550);await click('#lab-reset');

  // Fourth edition is a saved palette, never dependent on third-edition scratch settings.
  await viewport(1180);await js("window.scrollTo(0,0)");await click('[data-edition-choice="4"]');await click('[data-page="planner"]');await pause(350);
  check(await js("document.querySelector('#lab-fields').disabled&&Object.keys(labCurrent()).filter(k=>labCurrent()[k].enabled).sort().join(',')==='divider,idle,wheel'"),'Fourth edition displays the three approved checked candidates as read-only');
  check(await js("getComputedStyle(document.querySelector('.room')).borderBottomColor==='rgb(205, 197, 178)'&&getComputedStyle(document.querySelector('.plan-room')).borderBottomColor==='rgb(205, 197, 178)'"),'Saved fourth edition has limestone list separators');
  check(await js("getComputedStyle(document.querySelector('.room .strip .idle')).fill==='rgb(205, 197, 178)'&&getComputedStyle(document.querySelector('.room .strip .available')).fill==='rgb(70, 89, 99)'"),'Saved fourth edition accents idle bars only');
  check(await js("getComputedStyle(document.querySelector('.wheel-band')).backgroundColor==='rgb(228, 223, 210)'&&getComputedStyle(document.querySelector('.theme-frame')).backgroundColor==='rgb(205, 214, 214)'"),'Saved wheel accent and unchecked original frame match screenshot');
  check(await roseSignature()===originalRoses,'Saved fourth edition retains both original rose colors');
  const fixedPaint=await paintSignature();await shot('v4-final-light-planner');
  await click('[data-edition-choice="3"]');await inputHex('idle','#ABCDEF');await inputHex('wheel','#123456');
  await click('[data-edition-choice="4"]');await pause(350);
  check(await paintSignature()===fixedPaint,'Temporary trial changes never modify saved fourth edition');
  await click('#mode');await pause(350);
  check(await js("getComputedStyle(document.querySelector('.room .strip .idle')).fill==='rgb(118, 111, 98)'&&getComputedStyle(document.querySelector('.wheel-band')).backgroundColor==='rgb(70, 69, 61)'"),'Saved night edition uses the dark companion colors');
  await shot('v4-final-dark-planner');await click('#mode');await click('#reset');

  check(!requests.some(u=>/^https?:/.test(u)),'No external requests');
  check(!errors.length,'No runtime errors '+errors.join('\n'));
  for(const name of fs.readdirSync('docs/colors').filter(n=>n.endsWith('.md'))){
    const md=fs.readFileSync(path.join('docs/colors',name),'utf8');
    for(const m of md.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){if(/^(https?:|mailto:|#)/.test(m[1]))continue;check(fs.existsSync(path.resolve('docs/colors',decodeURIComponent(m[1].split('#')[0]))),name+' broken link '+m[1]);}
  }
  console.log(JSON.stringify({passed:assertions,colorModes:2,editions:4,widths:[1180,390,320],runtimeErrors:errors.length,externalRequests:requests.filter(u=>/^https?:/.test(u)).length}));
}
main().catch(e=>{console.error(e);console.error(errors);process.exitCode=1;}).finally(()=>{socket?.close();proc.kill();});
