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
    const page = await context.newPage();
    const scriptErrors = [];
    context.on('page', p => p.on('pageerror', error => scriptErrors.push(error.message)));
    page.on('pageerror', error => scriptErrors.push(error.message));
    await page.goto(base);
    await noOverflow(page);
    assert.equal(await page.locator('.article-row').count(), 3);
    await page.screenshot({path: path.join(screenshots, 'home-desktop.png'), fullPage: true});
    const rejected = await context.request.post(base + '/admin/posts', {form: {title: 'CSRF attempt'}});
    assert.equal(rejected.status(), 403);
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
    await page.getByRole('button', {name: '切换浅色模式'}).click();
    await page.reload();
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'light');
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
        await page.waitForURL(base + '/admin/posts');
    };
    await login();

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

    await page.goto(base + editPath);
    await page.locator('#status').selectOption('PUBLISHED');
    await page.getByRole('button', {name: '保存修改'}).click();
    await page.waitForURL(base + '/posts/' + id);
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
        for (const suffix of ['/', '/posts/' + id, editPath, `/admin/posts/${id}/history`, '/admin/posts']) {
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
    }
    console.log('Artifacts: ' + screenshots);
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
    if (browser) await browser.close();
    await stop();
    if (fs.existsSync(path.join(temp, 'server.log'))) {
        fs.copyFileSync(path.join(temp, 'server.log'), path.join(screenshots, 'server.log'));
    }
    console.log('Isolated test database/logs: ' + temp);
});
