'use strict';

function enhanceCode(root) {
    root.querySelectorAll('pre > code').forEach(code => {
        if (window.hljs && !code.dataset.highlighted) window.hljs.highlightElement(code);
        const pre = code.parentElement;
        if (pre.querySelector('button')) return;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'copy-code';
        button.textContent = '复制代码';
        button.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(code.textContent);
                button.textContent = '已复制';
            } catch { button.textContent = '请选中代码复制'; }
        });
        pre.append(button);
    });
}
enhanceCode(document);

const previewButton = document.getElementById('preview-button');
if (previewButton) {
    previewButton.addEventListener('click', async () => {
        const status = document.getElementById('preview-status');
        const preview = document.getElementById('markdown-preview');
        previewButton.disabled = true;
        status.textContent = '正在生成预览…';
        try {
            const response = await fetch(previewButton.dataset.url, {
                method: 'POST', headers: {'Content-Type': 'text/plain;charset=UTF-8'},
                body: document.getElementById('content').value
            });
            if (!response.ok) throw new Error('Preview failed');
            // HTML is returned only by the server's MarkdownService sanitization boundary.
            preview.innerHTML = await response.text();
            preview.hidden = false;
            previewButton.setAttribute('aria-expanded', 'true');
            enhanceCode(preview);
            status.textContent = '预览已更新，尚未保存文章。';
        } catch { status.textContent = '预览失败，请检查正文长度或稍后重试。内容仍保留在编辑框中。'; }
        finally { previewButton.disabled = false; }
    });
}
