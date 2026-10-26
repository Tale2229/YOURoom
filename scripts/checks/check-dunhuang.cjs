const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const out = auditPaths.output;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'youroom-dunhuang-'));
const proc = spawn(auditPaths.browser(), [
  '--headless=new', '--remote-debugging-port=9357', '--no-first-run',
  '--no-default-browser-check', '--disable-extensions', '--disable-background-networking',
  '--disable-sync', '--user-data-dir=' + profile, 'about:blank'
], { windowsHide: true, stdio: 'ignore' });
const pause = ms => new Promise(r => setTimeout(r, ms));
let socket, browserSocket;
let seq = 0;
const pending = new Map();
const errors = [];
function check(condition, message) { if (!condition) throw new Error(message); }
async function connect(url) {
  const ws = new WebSocket(url);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  return ws;
}
async function main() {
  const colorsDir=path.resolve('docs/colors');
  const sources=fs.readdirSync(path.join(colorsDir,'images')).filter(n=>/^微信图片_.*\.png$/.test(n));
  check(sources.length===13,'13 original images in images directory');
  check(!fs.readdirSync(colorsDir).some(n=>/^微信图片_.*\.png$/.test(n)),'No loose original images');
  let checkedLinks=0;
  for(const name of fs.readdirSync(colorsDir).filter(n=>n.endsWith('.md'))){
    const contents=fs.readFileSync(path.join(colorsDir,name),'utf8');
    for(const match of contents.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){
      const target=match[1];if(/^(https?:|mailto:|#)/.test(target))continue;
      check(fs.existsSync(path.resolve(colorsDir,decodeURIComponent(target.split('#')[0]))),name+': broken link '+target);checkedLinks++;
    }
  }
  const catalog=fs.readFileSync(path.join(colorsDir,'配色方案图鉴.md'),'utf8');
  check(!/第[一二三四]版|### .+版/.test(catalog),'Catalog has no theme iteration sections');
  check([...catalog.matchAll(/\]\(images\/微信图片_[^)]+\.png\)/g)].length===13,'All catalog image references updated');
  let info;
  for (let i = 0; i < 100; i++) {
    try { info = await fetch('http://127.0.0.1:9357/json/version').then(r => r.json()); break; }
    catch { await pause(100); }
  }
  check(info, 'Browser did not start');
  browserSocket = await connect(info.webSocketDebuggerUrl);
  const targets = await fetch('http://127.0.0.1:9357/json/list').then(r => r.json());
  socket = await connect(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
  socket.onmessage = ({ data }) => {
    const m = JSON.parse(data);
    if (m.id) { const p = pending.get(m.id); if (p) { pending.delete(m.id); m.error ? p.reject(m.error) : p.resolve(m.result); } }
    if (m.method === 'Runtime.exceptionThrown') errors.push(m.params.exceptionDetails.text + ': ' + (m.params.exceptionDetails.exception?.description || ''));
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++seq; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params }));
  });
  const js = async expression => {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    check(!result.exceptionDetails, JSON.stringify(result.exceptionDetails));
    return result.result.value;
  };
  const click = selector => js(`document.querySelector(${JSON.stringify(selector)}).click()`);
  const waitFor = async expression => {
    for(let i=0;i<30;i++){if(await js(expression))return;await pause(50);}
    throw new Error('Timed out waiting for '+expression);
  };
  const screenshot = async name => {
    const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    const edition=await js('document.documentElement.dataset.edition');
    fs.writeFileSync(path.join(out, name.replace('dunhuang-','dunhuang-v'+edition+'-') + '.png'), Buffer.from(shot.data, 'base64'));
  };
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1180, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: pathToFileURL(path.resolve('docs/colors/敦煌/dunhuang-preview.html')).href });
  for(let i=0;i<50;i++){if(await js('document.readyState === "complete" && !!document.querySelector("#slot-status")?.textContent')) break;await pause(100);}
  check(await js('document.querySelectorAll("[data-slot]").length===14'), '14 slots');
  check(await js('document.querySelectorAll(".room").length===4'), '4 classrooms');
  check(await js('(()=>{const ids=Array.from(document.querySelectorAll("[id]")).map(e=>e.id);return new Set(ids).size===ids.length})()'), 'Unique element IDs');
  check(await js('document.querySelectorAll(".slot[aria-pressed=true]").length===3'), 'Initial selection');
  check(await js('document.documentElement.scrollWidth<=innerWidth'), 'Desktop overflow');
  check(await js('document.documentElement.dataset.edition==="4"'), 'Edition 4 is the default');
  for(const edition of ['1','2','3','4']){
    await click('[data-edition-choice="'+edition+'"]');
    for(const mode of ['light','dark']){
      if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
      const ratios=await js('Array.from(document.querySelectorAll(".contrast-row>code")).map(e=>parseFloat(e.textContent))');
      check(ratios.length===11 && ratios.every(r=>r>=4.5), 'Edition '+edition+' '+mode+' contrast '+ratios);
    }
  }
  for(const mode of ['light','dark']){
    if(await js('document.documentElement.dataset.mode')!==mode)await click('#mode');
    const snapshots={};
    for(const edition of ['2','3','4']){
      await click('[data-edition-choice="'+edition+'"]');
      snapshots[edition]=await js('(()=>{const c=getComputedStyle(document.documentElement);return Object.fromEntries(Array.from(c).filter(k=>k.startsWith("--")).map(k=>[k,c.getPropertyValue(k).trim()]))})()');
    }
    const restored=['--nav-active','--on-nav-active','--relaxed-button-face','--relaxed-button-ink'];
    for(const name of Object.keys(snapshots['3'])){
      check(snapshots['4'][name]===snapshots[restored.includes(name)?'2':'3'][name],mode+' edition 4 source mismatch: '+name);
    }
  }
  await waitFor('getComputedStyle(document.querySelector(".app-head")).backgroundColor==="rgb(126, 62, 48)"');
  check(await js('getComputedStyle(document.querySelector(".app-head")).backgroundColor==="rgb(126, 62, 48)"'), 'Warm header in dark edition 4');
  await click('#mode'); await pause(350); await screenshot('dunhuang-rooms-light');
  check(await js('getComputedStyle(document.querySelector(".app-head")).backgroundColor==="rgb(166, 76, 59)"'), 'Ochre-red header');
  check(await js('getComputedStyle(document.querySelector(".nav")).backgroundColor==="rgb(107, 59, 50)"'), 'Brown navigation');
  check(await js('getComputedStyle(document.querySelector(".nav .active")).backgroundColor==="rgb(143, 191, 176)"'), 'Edition 2 selected navigation');
  check(await js('getComputedStyle(document.querySelector("#relaxed")).backgroundColor==="rgb(143, 191, 176)"'), 'Edition 2 relaxed-mode button');
  check(await js('getComputedStyle(document.querySelector("#generate")).backgroundColor==="rgb(60, 94, 82)"'), 'Muted deep jade launch button');
  check(await js('getComputedStyle(document.querySelector(".heading")).backgroundColor==="rgb(107, 59, 50)" && getComputedStyle(document.querySelector(".heading")).color==="rgb(255, 244, 232)"'), 'Brown heading and legible cream title');
  check(await js('getComputedStyle(document.querySelector(".heading"),"::before").backgroundColor==="rgb(183, 143, 104)" && getComputedStyle(document.querySelector(".other-plans")).color==="rgb(230, 197, 165)"'), 'Only warm colors in heading');
  check(await js('getComputedStyle(document.querySelector(".theme-arrow")).color==="rgb(139, 91, 61)"'), 'Warm rather than jade theme arrows');
  await click('.room .more');
  check(await js('document.querySelector(".room").classList.contains("revealed")'), 'Reveal room actions');
  await pause(350); await screenshot('dunhuang-actions-light');
  await click('.room [data-action=note]');
  await js('document.querySelector("#note-input").value="靠窗安静";document.querySelector("#note-input").dispatchEvent(new Event("input"))');
  check(await js('document.querySelector("#dialog-exit").dataset.dirty==="true"'), 'Dirty confirmation');
  await click('#dialog-exit');
  check(await js('!document.querySelector("#modal").hidden && !!document.querySelector(".underline")'), 'Underline instant and dialog open');
  await pause(345);
  check(await js('!document.querySelector("#modal").hidden'), 'Complete check holds');
  await pause(200);
  check(await js('document.querySelector("#modal").hidden'), 'Dialog closes after feedback');
  check(await js('document.querySelector(".room .note").textContent==="靠窗安静"'), 'Note committed');
  await click('#room-filter');await click('#add-tag');
  check(await js('!document.querySelector("#tag-editor").hidden'), 'Tag entry');
  await click('#tag-cancel');await click('#dialog-exit');
  await click('[data-page=planner]');await pause(300);await screenshot('dunhuang-planner-light');
  await click('#add-range');
  check(await js('document.querySelector("#notice").textContent==="最多添加三个时段"'), 'Period limit notice');
  await click('#relaxed');
  check(await js('document.querySelector("#relaxed").getAttribute("aria-pressed")==="false"'), 'Relaxed toggle');
  await click('#expected');await click('[data-expected="3-305"]');await click('#dialog-exit');await pause(550);
  check(await js('document.querySelector("#expected-count").textContent==="1" && !document.querySelector("#expected-count").hidden'), 'Expected room badge');
  await click('#other-plans');
  check(await js('document.querySelectorAll("[data-goal]").length===5 && !document.querySelector("#plan-options").hidden'), 'Five direction states');
  const planBefore=await js('document.querySelector("#target").innerHTML');
  for(const edition of ['1','2','3']){
    await click('[data-edition-choice="'+edition+'"]');await pause(300);await screenshot('dunhuang-planner-comparison');
    check(await js('document.querySelector("#planner").hidden===false && document.querySelector("#expected-count").textContent==="1" && document.querySelector("#other-plans").getAttribute("aria-expanded")==="true"'), 'Edition switching preserves controls and current page');
    check(await js('document.querySelector("#target").innerHTML')===planBefore, 'Edition switching preserves ranges');
  }
  await click('[data-edition-choice="4"]');
  await click('[data-page=settings]');await pause(300);await screenshot('dunhuang-settings-light');
  await click('#style-toggle');await click('[data-style="1"]');await click('#numbers');
  check(await js('document.querySelector("#numbers").getAttribute("aria-checked")==="true" && document.querySelector("#style-value").textContent==="等比例"'), 'Strip settings');
  await click('#mode');await pause(350);await screenshot('dunhuang-settings-dark');
  await click('[data-page=planner]');await pause(300);await screenshot('dunhuang-planner-dark');
  await click('#reset');await click('#mode');
  await send('Emulation.setDeviceMetricsOverride', { width:390, height:900, deviceScaleFactor:1, mobile:true });
  await pause(350);check(await js('document.documentElement.scrollWidth<=innerWidth'), '390px overflow');await screenshot('dunhuang-mobile-390');
  await click('[data-page=planner]');await pause(300);await screenshot('dunhuang-planner-390');
  await send('Emulation.setDeviceMetricsOverride', { width:320, height:800, deviceScaleFactor:1, mobile:true });
  await pause(300);check(await js('document.documentElement.scrollWidth<=innerWidth'), '320px overflow');
  check(await js('document.querySelector("#generate").getBoundingClientRect().right<=innerWidth'), '320px controls visible');
  check(await js('Array.from(document.querySelectorAll(".tools button")).every(e=>e.getBoundingClientRect().left>=0 && e.getBoundingClientRect().right<=innerWidth)'), '320px edition controls visible');
  await screenshot('dunhuang-planner-320');
  await send('Emulation.setEmulatedMedia', { features:[{name:'prefers-reduced-motion',value:'reduce'}] });
  check(await js('getComputedStyle(document.querySelector(".control")).transitionDuration==="0s"'), 'Reduced motion');
  check(errors.length===0, 'Browser errors: '+errors.join('\n'));
  check(await js('performance.getEntriesByType("resource").every(e=>!/^https?:/.test(e.name))'), 'No remote assets');
  console.log('PASS: four editions, 88 contrast pairs, exact edition-2/3 composition in both modes, warm-only heading, deep jade launch, warm theme triangles, state-preserving comparison, selection, room reveal, note confirmation/hold, tags, plan controls, 5 goals, strip styles, dark mode, 390px/320px, reduced motion; 12 screenshots.');
  console.log('PASS: 13 relocated images, '+checkedLinks+' local documentation links, catalog/individual design-record separation.');
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>{
  if(browserSocket?.readyState===1)browserSocket.send(JSON.stringify({id:99999,method:'Browser.close'}));
  socket?.close();browserSocket?.close();proc.unref();
  setTimeout(()=>process.exit(process.exitCode||0),500);
});
