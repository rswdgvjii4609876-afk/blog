#!/usr/bin/env bash
# Run on the deployment server from its existing checkout.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
site_url="${1:-https://43.132.227.52}"
backup_dir="backups/pre-submit-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
container_id="$(docker compose ps -a -q app)"
if [[ -z "$container_id" ]]; then
    echo '未找到 app 容器，停止检查。' >&2
    exit 1
fi
# Always attempt to restore the service if the copy fails after stopping it.
resume_app() { docker compose start app; }
trap resume_app EXIT
docker compose stop app
docker cp "$container_id:/app/data" "$backup_dir/data"
test -n "$(find "$backup_dir/data" -name '*.mv.db' -type f -print -quit)"
# Configuration can contain credentials: private local backup only, never commit it.
config_files=()
for file in .env compose.yaml compose.override.yaml deploy/Caddyfile deploy/Caddyfile.ip; do
    [[ ! -f "$file" ]] || config_files+=("$file")
done
if (( ${#config_files[@]} )); then
    tar -czf "$backup_dir/deployment-config.tar.gz" "${config_files[@]}"
fi
git rev-parse HEAD > "$backup_dir/git-commit.txt"
docker compose start app
trap - EXIT
curl --fail --silent --show-error --retry 30 --retry-delay 2 --retry-connrefused \
    --max-time 10 "$site_url/healthz" > "$backup_dir/health.json"
python3 - "$site_url" "$backup_dir" <<'PY'
import json,re,sys,urllib.request
# Avoid Ubuntu apport masking the original check failure for stdin scripts.
sys.excepthook = sys.__excepthook__
from html import unescape
from pathlib import Path
base,backup=sys.argv[1].rstrip('/'),Path(sys.argv[2])
results=[]
def get(path):
    with urllib.request.urlopen(base+path,timeout=20) as r:
        return r.url,r.read().decode('utf-8')
health=json.loads((backup/'health.json').read_text())
assert health.get('status')=='UP','健康检查未返回 UP'
url,home=get('/')
assert '砒霜与蜜糖、美味与毒药' in home,'首页未发现最新随笔，请先执行公众号文章导入'
expected={entry['title']:entry['source'] for entry in json.loads(Path('content/essays/manifest.json').read_text())}
seen=set()
found=set()
legacy=[]
for page in range(2):
    _,html=get('/' if page==0 else '/?page=1')
    for path in re.findall(r'href="(/posts/\d+)"',html):
        if path in seen:
            continue
        seen.add(path)
        _,body=get(path)
        heading=re.search(r'<h1\b[^>]*>(.*?)</h1>',body,re.S)
        assert heading, '文章详情缺少标题：'+path
        title=unescape(re.sub(r'<[^>]+>','',heading.group(1))).strip()
        if title in expected:
            links=[unescape(x) for x in re.findall(r'href="([^"]+)"',body)]
            assert expected[title] in links, '迁移文章缺少对应来源链接：'+path+' '+title
            found.add(title)
        else:
            legacy.append(path+' '+title)
assert found==set(expected), '尚未核验全部六篇迁移文章：'+'、'.join(sorted(set(expected)-found))
for article in legacy:
    print('提示：另有旧文章仍公开（不属于公众号迁移文章）：'+article)
for title in ['你好，世界','Spring Boot 起步笔记','为什么用 Thymeleaf']:
    assert title not in home,'首页仍展示默认示例：'+title
_,identity=get('/identity')
assert '读者' in identity and ('作者' in identity or '管理员' in identity),'身份页内容异常'
url,_=get('/admin/overview')
assert '/login' in url,'未登录访问后台没有跳转登录'
results=['健康检查 UP','首页最新随笔可见','两页中的六篇文章详情及来源可访问','首页默认示例已移除',
         '身份切换入口可访问','未登录后台跳转登录','完整应用数据卷及部署配置已备份']
(backup/'verification.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
for result in results: print('通过：'+result)
print('备份及检查记录：'+str(backup))
print('仍需手机和电脑人工确认排版；评论/投票写入、管理员操作及备份恢复未在本脚本中测试。')
PY
docker compose ps
