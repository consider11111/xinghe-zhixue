const {chromium} = require('C:/Users/acer/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const base = process.env.TEST_URL || 'http://localhost:8091';

(async () => {
  const browser = await chromium.launch({headless:true,channel:'msedge'});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors = [], educationRequests = [];
    page.on('pageerror',e=>errors.push(e.message));
    page.on('request',r=>{if(r.url().includes('/api/education'))educationRequests.push(r.url());});
    await page.goto(base+'/education.html');
    assert.equal(await page.locator('main').isVisible(),false);
    await page.locator('#goLogin').click();
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('123456');
    await page.locator('#authSubmit').click();
    await page.locator('#accountTable tbody tr').first().waitFor();
    assert.equal(await page.locator('main .welcome,main .training,main .diagnosis').count(),0);
    assert.deepEqual(educationRequests,[]);
    assert.equal(await page.locator('nav button').count(),3);
    await page.screenshot({path:'target/admin-accounts-desktop.png',fullPage:true});
    await page.locator('#adminSearch').fill('admin');
    assert.match(await page.locator('#accountTable tbody').innerText(),/admin/);
    await page.locator('#adminSearch').fill('no_such_account_qa');
    assert.match(await page.locator('#accountTable').innerText(),/暂无符合条件/);
    await page.locator('#adminSearch').fill('');
    await page.locator('[data-tab=roles]').click();
    assert.match(await page.locator('main').innerText(),/账号安全规则/);
    await page.locator('[data-admin-view=base]').click();
    await page.locator('#newData').waitFor();
    for(const kind of ['subject','term','class']){
      await page.locator(`[data-kind=${kind}]`).click();
      assert.equal(await page.locator(`[data-kind=${kind}]`).getAttribute('aria-selected'),'true');
    }
    await page.screenshot({path:'target/admin-base-desktop.png',fullPage:true});
    await page.locator('#newData').click();
    await page.locator('dialog input[name=code]').fill('QA-CLASS');
    await page.locator('dialog input[name=name]').fill('QA Class');
    await page.screenshot({path:'target/admin-base-dialog.png'});
    await page.locator('.admin-cancel').click();
    await page.locator('[data-admin-view=operations]').click();
    await page.locator('.admin-health').waitFor();
    assert.match(await page.locator('.admin-metrics').innerText(),/连接正常/);
    await page.screenshot({path:'target/admin-operations-desktop.png',fullPage:true});
    await page.locator('#refreshAdmin').click();
    await page.locator('.admin-health').waitFor();
    for(const view of ['accounts','base','operations']){
      await page.setViewportSize({width:390,height:844});
      await page.locator(`[data-admin-view=${view}]`).click();
      if(view==='accounts')await page.locator('[data-tab=users]').click();
      await page.locator(view==='accounts'?'#accountTable':view==='base'?'#newData':'.admin-health').waitFor();
      assert.equal(await page.locator(`nav [data-admin-view=${view}]`).getAttribute('aria-current'),'page');
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
      await page.screenshot({path:`target/admin-${view}-mobile.png`,fullPage:true});
    }
    // Test form error/success handling without adding disposable accounts to the live database.
    await page.setViewportSize({width:1440,height:1000});
    await page.locator('[data-admin-view=accounts]').click();
    await page.locator('#newUser').waitFor();
    await page.locator('#newUser').click();
    await page.locator('dialog [name=username]').fill('qa_browser');
    await page.locator('dialog [name=displayName]').fill('QA Browser');
    await page.locator('dialog [name=password]').fill('Test123456');
    let posts=0;
    await page.route('**/api/admin/users', async route=>{
      if(route.request().method()!=='POST')return route.continue();
      const body=route.request().postDataJSON();
      assert.equal(body.username,'qa_browser');assert.equal(body.enabled,true);
      posts++;
      await route.fulfill({status:posts===1?409:200,json:posts===1?{message:'账号已存在'}:{id:123456}});
    });
    await page.locator('dialog [type=submit]').click();
    await page.getByText('账号已存在',{exact:true}).waitFor();
    await page.locator('dialog [type=submit]').click();
    await page.locator('dialog').waitFor({state:'detached'});
    assert.equal(posts,2);
    await page.unroute('**/api/admin/users');
    await page.locator('.logout-btn').click();
    await page.locator('#goLogin').waitFor();
    await page.locator('#goLogin').click();
    await page.locator('#username').fill('teacher');await page.locator('#password').fill('123456');
    await page.locator('#authSubmit').click();await page.locator('.teacher-grid').waitFor();
    assert.equal(await page.locator('.admin-workspace').count(),0);
    for(const index of [1,2,3]){
      await page.locator('nav a').nth(index).click();await page.locator('#backOverview').click();
      await page.locator('.teacher-grid').waitFor();
    }
    await page.locator('.logout-btn').click();await page.locator('#goLogin').click();
    await page.locator('#username').fill('student');await page.locator('#password').fill('123456');
    await page.locator('#authSubmit').click();await page.locator('#diagnosis .score').waitFor();
    assert.equal(await page.locator('.admin-workspace').count(),0);
    assert.deepEqual(errors,[]);
    console.log('PASS: actual admin login/data/status, all navigation, desktop/mobile layout, form handling, logout and student/teacher isolation.');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
