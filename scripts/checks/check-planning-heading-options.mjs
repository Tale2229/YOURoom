import auditPaths from './paths.cjs';
auditPaths.enter();
import assert from 'node:assert/strict';
const { chromium } = auditPaths.playwright();

const browser=await chromium.launch({headless:true,executablePath:auditPaths.browser()});
try {
  const page=await browser.newPage({viewport:{width:1040,height:1150},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(auditPaths.fileUrl("docs/archive/ui-experiments/planning-heading-options.html"));
  assert.equal(await page.locator('.option').count(),4);
  assert.equal(await page.locator('.room').count(),8);
  for(const variant of ['a','b','c','d']) {
    const root=page.locator(`.style-${variant}`),head=root.locator('.heading');
    await head.click();assert.equal(await head.getAttribute('aria-expanded'),'true');
    await root.locator('.direction').nth(3).click();
    assert.equal(await root.locator('.title-copy').innerText(),'综合推荐','browsing alone must not change the selected direction');
    await root.locator('.alternative').nth(3).click();
    assert.equal(await head.getAttribute('aria-expanded'),'false');
    assert.equal(await root.locator('.rank').innerText(),'04');
    assert.equal(await root.locator('.title-copy').innerText(),'收藏教室覆盖最多');
    assert.equal(await root.locator('.room-name').first().innerText(),'4-201');
    await head.click();await head.press('Escape');
    assert.equal(await head.getAttribute('aria-expanded'),'false');
    assert.equal(await root.locator('.drawer').evaluate(n=>n.inert),true);
  }
  await page.locator('.theme[data-campus=shahe]').click();
  assert.equal(await page.locator('.campus-name').first().innerText(),'沙河');
  await page.locator('#dark-mode').click();
  assert.equal(await page.locator('body').getAttribute('data-dark'),'true');
  await page.locator('#show-notice').check();
  await page.waitForTimeout(260);
  assert(await page.locator('.notice').first().evaluate(n=>n.getBoundingClientRect().height>20));
  const geometry=()=>page.evaluate(()=>[...document.querySelectorAll('.option .heading')].map(n=>{
    const r=n.getBoundingClientRect(),entry=n.querySelector('.entry').getBoundingClientRect(),title=n.querySelector('.title').getBoundingClientRect(),count=n.querySelector('.count').getBoundingClientRect();
    return {left:r.left,right:r.right,entryLeft:entry.left,entryRight:entry.right,titleRight:title.right,countLeft:count.left,countRight:count.right};
  }));
  for(const width of [320,360,400]) {
    await page.locator('#width').selectOption(String(width));
    for(const viewport of [320,390,1040]) {
      await page.setViewportSize({width:viewport,height:900});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`no horizontal page overflow at ${viewport}/${width}`);
      for(const g of await geometry()) {
        assert(g.entryRight<=g.right+.5,'entry stays within the heading');
        assert(g.titleRight<=g.entryLeft+.5,'title cannot cover the action');
        assert(g.countRight<=g.entryLeft+.5,'counter cannot cover the action');
      }
    }
  }
  await page.setViewportSize({width:1040,height:1150});await page.locator('#width').selectOption('360');
  await page.locator('#dark-mode').click();await page.locator('.theme[data-campus=west]').click();
  await page.locator('#show-notice').uncheck();
  // Reset selections for a like-for-like screenshot.
  for(const variant of ['a','b','c','d']) {
    const root=page.locator(`.style-${variant}`);await root.locator('.heading').click();
    await root.locator('.direction').first().click();await root.locator('.alternative').first().click();
  }
  await page.waitForTimeout(350);
  await page.screenshot({path:auditPaths.resolve("artifacts/qa/design-preview/planning-heading-options-desktop.png"),fullPage:true,animations:'disabled'});
  await page.locator('.style-a .heading').click();await page.waitForTimeout(300);
  await page.screenshot({path:auditPaths.resolve("artifacts/qa/design-preview/planning-heading-options-open.png"),fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:844});
  await page.locator('.theme[data-campus=shahe]').click();await page.locator('#dark-mode').click();
  await page.screenshot({path:auditPaths.resolve("artifacts/qa/design-preview/planning-heading-options-mobile.png"),fullPage:true,animations:'disabled'});
  await page.emulateMedia({reducedMotion:'reduce'});
  assert.equal(await page.locator('.chevron').first().evaluate(n=>getComputedStyle(n).transitionDuration),'0s');
  assert.deepEqual(errors,[]);
  console.log('PASS: four contextual variants; independent drawers and route selection; Escape/focus; campus and dark colors; notice placement; 320/360/400 widths across 320/390/1040 viewports; no overlapping actions or page overflow; reduced motion; three screenshots; no browser errors.');
} finally {await browser.close()}
