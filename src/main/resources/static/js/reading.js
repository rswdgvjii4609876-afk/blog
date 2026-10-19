'use strict';
(() => {
    const article = document.querySelector('.post-article[data-reading]');
    if (!article) return;
    const progress = document.getElementById('reading-progress');
    const label = document.getElementById('reading-percent');
    const links = [...document.querySelectorAll('.article-toc a')];
    const sections = links.map(link => document.getElementById(link.hash.slice(1))).filter(Boolean);
    let pending = false;
    const update = () => {
        const end = Math.max(1, article.offsetTop + article.offsetHeight - window.innerHeight);
        const fitsOnScreen = document.documentElement.scrollHeight <= window.innerHeight;
        const percent = fitsOnScreen ? 100 : Math.max(0, Math.min(100, Math.round(window.scrollY / end * 100)));
        progress.value = percent;
        label.textContent = percent + '%';
        let active = sections[0];
        for (const heading of sections) if (heading.getBoundingClientRect().top <= 160) active = heading;
        links.forEach(link => {
            if (active && link.hash === '#' + active.id) link.setAttribute('aria-current', 'location');
            else link.removeAttribute('aria-current');
        });
        pending = false;
    };
    window.addEventListener('scroll', () => {
        if (!pending) { pending = true; requestAnimationFrame(update); }
    }, {passive: true});
    window.addEventListener('resize', update);
    update();
})();
