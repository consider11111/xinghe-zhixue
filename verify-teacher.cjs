const { chromium } = require('C:/Users/acer/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({headless:true,channel:'msedge'});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.addInitScript(() => localStorage.setItem('xinghe_token','test-teacher'));
    await page.route('**/api/auth/me', r => r.fulfill({json:{displayName:'Teacher',role:'teacher'}}));
    await page.route('**/api/education/**', async r => {
      await new Promise(resolve => setTimeout(resolve,250));
      await r.fulfill({json:{}});
    });
    await page.goto('http://localhost:8090/education.html');
    await page.locator('.teacher-grid').waitFor();
    const original = await page.locator('main').innerHTML();
    for (const index of [1,2,3]) {
      for (const target of ['#backOverview','#overviewBtn','nav a:first-child']) {
        await page.locator('nav a').nth(index).click();
        assert.equal(await page.locator('nav a').nth(index).getAttribute('class'),'active');
        await page.locator(target).click();
        assert.equal(await page.locator('main').innerHTML(),original);
        assert.equal(await page.locator('nav .active').count(),1);
        assert.equal(await page.locator('nav a').first().getAttribute('class'),'active');
      }
    }
    await page.screenshot({path:'teacher-return-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:'teacher-return-mobile.png',fullPage:true});
    assert.deepEqual(errors,[]);
    console.log('PASS: all 9 return paths restore the initial overview, active nav follows selection, no browser errors.');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exitCode=1;});
