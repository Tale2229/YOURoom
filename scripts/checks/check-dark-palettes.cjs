const auditPaths = require('./paths.cjs');
auditPaths.enter();
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {spawn} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'youroom-dark-palettes-'));
const proc = spawn(auditPaths.browser(), [
 '--headless=new', '--remote-debugging-port=9371', '--no-first-run', '--no-default-browser-check',
 '--disable-extensions', '--disable-background-networking', '--disable-sync', '--user-data-dir='+profile, 'about:blank'
], {windowsHide:true, stdio:'ignore'});
const pause = ms => new Promise(r => setTimeout(r, ms));
let socket, id=0, count=0; const pending = new Map();
const examples = {MATISSE:'matisse', DUNHUANG:'dunhuang', OLD_PORT:'旧港来信', PEACH_PLUM:'蜜桃乌梅',
 BURGUNDY:'勃艮第', MORANDI:'莫兰迪', RED_BEAN:'入骨相思', AUTUMN_CITRUS:'橙黄橘绿时', MORNING_BELL:'晨钟暮鼓', MONDRIAN:'蒙德里安'};
const source = fs.readFileSync('app/src/main/java/com/example/youroom/ui/theme/Theme.kt', 'utf8');
const roles = fs.readFileSync('app/src/main/java/com/example/youroom/ui/theme/ThemeRoles.kt', 'utf8');
function check(condition, note) { count++; if (!condition) throw Error(note); }
async function main() {
 let target;
 for(let i=0;i<100;i++) {try {target=(await fetch('http://127.0.0.1:9371/json/list').then(r=>r.json())).find(t=>t.type==='page');if(target)break;}catch{}await pause(100);}
 check(target, 'Chrome connected'); socket=new WebSocket(target.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
 socket.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result)}};
 const send=(method,params={})=>new Promise((resolve,reject)=>{const seq=++id;pending.set(seq,{resolve,reject});socket.send(JSON.stringify({id:seq,method,params}))});
 const js=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 await send('Page.enable'); await send('Runtime.enable');
 for(const [tone,name] of Object.entries(examples)) {
  await send('Page.navigate',{url:pathToFileURL(path.resolve('docs/colors/'+({matisse:'马蒂斯',dunhuang:'敦煌'}[name]||name)+'/'+name+'-preview.html')).href});
  for(let i=0;i<100;i++){if(await js("document.readyState==='complete'&&!!document.querySelector('#mode')"))break;await pause(50);}
  await js("document.querySelector('#mode').click()");await pause(100);
  const css=await js("(()=>{const c=getComputedStyle(document.documentElement);return Object.fromEntries(['paper','primary','ink','muted','header-face','nav-face','wheel'].map(k=>[k,c.getPropertyValue('--'+k).trim().toUpperCase()]))})()");
  const match=source.match(new RegExp('CampusTone\\.'+tone+' -> if\\(dark\\) palette\\(([^)]*)\\)'));
  check(match, tone+' native palette');
  const colors=[...match[1].matchAll(/0xFF([0-9A-F]{6})/g)].map(m=>'#'+m[1]);
  for(const [key,index] of [['paper',4],['primary',0],['ink',5],['muted',6]]) check(css[key]===colors[index], `${name} ${key}: HTML ${css[key]} != native ${colors[index]}`);
  const block=tone==='MATISSE' ? roles.split('if (tone != CampusTone.MATISSE) return legacy')[1] : roles.split('if (tone == CampusTone.'+tone+')')[1].split('\n    if (tone ==')[0];
  for(const [key,role] of [['header-face','header'],['nav-face','navigation'],['wheel','wheelCenter']]) {
   if(!css[key])continue;
   const literal=block.match(new RegExp('\\b'+role+'\\s*=\\s*(?:if\\(dark\\)\\s*)?Color\\(0xFF([0-9A-F]{6})\\)'));
   if(literal)check(css[key]==='#'+literal[1], `${name} ${role}: HTML ${css[key]} != native #${literal[1]}`);
  }
  console.log(name+': accepted edition '+await js("document.documentElement.dataset.edition||'1'")+' dark palette matched');
 }
 console.log('OK '+count+' HTML/native dark-palette checks');
 await send('Browser.close');
}
main().catch(e=>{console.error(e);process.exitCode=1}).finally(()=>{if(socket)socket.close();proc.kill()});
