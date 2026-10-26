const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
  const targets = await fetch('http://localhost:9238/json/list').then(r => r.json());
  const target = targets.find(t => t.type === 'page' && t.url === 'about:blank');
  assert(target, 'Dedicated preview page must exist');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let serial = 0;
  const pending = new Map(), errors = [];
  socket.onmessage = ({data}) => {
    const msg = JSON.parse(data);
    if (msg.method === 'Runtime.exceptionThrown') errors.push(msg.params.exceptionDetails.text);
    if (msg.id && pending.has(msg.id)) {
      const {resolve, reject} = pending.get(msg.id); pending.delete(msg.id);
      if (msg.error) reject(new Error(JSON.stringify(msg.error))); else resolve(msg.result);
    }
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++serial; pending.set(id, {resolve, reject}); socket.send(JSON.stringify({id, method, params}));
  });
  const evaluate = async expression => {
    const r = await send('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true});
    if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
    return r.result.value;
  };
  const screenshot = async name => {
    const shot = await send('Page.captureScreenshot', {format:'png', captureBeyondViewport:true});
    fs.writeFileSync(`${auditPaths.output}/${name}.png`, Buffer.from(shot.data, 'base64'));
  };
  try {
    await send('Runtime.enable'); await send('Page.enable');
    await send('Emulation.setEmulatedMedia', {features:[{name:'prefers-reduced-motion',value:'reduce'}]});
    await send('Emulation.setDeviceMetricsOverride', {width:1100,height:1100,deviceScaleFactor:1,mobile:false});
    const loaded = new Promise(resolve => {
      socket.addEventListener('message', function handler({data}) {
        if (JSON.parse(data).method === 'Page.loadEventFired') { socket.removeEventListener('message', handler); resolve(); }
      });
    });
    await send('Page.navigate', {url:auditPaths.fileUrl("docs/archive/ui-experiments/close-button-options.html")});
    await loaded;
    assert.equal(await evaluate('document.querySelectorAll(".option").length'), 4);
    assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);
    await screenshot('close-button-options-desktop');
    const results = await evaluate(`(() => {
      const results=[];
      for(const a of document.querySelectorAll('.option')){
        const chip=a.querySelector('.chip'); const before=chip.getAttribute('aria-pressed');
        chip.click(); const chosen=chip.getAttribute('aria-pressed');
        a.querySelector('.close').click();
        const closed=a.classList.contains('closed') && a.querySelector('.dialog').inert;
        a.querySelector('.reopen button').click();
        const kept=chip.getAttribute('aria-pressed')===chosen && chosen!==before;
        a.querySelector('.clear').click();
        const cleared=[...a.querySelectorAll('.chip')].every(c=>c.getAttribute('aria-pressed')==='false');
        a.querySelector('.add').click();a.querySelector('.add').click();
        const added=a.querySelectorAll('[data-new]').length===1;
        results.push({type:a.dataset.type,closed,kept,cleared,added});
      }
      return results;
    })()`);
    for (const r of results) for (const key of ['closed','kept','cleared','added']) assert.equal(r[key],true,`${r.type}:${key}`);
    await evaluate(`document.querySelector('[data-value="shahe"]').click();document.querySelector('#dark').click()`);
    assert.equal(await evaluate(`[...document.querySelectorAll('.campus')].every(c=>c.textContent==='沙河')`),true);
    await screenshot('close-button-options-dark');
    await send('Emulation.setDeviceMetricsOverride', {width:390,height:2200,deviceScaleFactor:1,mobile:true});
    assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);
    assert.equal(await evaluate(`[...document.querySelectorAll('.close')].every(b=>{const r=b.getBoundingClientRect();return r.width>=48&&r.height>=48&&r.right<=innerWidth})`),true);
    await screenshot('close-button-options-mobile');
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({options:4,behaviors:results,mobileOverflow:false,consoleErrors:errors,screenshots:3}));
    await send('Browser.close');
  } finally { socket.close(); }
})().catch(e => { console.error(e); process.exitCode=1; });
