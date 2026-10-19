'use strict';

// Progressive enhancement: the initial two numbered fields also work without JavaScript.
const pollEditor = document.getElementById('poll-editor');
if (pollEditor) {
    const rows = document.getElementById('poll-options');
    const add = document.getElementById('add-poll-option');
    const duration = document.getElementById('poll-duration');
    const custom = document.getElementById('poll-custom-hours');
    const renumber = () => {
        [...rows.children].forEach((row, index) => {
            const input = row.querySelector('input');
            input.id = `poll-option-${index + 1}`;
            row.querySelector('label').htmlFor = input.id;
            row.querySelector('label').textContent = `选项 ${index + 1}`;
            const remove = row.querySelector('button');
            remove.hidden = false;
            remove.disabled = rows.children.length <= 2;
            remove.setAttribute('aria-label', `删除选项 ${index + 1}`);
        });
        add.disabled = rows.children.length >= 6;
    };
    add.hidden = false;
    add.addEventListener('click', () => {
        if (rows.children.length >= 6) return;
        const row = rows.firstElementChild.cloneNode(true);
        row.querySelector('input').value = '';
        rows.append(row);
        renumber();
        row.querySelector('input').focus();
    });
    rows.addEventListener('click', event => {
        const button = event.target.closest('.remove-poll-option');
        if (!button || rows.children.length <= 2) return;
        const row = button.closest('.poll-edit-row');
        const next = row.nextElementSibling || row.previousElementSibling;
        row.remove();
        renumber();
        next.querySelector('input').focus();
    });
    const updateDuration = () => {
        const active = duration.value === 'custom';
        custom.disabled = !active;
        custom.required = active;
    };
    duration.addEventListener('change', updateDuration);
    updateDuration();
    renumber();
}

for (const card of document.querySelectorAll('.poll-card')) {
    const form = card.querySelector('.poll-vote-form');
    const other = card.querySelector('.poll-other-input');
    const otherText = other?.querySelector('textarea');
    const synchronizeOther = () => {
        if (!other) return;
        const selected = form.querySelector('input[name="option"]:checked')?.value === '6';
        other.hidden = !selected || form.querySelector('fieldset').disabled;
        otherText.required = selected && !form.querySelector('fieldset').disabled;
    };
    form.addEventListener('change', synchronizeOther);
    synchronizeOther();
    const timer = card.querySelector('[data-poll-timer]');
    if (!timer) continue;
    let deadline = timer.dataset.endsAt ? Date.parse(timer.dataset.endsAt) : null;
    let anchor = Date.parse(timer.dataset.serverNow);
    let started = performance.now();
    let open = timer.dataset.pollOpen === 'true';
    let fetching = false;
    const button = form.querySelector('button[type="submit"]');
    const openLabel = form.querySelector('input[name="option"]:checked') ? '修改选择' : '参与投票';
    let countdown = timer.querySelector('.poll-countdown');
    const setOpen = value => {
        form.querySelector('fieldset').disabled = !value;
        button.disabled = !value;
        button.textContent = value ? openLabel : '投票已结束';
        timer.querySelector('.poll-status').textContent = value ? (deadline === null ? '进行中 · 作者手动结束' : '进行中') : '已结束';
        synchronizeOther();
    };
    const paintResults = state => {
        for (const option of state.results) {
            const label = [...form.querySelectorAll('.poll-option')].find(node => node.querySelector('input').value === String(option.index));
            if (!label) continue;
            let count = label.querySelector('.poll-count');
            if (!count) { count = document.createElement('span'); count.className = 'poll-count'; label.append(count); }
            count.textContent = `${option.votes} 票`;
        }
        if (state.open && state.votes === null) {
            card.querySelectorAll('.poll-count, .poll-total').forEach(node => node.remove());
        }
        if (state.votes !== null) {
            let total = card.querySelector('.poll-total');
            if (!total) { total = document.createElement('p'); total.className = 'poll-total'; card.append(total); }
            total.textContent = `共 ${state.votes} 份投票`;
        }
    };
    const refresh = async () => {
        if (fetching) return;
        fetching = true;
        try {
            const response = await fetch(timer.dataset.statusUrl, {headers: {Accept: 'application/json'}, cache: 'no-store'});
            if (!response.ok || response.redirected) return;
            const state = await response.json();
            deadline = state.endsAt ? Date.parse(state.endsAt) : null;
            anchor = Date.parse(state.serverNow);
            started = performance.now();
            open = state.open;
            timer.dataset.endsAt = state.endsAt || '';
            const date = timer.querySelector('time');
            if (date && state.endsAt) {
                date.dateTime = state.endsAt;
                date.textContent = new Intl.DateTimeFormat('zh-CN', {timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}).format(new Date(state.endsAt));
            }
            setOpen(open);
            if (!open && countdown) countdown.textContent = '';
            paintResults(state);
        } catch { /* Keep controls disabled after local expiry; server remains authoritative. */ }
        finally { fetching = false; }
    };
    const tick = () => {
        if (!open || deadline === null) return;
        const remaining = deadline - (anchor + performance.now() - started);
        if (remaining <= 0) {
            open = false;
            setOpen(false);
            if (countdown) countdown.textContent = '';
            refresh();
            return;
        }
        if (!countdown) { countdown = document.createElement('span'); countdown.className = 'poll-countdown'; timer.append(countdown); }
        const seconds = Math.ceil(remaining / 1000);
        const days = Math.floor(seconds / 86400);
        const hours = Math.floor(seconds / 3600) % 24;
        const minutes = Math.floor(seconds / 60) % 60;
        countdown.textContent = `剩余 ${days ? days + '天 ' : ''}${hours}小时 ${minutes}分 ${seconds % 60}秒`;
    };
    tick();
    setInterval(tick, 1000);
    setInterval(() => { if (!document.hidden) refresh(); }, 30000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
}
