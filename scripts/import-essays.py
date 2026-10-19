#!/usr/bin/env python3
"""Import approved essays through the existing authenticated, CSRF-protected admin forms."""
import argparse
import getpass
import http.cookiejar
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


class Page(HTMLParser):
    def __init__(self, html):
        super().__init__(convert_charrefs=True)
        self.fields, self.links = {}, []
        self.capture = None
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'a' and 'href' in a:
            self.links.append(a['href'])
        if tag == 'input' and a.get('name'):
            self.fields[a['name']] = a.get('value', '')
        if tag == 'textarea' and a.get('name'):
            self.capture = a['name']
            self.fields[self.capture] = ''
        if tag == 'option' and 'selected' in a:
            self.fields['status'] = a.get('value', '')

    def handle_data(self, data):
        if self.capture:
            self.fields[self.capture] += data

    def handle_endtag(self, tag):
        if tag == 'textarea':
            self.capture = None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='HTTPS site URL, or local loopback HTTP')
    parser.add_argument('--apply', action='store_true', help='Apply changes; default is read-only preview')
    parser.add_argument('--archive-id', type=int, action='append', default=[],
                        help='Explicit additional test article ID to archive; repeatable')
    args = parser.parse_args()
    base = args.url.rstrip('/')
    u = urllib.parse.urlsplit(base)
    if u.username or u.password or u.path or u.query or u.fragment or not u.hostname:
        parser.error('Use a site origin without credentials or path')
    if u.scheme != 'https' and not (u.scheme == 'http' and u.hostname in ('localhost', '127.0.0.1', '::1')):
        parser.error('Remote connections require HTTPS')
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(path, fields=None):
        url = base + path
        data = None if fields is None else urllib.parse.urlencode(fields).encode()
        with opener.open(urllib.request.Request(url, data=data), timeout=30) as response:
            if urllib.parse.urlsplit(response.url).netloc != u.netloc:
                raise RuntimeError('Unexpected response origin')
            return response.url, response.read().decode('utf-8')

    url, html = request('/admin/posts')
    if urllib.parse.urlsplit(url).path == '/login':
        login = Page(html).fields
        login.update(username=input('管理员账号 [admin]: ').strip() or 'admin', password=getpass.getpass('管理员密码（不回显）: '))
        url, html = request('/login', login)
        if urllib.parse.urlsplit(url).path == '/login':
            raise RuntimeError('登录失败；未修改文章')

    # Snapshot every editable article before the first write, including drafts and version numbers.
    existing = {}
    page = 0
    while True:
        _, html = request('/admin/posts?page=' + str(page))
        document = Page(html)
        for link in document.links:
            if re.fullmatch(r'/admin/posts/\d+/edit', link):
                article_id = int(link.split('/')[3])
                if article_id not in existing:
                    _, edit = request(link)
                    fields = Page(edit).fields
                    if not all(k in fields for k in ('title', 'summary', 'content', 'status', 'version')):
                        raise RuntimeError('无法读取完整编辑表单；未开始写入')
                    existing[article_id] = {k: v for k, v in fields.items() if k != '_csrf'}
        if not any('page=' + str(page + 1) in x for x in document.links):
            break
        page += 1

    entries = json.loads((ROOT / 'content/essays/manifest.json').read_text())
    pending = []
    for entry in entries:
        content = (ROOT / 'content/essays' / entry['file']).read_text().strip()
        content += '\n\n---\n\n[微信公众号原文](' + entry['source'] + ')\n'
        matches = [x for x in existing.values() if x['title'] == entry['title']]
        if matches:
            if len(matches) != 1 or matches[0]['content'].strip() != content.strip():
                raise RuntimeError('同名文章内容不同，停止以避免覆盖：' + entry['title'])
            print('已存在，跳过：' + entry['title'])
        else:
            pending.append(dict(title=entry['title'], summary=entry['summary'], content=content,
                                status='PUBLISHED', changeNote='迁移经授权的公众号文章，保留原文来源'))
    sample_titles = {'你好，世界', 'Spring Boot 起步笔记', '为什么用 Thymeleaf'}
    archive = [i for i, x in existing.items() if x['status'] == 'PUBLISHED' and
               (x['title'] in sample_titles or i in args.archive_id)]
    if set(args.archive_id) - set(existing):
        raise RuntimeError('指定的测试文章 ID 不存在')
    print('待导入：' + '、'.join(x['title'] for x in pending))
    print('待转草稿：' + '、'.join(str(i) + ' ' + existing[i]['title'] for i in archive))
    if not args.apply:
        print('仅预览，未修改。确认后增加 --apply；数字标题等其他测试文章需显式 --archive-id ID。')
        return
    backup = ROOT / 'backups' / ('essays-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    backup.mkdir(parents=True, mode=0o700)
    destination = backup / 'articles.json'
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as output:
        json.dump(existing, output, ensure_ascii=False, indent=2)
    print('旧文章备份：' + str(destination))
    # Publish all new content before withdrawing samples. An interrupted run can be rerun safely.
    for fields in pending:
        _, html = request('/admin/posts/new')
        csrf = Page(html).fields.get('_csrf')
        if not csrf:
            raise RuntimeError('登录已失效或缺少 CSRF token')
        url, _ = request('/admin/posts', dict(fields, _csrf=csrf))
        if not re.fullmatch(r'/posts/\d+', urllib.parse.urlsplit(url).path):
            raise RuntimeError('发布未成功：' + fields['title'])
        print('已发布：' + fields['title'])
    for article_id in archive:
        _, html = request(f'/admin/posts/{article_id}/edit')
        current = Page(html).fields
        before = existing[article_id]
        if any(current.get(k) != v for k, v in before.items()):
            raise RuntimeError('文章发生变化，停止归档：' + str(article_id))
        current.update(status='DRAFT', changeNote='真实随笔上线，测试文章转草稿保留历史')
        url, _ = request(f'/admin/posts/{article_id}', current)
        if urllib.parse.urlsplit(url).path != '/admin/posts':
            raise RuntimeError('转草稿失败：' + str(article_id))
    print('完成。旧文章与历史保留在后台；公众号原始日期保留在正文，站内日期为导入时间。')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, urllib.error.URLError, OSError, ValueError) as error:
        raise SystemExit('导入停止：' + str(error))
