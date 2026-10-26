const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const browserPath=auditPaths.browser();
const profile=path.resolve('artifacts/qa/guide-directory-themes-browser-profile');
const output=path.resolve('artifacts/qa/guide-directory-themes-qa');
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
  await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/usage-guide/usage-guide-directory-themes-preview.html')).href});
  for(let i=0;i<80;i++) {
    if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".directory").length===12'))break;
    await pause(100);
  }
  assert.equal(await evaluate('document.querySelectorAll(".directory").length'),12);
  const shape=await run(()=>{
    return [...document.querySelectorAll('.phone')].map(phone=>({
      theme:phone.dataset.theme,
      children:[...phone.querySelector('.directory').children].map(el=>el.className),
      chapters:phone.querySelectorAll('.route-row').length,
      wrongParts:phone.querySelectorAll('.folio-spine,.folio-list,.route-head,.route-footer,.color-grid').length
    }));
  });
  assert.equal(shape.length,12);
  assert.equal(new Set(shape.map(p=>p.theme)).size,12);
  for(const s of shape){assert.deepEqual(s.children,['folio-head','route-list','menu-footer ']);assert.equal(s.chapters,4);assert.equal(s.wrongParts,0);}
  const contrastFailures=await run(()=>{
    const failures=[];
    for(const t of THEMES)for(const m of ['light','dark']){
      const p=palette(t,m);
      for(const [bg,fg] of [['paper','ink'],['paper','muted'],['control','ink'],['low','ink'],['high','ink'],
        ['strong','on-strong'],['accent','on-accent'],['plane-1','on-plane-1']]){
        const ratio=contrast(p[bg],p[fg]);if(ratio<4.5)failures.push({theme:t.id,mode:m,bg,fg,ratio});
      }
    }
    return failures;
  });
  assert.deepEqual(contrastFailures,[]);
  await screenshot('desktop-twelve-themes-light');
  const firstRow=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width:1440,height:1230,scale:1}});
  fs.writeFileSync(path.join(output,'desktop-first-row-light.png'),Buffer.from(firstRow.data,'base64'));
  const issues=[];
  for(const width of [1440,768,390,320]){
    await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width<500});
    for(const m of ['light','dark']){
      await run(m=>{document.querySelector('button[data-mode="'+m+'"]').click();},m);
      const layout=await run(()=>{
        const problems=[];
        if(document.documentElement.scrollWidth>innerWidth+1)problems.push('page overflow');
        for(const phone of document.querySelectorAll('.phone')){
          const box=phone.querySelector('.directory'),rect=box.getBoundingClientRect();
          const p=palette(themeFor(phone));
          if(getComputedStyle(box.querySelector('.menu-footer')).backgroundColor!==
            'rgb('+hexRgb(p.paper).join(', ')+')')problems.push(phone.dataset.theme+': footer is not A paper');
          for(const el of box.querySelectorAll('.folio-head,.route-list,.menu-footer,.chapter-button,.start-guide,.footer-meta,.close-menu')){
            const r=el.getBoundingClientRect();
            if(r.left<rect.left-.5||r.right>rect.right+.5||r.top<rect.top-.5||r.bottom>rect.bottom+.5)problems.push(phone.dataset.theme+':'+el.className+' escaped');
            if(el.scrollWidth>el.clientWidth+1)problems.push(phone.dataset.theme+':'+el.className+' overflow');
          }
        }
        return problems;
      });
      if(layout.length)issues.push({width,mode:m,layout});
    }
  }
  assert.deepEqual(issues,[]);
  await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1180,deviceScaleFactor:1,mobile:false});
  await screenshot('desktop-twelve-themes-dark');
  await click('button[data-mode="light"]');
  const ids=await evaluate('THEMES.map(t=>t.id)');
  for(const id of ids){
    const selector='.phone[data-theme="'+id+'"]';
    for(const chapter of [0,1,2,3]){
      await click(selector+' [data-chapter="'+chapter+'"]');
      assert.equal(await run(s=>document.querySelectorAll(s+' .step-option').length,selector),[9,6,16,12][chapter]);
      await click(selector+' [data-action="back"]');
    }
  }
  await click('.phone[data-theme="OLD_PORT"] [data-chapter="2"]');
  await click('.phone[data-theme="OLD_PORT"] [data-step="31"]');
  await click('.phone[data-theme="OLD_PORT"] [data-action="enter"]');
  assert.equal(await evaluate('states.get(document.querySelector(".phone[data-theme=OLD_PORT]")).current'),31);
  assert.equal(await evaluate('states.get(document.querySelector(".phone[data-theme=MATISSE]")).current'),1);
  await click('.phone[data-theme="OLD_PORT"] [data-action="reopen"]');
  await click('.phone[data-theme="OLD_PORT"] [data-action="skip"]');
  await click('.phone[data-theme="OLD_PORT"] [data-action="reopen"]');
  await run(()=>{progressSelect.value='17';progressSelect.onchange();});
  assert.equal(await evaluate('document.querySelectorAll(".route-row.current[data-chapter=\\"2\\"]").length'),12);
  for(const id of ['MATISSE','DUNHUANG','MORANDI']){
    await run(id=>{themeSelect.value=id;themeSelect.onchange();},id);
    assert.equal(await evaluate('document.querySelectorAll(".theme-study:not([hidden])").length'),1);
    assert.equal(await run(id=>document.querySelector('.theme-study:not([hidden])').dataset.theme===id,id),true);
    await screenshot('focus-'+id.toLowerCase());
  }
  await click('button[data-mode="dark"]');
  await screenshot('focus-morandi-dark');
  await click('button[data-mode="light"]');
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await run(()=>{themeSelect.value='MATISSE';themeSelect.onchange();});
  await screenshot('mobile-matisse-hybrid');
  await click('.phone[data-theme="MATISSE"] [data-chapter="3"]');
  await screenshot('mobile-chapter-detail');
  await click('.phone[data-theme="MATISSE"] [data-action="back"]');
  await run(()=>{themeSelect.value='all';themeSelect.onchange();});
  assert.equal(await evaluate('document.querySelectorAll(".theme-study:not([hidden])").length'),12);
  assert.deepEqual(exceptions,[]);
  console.log(JSON.stringify({ok:true,themes:12,composition:'A header + C route + A footer',contrastPairs:192,
    responsiveThemeStates:96,chapterEntries:48,independentProgress:true,screenshots:fs.readdirSync(output)},null,2));
}

main().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
  if(socket?.readyState===WebSocket.OPEN) {
    try{await Promise.race([send('Browser.close'),pause(2000)]);}catch{}
    socket.close();
  }
  if(browser&&!browser.killed)browser.kill();
});
