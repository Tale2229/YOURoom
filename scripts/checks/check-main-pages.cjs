const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),vm=require('node:vm');
const {spawn}=require('node:child_process'),{pathToFileURL}=require('node:url');
const html=fs.readFileSync('docs/design/main-pages/main-pages-preview.html','utf8');
new vm.Script(html.match(/<script>([\s\S]*?)<\/script>/)[1],{filename:'main-pages-preview.html'});
const pause=ms=>new Promise(r=>setTimeout(r,ms)),pending=new Map(),errors=[],network=[],out=path.resolve('artifacts/qa/main-pages-qa');
let browser,socket,serial=0;
function send(method,params={}){const id=++serial;const p=new Promise((resolve,reject)=>pending.set(id,{resolve,reject}));socket.send(JSON.stringify({id,method,params}));return p;}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const run=(fn,...args)=>evaluate('('+fn.toString()+')(...'+JSON.stringify(args)+')');
async function click(selector){assert(await run(s=>{const b=document.querySelector(s);if(!b)return false;b.click();return true},selector),selector);await pause(30);}
async function screenshot(name,width=1440,height=1250){await pause(120);const r=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width,height,scale:1}});fs.writeFileSync(path.join(out,name+'.png'),Buffer.from(r.data,'base64'));}
async function size(width,height=1250){await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});}
async function main(){
 fs.mkdirSync(out,{recursive:true});
 browser=spawn(auditPaths.browser(),['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-extensions','--disable-sync','--remote-debugging-port=9796','--remote-debugging-address=127.0.0.1','--user-data-dir='+path.resolve('artifacts/qa/main-pages-browser-profile'),'about:blank'],{windowsHide:true,stdio:'ignore'});
 let target;for(let i=0;i<100;i++){try{target=(await(await fetch('http://127.0.0.1:9796/json/list')).json()).find(t=>t.type==='page');if(target)break}catch{}await pause(100)}assert(target,'Chrome did not start');
 socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j});
 socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.method==='Runtime.exceptionThrown')errors.push(m.params);if(m.method==='Network.requestWillBeSent'&&/^https?:/.test(m.params.request.url))network.push(m.params.request.url);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}};
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await size(1440);
 await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/design/main-pages/main-pages-preview.html')).href});
 for(let i=0;i<80;i++){if(await evaluate('document.readyState==="complete" && document.querySelectorAll(".phone").length===3'))break;await pause(100)}
 assert.equal(await evaluate('THEMES.length'),12);assert.equal(await evaluate('apps.length'),3);
 await screenshot('01-classrooms');
 await click('[data-page="planner"]');await pause(150);await screenshot('02-planning');
 await click('[data-page="settings"]');await pause(150);await screenshot('03-settings-initial');
 let hidden=await run(()=>apps.map(app=>{const p=phone(app),body=p.querySelector('.settings-page').getBoundingClientRect(),account=p.querySelector('.account-panel').getBoundingClientRect();return {id:app.id,viewport:body.bottom,account:account.top,scroll:p.querySelector('.settings-page').scrollTop}}));
 hidden.forEach(x=>assert(x.account>=x.viewport-1,'Initially exposed account: '+JSON.stringify(x)));
 await run(()=>apps.forEach(app=>{const b=phone(app).querySelector('.settings-page');b.style.scrollBehavior='auto';b.scrollTop=b.scrollHeight}));await pause(80);await screenshot('04-settings-revealed');
 assert(await run(()=>apps.every(app=>{const p=phone(app),b=p.querySelector('.settings-page').getBoundingClientRect(),v=p.querySelector('.account-version').getBoundingClientRect();return v.bottom<=b.bottom+1 && v.top>=b.top})), 'Version is not reachable');
 await click('#phone-A [data-action="clear-cache"]');assert.equal(await evaluate('document.querySelectorAll("#phone-A .phone-overlay").length'),1);await screenshot('05-safe-account-preview');await click('#phone-A [data-action="close-modal"]');
 await run(()=>apps.forEach(app=>{phone(app).querySelector('.settings-page').scrollTop=0}));
 await click('#phone-A [data-action="strip-options"]');await click('#phone-A [data-strip="等比例"]');assert.equal(await evaluate('apps[0].strip'),'等比例');await click('#phone-A [data-action="strip-options"]');
 await click('#phone-A [data-theme-step="1"]');assert.equal(await evaluate('apps[0].theme'),'DUNHUANG');assert.equal(await evaluate('apps[1].theme'),'MATISSE');
 await click('#phone-A [data-action="dark"]');assert.equal(await evaluate('apps[0].dark'),true);assert.equal(await evaluate('apps[1].dark'),false);
 await click('#reset');await click('[data-page="rooms"]');
 await run(()=>{const input=document.querySelector('#phone-A input[type=search]');input.value='3-305';input.dispatchEvent(new Event('input',{bubbles:true}))});assert.equal(await evaluate('document.querySelectorAll("#phone-A .room-card").length'),1);assert.equal(await evaluate('document.querySelectorAll("#phone-B .room-card").length'),6);
 await click('#phone-A [data-action="clear-search"]');await click('#phone-A [data-action="room"][data-name="2-201"]');assert.equal(await evaluate('apps[0].openRoom'),'2-201');
 await click('#phone-A [data-room-action="note"][data-name="2-201"]');await run(()=>{const field=document.querySelector('#phone-A textarea');field.value='窗边安静';field.dispatchEvent(new Event('input',{bubbles:true}))});await click('#phone-A [data-action="close-modal"]');assert.equal(await evaluate('apps[0].rooms[0].note'),'窗边安静');
 await click('#phone-B [data-action="filter"]');await click('#phone-B [data-building="教学楼 3"]');await click('#phone-B [data-action="close-modal"]');assert(await run(()=>[...document.querySelectorAll('#phone-B .room-card')].every(x=>x.dataset.room.startsWith('3-'))));
 await click('[data-page="planner"]');await pause(100);assert.equal(await evaluate('apps[0].start'),540);assert.equal(await evaluate('apps[0].end'),720);
 await run(()=>document.querySelector('#phone-A [data-wheel="start-hour"]').scrollTop=400);await pause(120);assert.equal(await evaluate('apps[0].start'),600);assert.equal(await evaluate('apps[0].end'),720);
 await click('#phone-A [data-action="add-period"]');assert.equal(await evaluate('apps[0].periods.length'),2,'Overlapping draft must not add a period');
 await run(()=>{apps[0].start=1080;apps[0].end=1140;renderPage(apps[0],'planner')});await click('#phone-A [data-action="add-period"]');assert.equal(await evaluate('apps[0].periods.length'),3);assert.equal(await evaluate('apps[0].routePeriods.length'),2,'Existing result stays frozen');await click('#phone-A [data-action="add-period"]');assert.equal(await evaluate('apps[0].periods.length'),3);
 await click('#phone-A [data-action="alternatives"]');await click('#phone-A [data-option="2"]');assert.equal(await evaluate('apps[0].option'),2);
 await click('#phone-A [data-action="generate"]');await click('#phone-A [data-nav="settings"]');await pause(750);assert.equal(await evaluate('apps[0].busy'),false);await click('#phone-A [data-nav="planner"]');assert.equal(await evaluate('document.querySelector("#phone-A [data-action=generate]").getAttribute("aria-label")'),'生成规划');
 await click('#reset');await click('[data-mode="dark"]');await click('[data-page="rooms"]');await screenshot('06-dark-classrooms');await click('[data-page="settings"]');await run(()=>apps.forEach(app=>{const b=phone(app).querySelector('.settings-page');b.style.scrollBehavior='auto';b.scrollTop=b.scrollHeight}));await screenshot('07-dark-settings-revealed');
 let layouts=0,footerChecks=0;const themes=await evaluate('THEMES.map(t=>t.id)');
 for(const width of [320,390,900,1440]){await size(width);for(const mode of ['light','dark'])for(const theme of themes){
   const result=await run(async(theme,mode)=>{const problems=[];apps.forEach(app=>{app.theme=theme;app.dark=mode==='dark'});
    for(const page of ['rooms','planner','settings']){setPage(page);await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));for(const app of apps){const p=phone(app),body=p.querySelector('.app-page');if(body.scrollWidth>body.clientWidth+1)problems.push({id:app.id,page,kind:'horizontal overflow',w:body.scrollWidth,c:body.clientWidth});if(page==='settings'){const b=body.getBoundingClientRect(),a=p.querySelector('.account-panel').getBoundingClientRect();if(a.top<b.bottom-1)problems.push({id:app.id,page,kind:'account initially exposed',top:a.top,bottom:b.bottom});const img=p.querySelector('[data-avatar]');await img.decode().catch(()=>{});if(!img.naturalWidth)problems.push({id:app.id,page,kind:'missing avatar'})}}}if(document.documentElement.scrollWidth>innerWidth+1)problems.push({kind:'document overflow'});return problems},theme,mode);
   assert.deepEqual(result,[],width+'/'+theme+'/'+mode+' '+JSON.stringify(result));layouts+=9;footerChecks+=3;
 }}
 await size(390,1150);await click('[data-mode="light"]');await run(()=>{selectedTheme='MATISSE';apps.forEach(app=>app.theme='MATISSE');setPage('settings');document.querySelector('.gallery').scrollLeft=0});await screenshot('08-phone-settings',390,1150);
 await send('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:1});
 const point=await run(()=>{const r=document.querySelector('#phone-A .settings-page').getBoundingClientRect();return {x:r.x+r.width/2,y:r.bottom-45,end:r.top+60}});
 await send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:point.x,y:point.y}]});for(let i=1;i<=9;i++){await send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:point.x,y:point.y+(point.end-point.y)*i/9}]});await pause(18)}await send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await pause(300);
 assert(await run(()=>document.querySelector('#phone-A .settings-page').scrollTop>40),'Real touch swipe did not reveal account');await screenshot('09-touch-revealed',390,1150);
 await send('Emulation.setTouchEmulationEnabled',{enabled:false});await size(1440);await click('[data-page="settings"]');await run(()=>apps.forEach(app=>{app.theme='MATISSE';app.dark=false;applyTheme(app)}));
 assert.equal(await evaluate('localStorage.length'),0);assert.deepEqual(network,[]);assert.deepEqual(errors,[]);
 const result={layouts,footerChecks,themes:12,modes:2,variants:3,pages:3,realTouchReveal:true,interactions:'passed',network,errors};fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{try{if(socket?.readyState===WebSocket.OPEN){await send('Browser.close');socket.close()}}catch{}browser?.kill()});
