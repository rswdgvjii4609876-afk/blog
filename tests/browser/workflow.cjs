'use strict';

// Uses a temporary H2 file database; never touches the user's development data.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {once} = require('node:events');
const {chromium} = require('playwright');
const crypto = require('node:crypto');
const authenticatedMode = process.env.BLOG_BROWSER_AUTH === '1';
const testPassword = crypto.randomBytes(24).toString('hex');

const root = path.resolve(__dirname, '../..');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'blog-browser-'));
const port = Number(process.env.BLOG_TEST_PORT || 18080);
const base = `http://127.0.0.1:${port}`;
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java') : 'java';
const screenshots = process.env.BLOG_SCREENSHOT_DIR || path.join(temp, 'screenshots');
fs.mkdirSync(screenshots, {recursive: true});
let server;
let browser;

async function start() {
    const log = fs.openSync(path.join(temp, 'server.log'), 'a');
    server = spawn(java, ['-Djava.net.preferIPv4Stack=true', '-jar',
        path.join(root, 'target/blog-0.0.1-SNAPSHOT.jar'), `--server.port=${port}`], {
        cwd: root, env: {...process.env, BLOG_DATA_DIR: path.join(temp, 'data'),
            ...(authenticatedMode ? {SPRING_PROFILES_ACTIVE: 'dev,shared', BLOG_ADMIN_USERNAME: 'browser-author', BLOG_ADMIN_PASSWORD: testPassword} : {})},
        stdio: ['ignore', log, log]
    });
    fs.closeSync(log);
    for (let i = 0; i < 120; i++) {
        if (server.exitCode !== null) throw new Error('Server exited: ' + fs.readFileSync(path.join(temp, 'server.log'), 'utf8'));
        try {
            const response = await fetch(base);
            if (response.ok && (await response.text()).includes('知行')) return;
        } catch { /* Wait for the actual response, not just an open port. */ }
        await new Promise(resolve => setTimeout(resolve, 500));
    }
    throw new Error('Server did not become ready; inspect ' + temp);
}

async function stop() {
    if (!server || server.exitCode !== null) return;
    const exited = once(server, 'exit');
    server.kill('SIGTERM');
    const timeout = setTimeout(() => server.kill('SIGKILL'), 15000);
    await exited;
    clearTimeout(timeout);
}

async function noOverflow(page) {
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'Horizontal overflow: ' + page.url());
}

(async () => {
    await start();
    browser = await chromium.launch({
        headless: true,
        ...(process.env.CHROMIUM_PATH ? {executablePath: process.env.CHROMIUM_PATH} : {}),
        args: ['--no-sandbox']
    });
    const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
    context.setDefaultTimeout(15000);
    const page = await context.newPage();
    const scriptErrors = [];
    context.on('page', p => p.on('pageerror', error => scriptErrors.push(error.message)));
    page.on('pageerror', error => scriptErrors.push(error.message));
    await page.goto(base);
    await noOverflow(page);
    assert.equal(await page.locator('.article-row').count(), 3);
    assert.equal(await page.locator('.latest-feature .featured-post').count(), 1);
    assert.equal(await page.locator('.archive-section .article-row').count(), 2);
    const featuredBounds = await page.locator('.featured-post').boundingBox();
    const archiveHeading = await page.locator('.archive-heading').boundingBox();
    assert(archiveHeading.y > featuredBounds.y + featuredBounds.height, 'Archive must be below latest card');

    await page.screenshot({path: path.join(screenshots, 'home-desktop.png'), fullPage: true});
    const rejected = await context.request.post(base + '/admin/posts', {form: {title: 'CSRF attempt'}});
    assert.equal(rejected.status(), 403);
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
    await page.getByRole('button', {name: '切换浅色模式'}).click();
    await page.reload();
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'light');
    await page.screenshot({path: path.join(screenshots, 'home-light-desktop.png'), fullPage: true});
    await page.getByRole('button', {name: '切换深色模式'}).click();
    console.log('PASS: seeded homepage, CSRF rejection and persistent theme switching');
    const login = async () => {
        if (!authenticatedMode) return;
        const visitor = await browser.newContext();
        const response = await visitor.request.get(base + '/admin/posts', {maxRedirects: 0});
        assert.equal(response.status(), 302);
        await visitor.close();
        await page.goto(base + '/login');
        await page.screenshot({path: path.join(screenshots, 'login-desktop.png'), fullPage: true});
        await page.locator('#username').fill('browser-author');
        await page.locator('#password').fill(testPassword);
        await page.getByRole('button', {name: '进入创作台 ↗'}).click();
        await page.waitForURL(base + '/admin/overview');
    };
    await login();
    await page.goto(base + '/admin/overview');
    await page.locator('#welcome').fill('欢迎来到我的博客，一起看看新的故事。');
    await page.getByRole('button', {name: '保存欢迎语'}).click();
    await page.waitForURL(base + '/admin/overview');
    await page.locator('#poll-question').fill('下一篇，你想读什么？');
    await page.locator('#poll-option-1').fill('学习笔记');
    await page.locator('#poll-option-2').fill('生活记录');
    await page.getByRole('button', {name: '＋ 添加选项'}).click();
    await page.locator('#poll-option-3').fill('临时选项');
    await page.getByRole('button', {name: '删除选项 2', exact: true}).click();
    assert.equal(await page.locator('.poll-edit-row').count(), 2);
    await page.locator('#poll-option-2').fill('生活记录');
    await page.locator('#poll-duration').selectOption('custom');
    await page.locator('#poll-custom-hours').fill('48');
    await page.locator('#poll-other-enabled').check();
    await page.getByRole('button', {name: '发布投票'}).click();
    await page.waitForURL(base + '/admin/overview');
    const homeVisitor = await browser.newContext({viewport: {width: 390, height: 844}});
    const homeReader = await homeVisitor.newPage();
    homeReader.on('pageerror', error => scriptErrors.push(error.message));
    await homeReader.goto(base);
    assert((await homeReader.locator('h1').textContent()).includes('一起看看新的故事'));
    assert.equal(await homeReader.locator('a[href^="/admin"]').count(), 0);
    assert.equal(await homeReader.locator('.featured-post').count(), 1);
    assert(await homeReader.locator('.latest-excerpt').isVisible());
    await homeReader.getByRole('link', {name: '身份切换'}).click();
    await homeReader.getByRole('link', {name: /我是作者/}).click();
    await homeReader.waitForURL(base + (authenticatedMode ? '/login' : '/admin/overview'));
    await homeReader.goto(base);
    for (const mood of ['开心', '期待']) {
        await homeReader.locator('.mood-choices button').filter({hasText: mood}).click();
        await homeReader.getByRole('status').filter({hasText: '已记下'}).waitFor();
    }
    assert((await homeReader.locator('.mood-choices button[aria-pressed="true"]').textContent()).includes('期待'));
    assert.equal(await homeReader.locator('.mood-count').evaluateAll(nodes => nodes.reduce((sum,n) => sum + Number(n.textContent),0)), 1);
    assert.equal(await homeReader.locator('.poll-count').count(), 0);
    await homeReader.locator('.poll-option').first().click();
    await homeReader.getByRole('button', {name: '参与投票'}).click();
    await homeReader.getByRole('status').filter({hasText: '投票已保存'}).waitFor();
    await homeReader.locator('.poll-option').nth(1).click();
    await homeReader.getByRole('button', {name: '修改选择'}).click();
    await homeReader.getByRole('status').filter({hasText: '投票已保存'}).waitFor();
    assert((await homeReader.locator('.poll-card').textContent()).includes('共 1 份投票'));
    await homeReader.locator('.poll-countdown').waitFor();
    assert((await homeReader.locator('.poll-countdown').textContent()).includes('剩余'));
    await homeReader.locator('.poll-option').last().click();
    await homeReader.locator('#poll-other-text').fill('<script>privateOtherBrowser=1</script> 我自己的想法');
    await homeReader.getByRole('button', {name: '修改选择'}).click();
    await homeReader.getByRole('status').filter({hasText: '投票已保存'}).waitFor();
    assert(!(await homeReader.content()).includes('privateOtherBrowser'));
    const publicState = await homeVisitor.request.get(base + await homeReader.locator('[data-status-url]').getAttribute('data-status-url'));
    assert(!(await publicState.text()).includes('privateOtherBrowser'));
    await page.goto(base + '/admin/overview');
    await page.getByRole('link', {name: '查看“其他”答案（仅作者）'}).click();
    assert((await page.locator('main').textContent()).includes('<script>privateOtherBrowser=1</script>'));
    assert.equal(await page.locator('main script').count(), 0);
    await page.goto(base + '/admin/overview');
    await page.locator('.poll-extension input[name="hours"]').fill('24');
    await page.getByRole('button', {name: '延长截止时间'}).click();
    await page.getByRole('status').filter({hasText: '已保存'}).waitFor();
    await homeReader.reload();
    assert((await homeReader.locator('.poll-countdown').textContent()).includes('剩余'));
    console.log('PASS: numbered option add/remove, custom duration, private Other answer and author extension');
    // Exercise expiry and extension in a real browser without waiting hours or changing the DB clock.
    const timerVisitor = await browser.newContext({viewport: {width: 390, height: 844}});
    const timerPage = await timerVisitor.newPage();
    const statusPath = await homeReader.locator('[data-status-url]').getAttribute('data-status-url');
    const initialTimerNow = new Date().toISOString();
    const shortEnd = new Date(Date.now() + 3500).toISOString();
    let extendedTimer = false;
    await timerPage.route(base + '/', async route => {
        const response = await route.fetch();
        let html = await response.text();
        html = html.replace(/data-ends-at="[^"]*"/, `data-ends-at="${shortEnd}"`)
            .replace(/data-server-now="[^"]*"/, `data-server-now="${initialTimerNow}"`);
        await route.fulfill({response, body: html});
    });
    await timerPage.route(base + statusPath, route => route.fulfill({contentType: 'application/json', body: JSON.stringify({
        id: Number(statusPath.match(/polls\/(\d+)/)[1]), open: extendedTimer,
        serverNow: new Date().toISOString(), endsAt: extendedTimer ? new Date(Date.now() + 3600000).toISOString() : shortEnd,
        results: extendedTimer ? [] : [{index:0,label:'学习笔记',votes:0},{index:1,label:'生活记录',votes:0},{index:6,label:'其他',votes:1}],
        votes: extendedTimer ? null : 1
    })}));
    await timerPage.goto(base);
    await timerPage.getByRole('button', {name: '投票已结束'}).waitFor();
    assert(await timerPage.getByRole('button', {name: '投票已结束'}).isDisabled());
    await timerPage.waitForFunction(() => document.querySelector('.poll-total')?.textContent.includes('1'));
    extendedTimer = true;
    await timerPage.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await timerPage.waitForFunction(() => !document.querySelector('.poll-vote-form button[type=submit]').disabled);
    await timerPage.locator('.poll-option').last().click();
    assert(await timerPage.locator('#poll-other-text').isVisible());
    assert.equal(await timerPage.locator('.poll-count').count(), 0);
    await timerVisitor.close();
    console.log('PASS: live countdown expiry disables vote and shows results; extension restores voting');


    await homeReader.locator('#wall-nickname').fill('家里的读者');
    await homeReader.locator('#wall-body').fill('希望多写点生活。<script>window.wallXss=1</script>');
    await homeReader.getByRole('button', {name: '送出留言'}).click();
    await homeReader.getByRole('status').filter({hasText: '留言已收到'}).waitFor();
    assert.equal(await homeReader.locator('.wall-card .reader-comment').count(), 0);
    await page.goto(base + '/admin/messages');
    await page.locator('textarea[name="reply"]').fill('谢谢你，会安排的。');
    await page.getByRole('button', {name: '保存回复'}).click();
    await page.getByRole('status').filter({hasText: '留言已处理'}).waitFor();
    await page.getByRole('button', {name: '审核通过'}).click();
    await page.getByRole('status').filter({hasText: '留言已处理'}).waitFor();
    await homeReader.reload();
    assert.equal(await homeReader.locator('.wall-card .reader-comment').count(), 1);
    assert((await homeReader.locator('.author-reply').textContent()).includes('谢谢你，会安排的'));
    assert.equal(await homeReader.evaluate(() => window.wallXss), undefined);
    assert.equal(await homeReader.locator('.wall-card .reader-comment script').count(), 0);
    await noOverflow(homeReader);
    await homeReader.screenshot({path: path.join(screenshots, 'reader-home-mobile.png'), fullPage: true});
    await page.goto(base + '/admin/overview');
    await page.screenshot({path: path.join(screenshots, 'author-workspace.png'), fullPage: true});
    console.log('PASS: content-first reader home, identity gate, daily mood, single-choice poll and moderated wall/reply');

    const title = '端到端验收：学习成长记录';
    const source = '## 从问题出发\n\n**先验证，再下结论。**\n\n```java\nSystem.out.println("hello");\n```\n\n<script>window.__xss = 1</script>\n\n[x](javascript:alert%281%29)';
    await page.goto(base + '/admin/posts/new');
    await page.locator('#title').fill(title);
    await page.locator('#summary').fill('验证写作、发布、修订、恢复与重启持久化。');
    await page.locator('#content').fill(source);
    await page.locator('#changeNote').fill('创建可验证的学习笔记');
    await page.getByRole('button', {name: '预览排版'}).click();
    await page.waitForFunction(() => document.getElementById('preview-status').textContent.includes('预览已更新'));
    assert.equal(await page.locator('#markdown-preview h2').textContent(), '从问题出发');
    assert.equal(await page.locator('#markdown-preview code.hljs').count(), 1);
    assert.equal(await page.evaluate(() => window.__xss), undefined);
    await page.getByRole('button', {name: '保存草稿', exact: true}).click();
    await page.waitForURL(base + '/admin/posts');
    let item = page.locator('.admin-item').filter({has: page.getByRole('heading', {name: title, exact: true})});
    const editPath = await item.getByRole('link', {name: '编辑', exact: true}).getAttribute('href');
    const id = editPath.match(/posts\/(\d+)/)[1];
    assert.equal((await context.request.get(base + '/posts/' + id)).status(), 404);
    console.log('PASS: Markdown preview, local highlighting and draft isolation');

    await page.goto(base + editPath);
    await page.locator('#status').selectOption('PUBLISHED');
    await page.locator('#changeNote').fill('确认内容，首次发布');
    await page.getByRole('button', {name: '保存修改'}).click();
    await page.waitForURL(base + '/posts/' + id);
    assert.equal(await page.locator('.article-toc a').textContent(), '从问题出发');
    assert.equal(await page.evaluate(() => window.__xss), undefined);
    assert((await page.locator('.markdown-body').innerText()).includes('<script>'));
    await noOverflow(page);
    await page.screenshot({path: path.join(screenshots, 'article-desktop.png'), fullPage: true});
    await page.locator('.post-footer').scrollIntoViewIfNeeded();
    await page.waitForFunction(() => document.getElementById('reading-progress').value > 0);
    assert.equal(await page.locator('.article-toc a[aria-current]').count(), 1);
    console.log('PASS: publication, safe rendering, reading progress and active article navigation');

    const reader = await browser.newContext({viewport: {width: 390, height: 844}});
    const readerPage = await reader.newPage();
    readerPage.on('pageerror', error => scriptErrors.push(error.message));
    await readerPage.goto(base + '/posts/' + id);
    const readerComment = '<script>window.readerXss = 1</script> 读者的真实问题';
    await readerPage.locator('#comment-nickname').fill('路过的读者');
    await readerPage.locator('#comment-body').fill(readerComment);
    await readerPage.getByRole('button', {name: '提交评论，等待审核 ↗'}).click();
    await readerPage.getByRole('status').filter({hasText: '审核通过'}).waitFor();
    assert.equal(await readerPage.locator('.reader-comment').count(), 0);
    assert((await reader.request.get(base + '/admin/comments', {maxRedirects: 0})).status() === (authenticatedMode ? 302 : 200));
    await page.goto(base + '/admin/comments');
    const pendingComment = page.locator('.moderation-comment').filter({hasText: '路过的读者'});
    await pendingComment.getByRole('button', {name: '审核通过'}).click();
    await page.waitForURL(base + '/admin/comments');
    await readerPage.reload();
    assert.equal(await readerPage.locator('.comment-body').textContent(), readerComment);
    assert.equal(await readerPage.evaluate(() => window.readerXss), undefined);
    assert.equal(await readerPage.locator('.comment-body script').count(), 0);
    for (const stars of [2, 5]) {
        await readerPage.locator('.rating-stars label').nth(stars - 1).click();
        await readerPage.getByRole('button', {name: '保存评分'}).click();
        await readerPage.getByRole('status').filter({hasText: '评分已保存'}).waitFor();
    }
    assert.equal(await readerPage.locator('.rating-average').textContent(), '5.0');
    assert((await readerPage.locator('.rating-panel').textContent()).includes('1 份评分'));
    await noOverflow(readerPage);
    await readerPage.screenshot({path: path.join(screenshots, 'discussion-mobile.png'), fullPage: true});
    await page.goto(base + '/admin/comments?status=APPROVED');
    await page.screenshot({path: path.join(screenshots, 'moderation-desktop.png'), fullPage: true});
    await page.getByRole('button', {name: '隐藏评论'}).click();
    await readerPage.reload();
    assert.equal(await readerPage.locator('.reader-comment').count(), 0);
    await page.goto(base + '/admin/comments?status=HIDDEN');
    await page.getByRole('button', {name: '审核通过'}).click();
    console.log('PASS: anonymous moderated comments, escaped content, hide/reapprove and repeat star voting');


    const stale = await context.newPage();
    await stale.goto(base + editPath);
    await page.goto(base + editPath);
    await page.locator('#content').fill(source + '\n\n## 复盘\n\n这一版补充了结论。');
    await page.locator('#changeNote').fill('补充实践后的结论');
    await page.getByRole('button', {name: '保存修改'}).click();
    await page.waitForURL(base + '/posts/' + id);
    await stale.locator('#content').fill('旧窗口的输入必须保留');
    await stale.getByRole('button', {name: '保存修改'}).click();
    await stale.getByRole('alert').waitFor();
    assert.equal(await stale.locator('#content').inputValue(), '旧窗口的输入必须保留');
    stale.on('dialog', dialog => dialog.accept());
    await stale.close({runBeforeUnload: false});
    console.log('PASS: stale editor cannot overwrite a newer revision');

    await page.goto(base + `/admin/posts/${id}/history`);
    assert.equal(await page.locator('.timeline > li').count(), 3);
    await page.screenshot({path: path.join(screenshots, 'history-desktop.png'), fullPage: true});
    await page.locator('.timeline > li').last().getByRole('link', {name: '对比当前版本'}).click();
    assert(await page.locator('.diff-added').count() > 0);
    assert((await page.locator('.diff-code').allTextContents()).some(text => text.includes('<script>')));
    assert.equal(await page.evaluate(() => window.__xss), undefined);
    await page.screenshot({path: path.join(screenshots, 'diff-desktop.png'), fullPage: true});
    await page.getByRole('link', {name: '← 返回历史快照'}).click();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', {name: '恢复为草稿'}).click();
    await page.waitForURL('**/edit?restored');
    assert.equal(await page.locator('#status').inputValue(), 'DRAFT');
    assert.equal(await page.locator('#content').inputValue(), source);
    assert.equal((await context.request.get(base + '/posts/' + id)).status(), 404);
    console.log('PASS: restore creates a new draft and withdraws public visibility');

    await stop();
    await start();
    await login();
    await page.goto(base + editPath);
    assert.equal(await page.locator('#content').inputValue(), source);
    await page.goto(base + `/admin/posts/${id}/history`);
    assert.equal(await page.locator('.timeline > li').count(), 4);
    await page.goto(base + '/admin/posts');
    assert.equal(await page.locator('.admin-item').count(), 4, 'Restart must not duplicate seeds');
    console.log('PASS: article and all four revisions survive process restart');
    await homeReader.goto(base);
    assert((await homeReader.locator('h1').textContent()).includes('一起看看新的故事'));
    assert((await homeReader.locator('.poll-card').textContent()).includes('共 1 份投票'));
    assert((await homeReader.locator('.author-reply').textContent()).includes('谢谢你，会安排的'));
    await page.goto(base + '/admin/overview');
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', {name: '结束投票'}).click();
    await page.getByRole('status').filter({hasText: '已保存'}).waitFor();
    await homeReader.reload();
    assert.equal(await homeReader.locator('.poll-card').count(), 1);
    assert(await homeReader.getByRole('button', {name: '投票已结束'}).isDisabled());
    assert((await homeReader.locator('.poll-status').textContent()).includes('已结束'));
    assert((await homeReader.locator('.poll-card').textContent()).includes('共 1 份投票'));
    assert(!(await homeReader.content()).includes('privateOtherBrowser'));
    await homeVisitor.close();
    console.log('PASS: community settings/votes/replies survive restart and closing poll disables voting while preserving results');


    await page.goto(base + editPath);
    await page.locator('#status').selectOption('PUBLISHED');
    await page.getByRole('button', {name: '保存修改'}).click();
    await page.waitForURL(base + '/posts/' + id);
    await readerPage.goto(base + '/posts/' + id);
    assert.equal(await readerPage.locator('.comment-body').textContent(), readerComment);
    assert.equal(await readerPage.locator('.rating-average').textContent(), '5.0');
    assert((await readerPage.locator('.rating-panel').textContent()).includes('1 份评分'));
    await reader.close();
    console.log('PASS: comments and browser rating survive restart, draft restore and republication');
    const mobile = await context.newPage();
    await mobile.setViewportSize({width: 390, height: 844});
    for (const [suffix, name] of [['/', 'home-mobile'], ['/posts/' + id, 'article-mobile'], [editPath, 'editor-mobile'], [`/admin/posts/${id}/history`, 'history-mobile']]) {
        await mobile.goto(base + suffix);
        await mobile.evaluate(() => window.scrollTo(0, 0));
        if (suffix.startsWith('/posts/')) {
            await mobile.waitForFunction(() => document.documentElement.scrollHeight <= window.innerHeight || document.getElementById('reading-progress').value === 0);
        }
        await noOverflow(mobile);
        await mobile.screenshot({path: path.join(screenshots, name + '.png'), fullPage: true});
    }
    for (const width of [320, 768]) {
        await mobile.setViewportSize({width, height: 900});
        for (const suffix of ['/', '/identity', '/admin/overview', '/admin/messages?status=APPROVED', '/posts/' + id, editPath, `/admin/posts/${id}/history`, '/admin/posts']) {
            await mobile.goto(base + suffix);
            await noOverflow(mobile);
        }
    }
    await mobile.close();
    await page.goto(base + '/?q=' + encodeURIComponent('端到端验收'));
    assert.equal(await page.locator('.article-row').count(), 1);
    await page.goto(base + '/admin/posts/' + id + '/delete');
    await page.getByRole('link', {name: '取消，保留文章'}).click();
    assert.equal((await context.request.get(base + '/posts/' + id)).status(), 200);
    await page.goto(base + '/admin/posts/' + id + '/delete');
    await page.getByRole('button', {name: '确认永久删除'}).click();
    await page.waitForURL(base + '/admin/posts');
    assert.equal((await context.request.get(base + '/posts/' + id)).status(), 404);
    assert.equal((await context.request.get(base + `/admin/posts/${id}/history`)).status(), 404);
    await page.goto(base + '/posts/999999999');
    assert((await page.textContent('h1')).includes('这一页还没有写下'));
    assert.deepEqual(scriptErrors, []);
    console.log('PASS: mobile layouts, search, delete confirmation, cleanup and friendly 404');
    if (authenticatedMode) {
        await page.goto(base + '/admin/posts');
        await page.getByRole('button', {name: '退出登录'}).click();
        await page.waitForURL(base + '/?signedout');
        await page.goto(base + '/admin/posts');
        await page.waitForURL(base + '/login');
        console.log('PASS: authenticated deployment mode login, visitor isolation and logout');
        // Keep an editor open while another tab logs out: stale CSRF must not lose text.
        await login();
        await page.goto(base + '/admin/posts/new');
        await page.locator('#content').fill('登录过期也必须保留的正文');
        const logoutTab = await context.newPage();
        await logoutTab.goto(base + '/admin/posts');
        await logoutTab.getByRole('button', {name: '退出登录'}).click();
        await logoutTab.waitForURL(base + '/?signedout');
        await page.getByRole('button', {name: '预览排版'}).click();
        await page.waitForFunction(() => document.getElementById('preview-status').textContent.includes('重新登录'));
        assert.equal(await page.locator('#content').inputValue(), '登录过期也必须保留的正文');
        assert.equal(await page.locator('#markdown-preview form').count(), 0);
        // Also guard against a followed login redirect with a misleading final HTTP 200.
        await page.route('**/admin/markdown/preview', route => route.fulfill({
            status: 302, headers: {location: '/login'}, body: ''
        }));
        await page.getByRole('button', {name: '预览排版'}).click();
        await page.waitForFunction(() => !document.getElementById('preview-button').disabled);
        assert((await page.locator('#preview-status').textContent()).includes('重新登录'));
        assert.equal(await page.locator('#markdown-preview form').count(), 0);
        assert.equal(await page.locator('#content').inputValue(), '登录过期也必须保留的正文');
        await logoutTab.close();
        console.log('PASS: expired-session preview and followed login redirects preserve editor content');

    }
    assert.deepEqual(scriptErrors, []);
    console.log('Artifacts: ' + screenshots);
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
    if (browser) await browser.close();
    await stop();
    if (fs.existsSync(path.join(temp, 'server.log'))) {
        fs.copyFileSync(path.join(temp, 'server.log'), path.join(screenshots, 'server.log'));
    }
    console.log('Isolated test database/logs: ' + temp);
});
