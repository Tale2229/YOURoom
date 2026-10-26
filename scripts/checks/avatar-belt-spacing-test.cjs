const auditPaths = require('./paths.cjs');
auditPaths.enter();
const {chromium}=auditPaths.playwright();
const {pathToFileURL}=require('node:url');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({executablePath:auditPaths.browser(),headless:true});
 try {
 const page=await browser.newPage({viewport:{width:1440,height:1300},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const url=pathToFileURL(auditPaths.resolve("docs/colors/avatar-belt-preview.html")).href;
 await page.goto(url);
 await page.waitForFunction(()=>window.beltStudy&&document.querySelectorAll('.belt-canvas').length===12);
 assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().stride),105.6);
 const scales=[1.1,1.2,1.3,1.5,2,2.5];
 const pixels=scale=>Math.round(96*scale*10)/10;
 const picker=page.locator('#gallery .study').first().locator('.theme-picker');
 const shotDir=auditPaths.resolve("artifacts/qa/avatar-verification/");
 function checkCenters(s,stride){
   assert.equal(s.stride,stride);assert.ok(Math.abs(s.capturePixels-23.04)<1e-8);assert.ok(Math.abs(s.escapePixels-36.48)<1e-8);
   for(let i=1;i<s.centers.length;i++)assert.ok(Math.abs(s.centers[i].x-s.centers[i-1].x-stride)<.00001);
 }
 for(const scale of scales){
   await page.locator('#reset').click();
   await page.locator(`[data-belt-spacing="${scale}"]`).click();
   assert.equal(await page.locator(`[data-belt-spacing="${scale}"]`).getAttribute('aria-pressed'),'true');
   const stride=pixels(scale);
   const all=await page.evaluate(()=>Array.from({length:12},(_,i)=>window.beltStudy.snapshot(i)));
   all.forEach(s=>checkCenters(s,stride));
   assert.ok(await page.locator('#spacing-note').textContent().then(t=>t.includes(`${stride}px`)));
   const geometry=await picker.locator('.frame').boundingBox();assert.ok(Math.abs(geometry.width-95)<.01);assert.ok(Math.abs(geometry.height-95)<.01);
   await picker.scrollIntoViewIfNeeded();const b=await picker.boundingBox(),x=b.x+b.width/2,y=b.y+47.5;
   await page.mouse.move(x,y);await page.mouse.down();
   await page.mouse.move(x-35,y,{steps:8});
   await page.waitForFunction(()=>window.beltStudy.snapshot().dragging);
   let s=await page.evaluate(()=>window.beltStudy.snapshot());
   assert.equal(s.captured,0);assert.equal(s.position,0);assert.ok(Math.abs(s.raw-35/stride)<.005);checkCenters(s,stride);
   await page.mouse.move(x-44,y,{steps:8});
   await page.waitForFunction(()=>window.beltStudy.snapshot().captured===null);
   assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().frame),'SAND');
   await page.mouse.move(x-(stride-22),y,{steps:12});
   await page.waitForFunction(()=>window.beltStudy.snapshot().captured===1&&Math.abs(window.beltStudy.snapshot().position-1)<.002);
   s=await page.evaluate(()=>window.beltStudy.snapshot());checkCenters(s,stride);
   assert.equal(s.frame,'PINE');assert.equal(s.applied,'SAND');
   await page.screenshot({path:shotDir+`belt-spacing-${scale}-held.png`});
   await page.mouse.move(x-(stride-39),y,{steps:8});
   await page.waitForFunction(()=>window.beltStudy.snapshot().captured===null);
   assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().frame),'PINE');
   await page.mouse.move(x-stride,y,{steps:8});
   await page.waitForFunction(()=>window.beltStudy.snapshot().captured===1);
   await new Promise(resolve=>setTimeout(resolve,120));await page.mouse.up();
   await page.waitForFunction(()=>!window.beltStudy.snapshot().browsing&&window.beltStudy.snapshot().applied==='PINE',null,{timeout:6000});
   await page.locator('#gallery .study').first().locator('.next').click();
   await page.waitForFunction(()=>window.beltStudy.snapshot().frame==='MATISSE');
   assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().applied),'PINE');
   await page.waitForFunction(()=>window.beltStudy.snapshot().applied==='MATISSE',null,{timeout:5000});
   await page.locator('#mode-toggle').click();assert.equal(await page.locator('.phone[data-dark="true"]').count(),12);
   assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().stride),stride);
 }
 await page.locator('#reset').click();assert.equal(await page.evaluate(()=>window.beltStudy.snapshot().stride),105.6);
 for(const width of [390,320]){
   await page.setViewportSize({width,height:1160});
   for(const scale of scales){
     await page.locator(`[data-belt-spacing="${scale}"]`).click();
     assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
   }
 }
 await page.emulateMedia({reducedMotion:'reduce'});await picker.focus();await page.keyboard.press('ArrowRight');
 await page.waitForFunction(()=>window.beltStudy.snapshot().applied==='PINE',null,{timeout:5000});
 const touch=await browser.newPage({viewport:{width:390,height:1160},deviceScaleFactor:1,hasTouch:true,isMobile:true});
 touch.on('pageerror',e=>errors.push(e.message));await touch.goto(url);
 await touch.waitForFunction(()=>window.beltStudy);
 const touchPicker=touch.locator('#gallery .study').first().locator('.theme-picker');
 const cdp=await touch.context().newCDPSession(touch);
 for(const scale of scales){
   await touch.locator('#reset').click();await touch.locator(`[data-belt-spacing="${scale}"]`).click();
   await touchPicker.scrollIntoViewIfNeeded();const b=await touchPicker.boundingBox(),x=b.x+b.width-4,y=b.y+47.5;
   const travel=pixels(scale)-18;
   await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
   for(let i=1;i<=16;i++){
     await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x-travel*i/16,y}]});
     await new Promise(resolve=>setTimeout(resolve,15));
   }
   await touch.waitForFunction(()=>window.beltStudy.snapshot().captured===1);
   const s=await touch.evaluate(()=>window.beltStudy.snapshot());checkCenters(s,pixels(scale));assert.equal(s.applied,'SAND');
   await new Promise(resolve=>setTimeout(resolve,120));
   await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
   await touch.waitForFunction(()=>!window.beltStudy.snapshot().browsing&&window.beltStudy.snapshot().applied==='PINE',null,{timeout:6000});
 }
 await touch.locator('#reset').click();await touch.locator('[data-belt-spacing="1.2"]').click();
 await touch.locator('#mode-toggle').click();await touch.locator('[data-belt-spacing="1.2"]').scrollIntoViewIfNeeded();
 await touch.evaluate(async()=>{await Promise.all(document.getAnimations().map(a=>a.finished.catch(()=>{})));});
 await touch.screenshot({path:shotDir+'belt-spacing-mobile-dark.png'});
 assert.deepEqual(errors,[]);
 console.log('PASS: 1.1/1.2/1.3/1.5/2/2.5 = 105.6/115.2/124.8/144/192/240px; fixed shared centers across 12 themes; constant 23/36px detents; gap frame retention; drag/capture/reverse/release/arrow/touch; dark mode; reset; reduced motion; 390/320px no overflow; zero browser errors.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
