'use strict';
(() => {
    let theme = 'dark';
    try { theme = localStorage.getItem('zhixing-theme') || theme; } catch { /* Storage may be unavailable. */ }
    document.documentElement.dataset.theme = theme === 'light' ? 'light' : 'dark';
    document.addEventListener('DOMContentLoaded', () => {
        const nav = document.querySelector('.nav-links');
        if (!nav) return;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'theme-toggle';
        const update = () => {
            const dark = document.documentElement.dataset.theme === 'dark';
            button.textContent = dark ? '☀' : '☾';
            button.setAttribute('aria-label', dark ? '切换浅色模式' : '切换深色模式');
            button.title = dark ? '浅色模式' : '深色模式';
        };
        button.addEventListener('click', () => {
            const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = next;
            try { localStorage.setItem('zhixing-theme', next); } catch { /* Theme still changes in memory. */ }
            update();
        });
        update(); nav.append(button);
    });
})();
