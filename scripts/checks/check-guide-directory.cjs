const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const browserPath=auditPaths.browser();
const profile=path.resolve('artifacts/qa/guide-directory-browser-profile');
const output=path.resolve('artifacts/qa/guide-directory-qa');
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser,socket,serial=0;
const inflight=new Map(),exceptions=[];
async function send(method,params={}) {
  const id=++serial;
  const promise=new Promise((resolve,reject)=>inflight.set(id,{resolve,reject}));
  socket.send(JSON.stringify({id,method,params}));return promise;
}
async function evaluate(expression) {
  const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
  if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));
  return r.result.value;
}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
const click=selector=>run(s=>document.querySelector(s).click(),selector);
const phoneSelector=design=>'.phone[data-design="'+design+'"]';
async function screenshot(name) {
  await pause(700);
  const layout=await send('Page.getLayoutMetrics'),size=layout.cssContentSize;
  const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,
    clip:{x:0,y:0,width:size.width,height:size.height,scale:1}});
  fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(r.data,'base64'));
}
async function main() {
  fs.mkdirSync(output,{recursive:true});
  browser=spawn(browserPath,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',
    '--remote-debugging-port=9447','--remote-debugging-address=127.0.0.1',
    '--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
  let target;
  for(let i=0;i<80;i++) {
    try {const targets=await(await fetch('http://127.0.0.1:9447/json/list')).json();
      target=targets.find(t=>t.type==='page');if(target)break;}catch{}
    await pause(150);
  }
  assert(target,'The dedicated headless browser did not start');
  socket=new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  socket.onmessage=event=>{
    const message=JSON.parse(event.data);
    if(message.method==='Runtime.exceptionThrown')exceptions.push(message.params);
    if(message.id) {
      const pending=inflight.get(message.id);inflight.delete(message.id);
      if(message.error)pending.reject(Error(JSON.stringify(message.error)));else pending.resolve(message.result);
    }
  };
  await send('Page.enable');await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1180,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/usage-guide/usage-guide-directory-preview.html')).href});
  for(let i=0;i<80;i++) {
    if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".directory").length===3'))break;
    await pause(100);
  }
  assert.equal(await evaluate('document.querySelectorAll(".directory").length'),3);
  const checks=await run(()=>{
    const results=[];
    for(const t of THEMES)for(const m of ['light','dark']) {
      const p=palette(t,m);
      for(const [bg,fg] of [['paper','ink'],['paper','muted'],['control','ink'],['high','ink'],['low','ink'],
        ['strong','on-strong'],['accent','on-accent'],['plane-1','on-plane-1']]) {
        const ratio=contrast(p[bg],p[fg]);if(ratio<4.5)results.push({theme:t.name,mode:m,bg,fg,ratio});
      }
    }
    return {themes:THEMES.length,steps:STEPS.length,chapters:CHAPTERS.length,failures:results};
  });
  assert.equal(checks.themes,12);assert.equal(checks.steps,43);assert.equal(checks.chapters,4);
  assert.deepEqual(checks.failures,[]);
  await screenshot('desktop-matisse-three-designs');
  const layoutProblems=[];
  for(const width of [1440,768,390,320]) {
    await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<500});
    for(const id of await evaluate('THEMES.map(t=>t.id)'))for(const appearance of ['light','dark']) {
      await run((id,appearance)=>{theme=THEMES.find(t=>t.id===id);mode=appearance;applyTheme();},id,appearance);
      const issues=await run(()=>{
        const issues=[];
        if(document.documentElement.scrollWidth>innerWidth+1)issues.push('page overflow');
        for(const phone of document.querySelectorAll('.phone')) {
          const menu=phone.querySelector('.directory'),mr=menu.getBoundingClientRect();
          for(const b of menu.querySelectorAll('.chapter-button,.start-guide,.footer-meta,.close-menu')) {
            const r=b.getBoundingClientRect();
            if(r.left<mr.left-.5||r.right>mr.right+.5||r.top<mr.top-.5||r.bottom>mr.bottom+.5)
              issues.push(phone.dataset.design+':'+b.className+' escaped menu');
            if(b.scrollWidth>b.clientWidth+1)issues.push(phone.dataset.design+':'+b.className+' text overflow');
          }
        }
        return issues;
      });
      if(issues.length)layoutProblems.push({width,id,appearance,issues});
    }
  }
  assert.deepEqual(layoutProblems,[]);
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1180,deviceScaleFactor:1,mobile:false});
  await evaluate('themeSelect.value="MORANDI";themeSelect.onchange();document.querySelector("button[data-mode=dark]").click();');
  await screenshot('desktop-morandi-dark');
  await evaluate('themeSelect.value="DUNHUANG";themeSelect.onchange();document.querySelector("button[data-mode=light]").click();progressSelect.value="17";progressSelect.onchange();');
  await screenshot('desktop-dunhuang-resume');
  for(const design of ['a','b','c']) {
    const selector=phoneSelector(design);
    await click(selector+' [data-chapter="2"]');
    assert.equal(await run(s=>document.querySelectorAll(s+' .step-option').length,selector),16);
    await click(selector+' [data-step="31"]');
    await click(selector+' [data-action="enter"]');
    assert.equal(await run(s=>states.get(document.querySelector(s)).current,selector),31);
    await click(selector+' [data-action="reopen"]');
    await click(selector+' [data-action="skip"]');
    assert.equal(await run(s=>!!document.querySelector(s+' .menu-closed'),selector),true);
    await click(selector+' [data-action="reopen"]');
  }
  for(const design of ['a','b','c'])for(const chapter of [0,1,2,3]) {
    const selector=phoneSelector(design);
    await click(selector+' [data-chapter="'+chapter+'"]');
    assert.equal(await run(s=>document.querySelectorAll(s+' .step-option').length,selector),[9,6,16,12][chapter]);
    await click(selector+' [data-action="back"]');
  }
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await evaluate('themeSelect.value="MATISSE";themeSelect.onchange();document.querySelector("button[data-mode=light]").click();progressSelect.value="17";progressSelect.onchange();document.querySelector("button[data-view=b]").click();');
  await screenshot('mobile-color-index');
  await click('.phone[data-design="b"] [data-chapter="3"]');
  await screenshot('mobile-chapter-detail');
  assert.equal(await evaluate('document.querySelector(".design[data-design=a]").hidden'),true);
  assert.equal(await evaluate('document.querySelector(".design[data-design=c]").hidden'),true);
  assert.deepEqual(exceptions,[]);
  console.log(JSON.stringify({ok:true,contrastPairs:12*2*8,responsiveThemeStates:4*12*2,chapterEntries:12,
    designs:3,themes:12,steps:43,screenshots:fs.readdirSync(output)},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
  if(socket?.readyState===WebSocket.OPEN) {
    try{await Promise.race([send('Browser.close'),pause(2000)]);}catch{}
    socket.close();
  }
  if(browser&&!browser.killed)browser.kill();
});
