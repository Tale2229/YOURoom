const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const root = path.resolve('docs/colors');
const themes = ['砂岩','松绿','马蒂斯','敦煌','旧港来信','蜜桃乌梅','勃艮第','莫兰迪','入骨相思','橙黄橘绿时','晨钟暮鼓','蒙德里安'];
const files = fs.readdirSync(root, { recursive: true }).filter(p => fs.statSync(path.join(root,p)).isFile());
let assertions = 0, links = 0;
function check(ok, message) { assertions++; if (!ok) throw Error(message); }
function localLink(file, href) {
  if (/^(?:https?:|mailto:|data:|#)/.test(href)) return;
  const target = decodeURIComponent(href.split(/[?#]/)[0]);
  check(fs.existsSync(path.resolve(root,path.dirname(file),target)), file+' broken link '+href);
  links++;
}
for (const name of themes) {
  check(fs.statSync(path.join(root,name)).isDirectory(), name+' folder');
  for (const file of ['README.md','avatar-original.svg']) check(fs.existsSync(path.join(root,name,file)),name+'/'+file);
}
check(fs.readdirSync(root).filter(p=>fs.statSync(path.join(root,p)).isDirectory()&&p!=='images').length===12,'Exactly 12 theme folders');
check(fs.readdirSync(path.join(root,'images')).length===13,'All 13 historical color cards retained');
for (const file of files.filter(p=>/\.(?:md|html)$/.test(p))) {
  const text = fs.readFileSync(path.join(root,file),'utf8');
  const regex = file.endsWith('.md') ? /\[[^\]]*\]\(([^)]+)\)/g : /href="([^"]+)"/g;
  for (const match of text.matchAll(regex)) localLink(file,match[1]);
}
const image = fs.readFileSync(path.join(root,'砂岩/avatar-generated-v1.png'));
check(image.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),'Selected asset is PNG');
check(image.readUInt32BE(16)===1254&&image.readUInt32BE(20)===1254,'Generated original stays 1254 x 1254');
check(crypto.createHash('sha256').update(image).digest('hex')==='beddcd49f17054b861360ec1ef04d10c5aa2bd170c8dcfdae9aaecc8757ea290','Original PNG unchanged');
const svgs = files.filter(p=>p.endsWith('.svg'));
check(svgs.length===19,'12 current SVGs, 2 dark variants and 5 Matisse candidates');
for (const file of svgs) check(!/<(?:script|image|foreignObject)\b|(?:href|url\()|var\(/i.test(fs.readFileSync(path.join(root,file),'utf8')),file+' standalone, no external dependencies');

const profile = fs.mkdtempSync(path.join(os.tmpdir(),'youroom-color-archive-check-'));
const proc = spawn(auditPaths.browser(),[
  '--headless=new','--remote-debugging-port=9382','--no-first-run','--no-default-browser-check',
  '--disable-extensions','--disable-background-networking','--disable-sync','--user-data-dir='+profile,'about:blank'
],{windowsHide:true,stdio:'ignore'});
const pause = ms=>new Promise(r=>setTimeout(r,ms));
let socket,seq=0;
const pending = new Map(),errors=[],network=[];
async function main() {
  let info;
  for(let i=0;i<100;i++) {try {info=(await fetch('http://127.0.0.1:9382/json/list').then(r=>r.json())).find(t=>t.type==='page');if(info)break;}catch{}await pause(100);}
  check(info,'Chrome connected');
  socket=new WebSocket(info.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject;});
  socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);if(m.method==='Network.requestWillBeSent')network.push(m.params.request.url);};
  const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));});
  const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
  await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
  await send('Emulation.setDeviceMetricsOverride',{width:1360,height:1000,deviceScaleFactor:1,mobile:false});
  const previews=files.filter(p=>p.endsWith('.html'));
  for(const file of previews) {
    const url=pathToFileURL(path.join(root,file)).href;
    await send('Page.navigate',{url});
    let ready=false;
    for(let i=0;i<100;i++) {ready=await js(`location.href===${JSON.stringify(url)}&&document.readyState==='complete'&&!!document.querySelector('.phone')`);if(ready)break;await pause(50);}
    check(ready,file+' loaded');
    check(await js("!document.querySelector('parsererror')"),file+' parses');
    const toggle=await js("document.getElementById('mode-toggle')?'#mode-toggle':'#mode'");
    check(await js(`!!document.querySelector(${JSON.stringify(toggle)})`),file+' mode toggle present');
    await js(`document.querySelector(${JSON.stringify(toggle)}).click()`);
    await pause(70);
    check(await js("(document.documentElement.dataset.mode||document.documentElement.dataset.theme)==='dark'"),file+' dark mode works');
    await js(`document.querySelector(${JSON.stringify(toggle)}).click()`);
    check(await js("(document.documentElement.dataset.mode||document.documentElement.dataset.theme)==='light'"),file+' light mode works');
  }
  await send('Page.navigate',{url:'about:blank'});
  const gallery=[];
  for(const file of svgs) {
    const text=fs.readFileSync(path.join(root,file),'utf8');
    const data='data:image/svg+xml;base64,'+Buffer.from(text).toString('base64');
    const result=await js(`(async()=>{const doc=new DOMParser().parseFromString(${JSON.stringify(text)},'image/svg+xml');if(doc.querySelector('parsererror'))return {error:true};const image=new Image();image.src=${JSON.stringify(data)};await image.decode();const canvas=document.createElement('canvas');canvas.width=canvas.height=75;canvas.getContext('2d').drawImage(image,0,0,75,75);const pixels=canvas.getContext('2d').getImageData(0,0,75,75).data;return {width:image.naturalWidth,height:image.naturalHeight,opaque:[...pixels].filter((_,i)=>i%4===3).every(a=>a===255)}})()`);
    check(!result.error,file+' valid SVG XML');
    check(result.width===75&&result.height===75&&result.opaque,file+' renders opaque at native 75px');
    gallery.push(`<figure><img src="${data}"><figcaption>${file}</figcaption></figure>`);
  }
  await send('Page.setDocumentContent',{frameId:(await send('Page.getFrameTree')).frameTree.frame.id,html:'<!doctype html><meta charset="utf-8"><title>Theme archive QA</title><style>body{margin:24px;background:#ecece8;font:12px sans-serif;display:grid;grid-template-columns:repeat(5,1fr);gap:18px}figure{margin:0;text-align:center}img{width:150px;height:150px;display:block;margin:auto auto 8px}figcaption{overflow-wrap:anywhere}</style>'+gallery.join('')});
  await js('Promise.all([...document.images].map(i=>i.decode()))');
  const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  fs.writeFileSync(path.join(auditPaths.output,'color-archives-qa.png'),Buffer.from(shot.data,'base64'));
  check(errors.length===0,'No browser exceptions: '+errors.join('\n'));
  check(!network.some(u=>/^https?:/.test(u)),'Previews and avatars stay offline');
  console.log(JSON.stringify({passed:assertions,folders:themes.length,links,htmlPreviews:previews.length,svgAssets:svgs.length,png:'1254x1254, unchanged SHA256',runtimeErrors:errors.length}));
  await send('Browser.close');
}
const watchdog=setTimeout(()=>{console.error('Archive check timed out');socket?.close();proc.kill();process.exit(1);},90000);
main().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>{clearTimeout(watchdog);socket?.close();proc.kill();});
