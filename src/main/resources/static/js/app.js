const themeToggleEl = document.getElementById('themeToggle');

function reflectTheme(theme) {
  themeToggleEl.setAttribute('aria-pressed', String(theme === 'light'));
  themeToggleEl.setAttribute('aria-label', `Switch to ${theme === 'light' ? 'dark' : 'light'} theme`);
  if (window.__mermaidRetheme) window.__mermaidRetheme();
}

themeToggleEl.addEventListener('click', () => {
  const next = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
  document.documentElement.setAttribute('data-theme', next);
  localStorage.setItem('ca_theme', next);
  reflectTheme(next);
});

reflectTheme(document.documentElement.getAttribute('data-theme') || 'dark');

const emptyEl    = document.getElementById('empty');
const chatEl     = document.getElementById('chat');
const messagesEl = document.getElementById('messages');
const form       = document.getElementById('form');
const qEl        = document.getElementById('q');
const btnEl      = document.getElementById('btn');
const toastEl    = document.getElementById('toast');

const chartModalEl     = document.getElementById('chartModal');
const chartModalBodyEl = document.getElementById('chartModalBody');
const chartModalCloseEl = document.getElementById('chartModalClose');

function fillModalBody() {
  const svg = chartModalBodyEl.querySelector('svg');
  if (!svg) return;
  svg.removeAttribute('width');
  svg.removeAttribute('height');
  svg.style.width = '100%';
  svg.style.height = 'auto';
  svg.style.maxWidth = 'none';
}

async function openChartModal(chartDiv) {
  chartModalBodyEl.innerHTML = '';
  chartModalEl.classList.remove('hidden');

  const src = chartDiv.dataset.src;
  const mermaid = window.__mermaid;
  if (src && mermaid) {
    try {
      const sized = '%%{init: {"xyChart": {"width": 1100, "height": 620}}}%%\n' + src;
      const { svg } = await mermaid.render('mermaid-modal-' + Date.now(), sized);
      if (chartModalEl.classList.contains('hidden')) return;
      chartModalBodyEl.innerHTML = svg;
      fillModalBody();
      return;
    } catch {}
  }
  const existing = chartDiv.querySelector('svg');
  if (existing) {
    chartModalBodyEl.appendChild(existing.cloneNode(true));
    fillModalBody();
  }
}
function closeChartModal() {
  chartModalEl.classList.add('hidden');
  chartModalBodyEl.innerHTML = '';
}
chartModalCloseEl.addEventListener('click', closeChartModal);
chartModalEl.addEventListener('click', e => { if (e.target === chartModalEl) closeChartModal(); });
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && !chartModalEl.classList.contains('hidden')) closeChartModal();
});

const conversationId = (() => {
  const key = 'ca_conv_id';
  let id = sessionStorage.getItem(key);
  if (!id) { id = crypto.randomUUID(); sessionStorage.setItem(key, id); }
  return id;
})();

marked.use({ gfm: true, breaks: true });

DOMPurify.addHook('afterSanitizeAttributes', node => {
  if (node.tagName !== 'A' || !node.getAttribute('href')) return;
  node.setAttribute('target', '_blank');
  node.setAttribute('rel', 'noopener noreferrer');
  if (/\/api\/consumption\/report\//.test(node.getAttribute('href'))) {
    node.setAttribute('class', 'report-btn');
    node.setAttribute('download', '');
  }
});

function renderMd(raw) {
  const html = marked.parse(raw);
  return DOMPurify.sanitize(
    typeof html === 'string' ? html : '',
    {
      ALLOWED_TAGS: ['h1','h2','h3','h4','h5','h6','p','br','strong','em','del',
                     'code','pre','ul','ol','li','blockquote','hr','a',
                     'table','thead','tbody','tr','th','td','span'],
      ALLOWED_ATTR: ['href','class','target','rel']
    }
  );
}

function processEvent(event) {
  const parts = event
    .split('\n')
    .filter(l => l.startsWith('data:'))
    .map(l => {
      const raw = l.slice(5);
      try { const p = JSON.parse(raw); if (typeof p === 'string') return p; } catch {}
      return raw;
    });
  if (!parts.length) return null;
  const chunk = parts.join('\n');
  return (chunk === '[DONE]' || chunk === '') ? null : chunk;
}

const esc = t => t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const scrollBottom = () => { messagesEl.scrollTop = messagesEl.scrollHeight; };
const ts = () => new Date().toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' });

let chatVisible = false;
function ensureChat() {
  if (chatVisible) return;
  chatVisible = true;
  emptyEl.classList.add('hidden');
  chatEl.classList.remove('hidden');
}

let toastTimer;
function showToast() {
  clearTimeout(toastTimer);
  toastEl.classList.add('show');
  toastTimer = setTimeout(() => toastEl.classList.remove('show'), 1800);
}

async function renderMermaid(container) {
  const mermaid = window.__mermaid;
  if (!mermaid) return;
  const blocks = container.querySelectorAll('code.language-mermaid');
  let idx = 0;
  for (const block of blocks) {
    const source = block.textContent;
    const pre    = block.parentElement;
    const id     = 'mermaid-' + Date.now() + '-' + idx++;
    try {
      const { svg } = await mermaid.render(id, source);
      const div = document.createElement('div');
      div.className = 'mermaid-chart';
      div.innerHTML = svg;
      div.dataset.src = source;
      const badge = document.createElement('span');
      badge.className = 'chart-expand';
      badge.textContent = '⤢ Expand';
      div.appendChild(badge);
      div.addEventListener('click', () => openChartModal(div));
      pre.replaceWith(div);
    } catch (e) {
      const err = document.createElement('p');
      err.className = 'mermaid-error mono';
      err.textContent = 'Chart error: ' + e.message;
      pre.replaceWith(err);
    }
  }
}

function addUserBubble(text) {
  ensureChat();
  const el = document.createElement('div');
  el.className = 'msg-enter flex items-end justify-end gap-2';
  el.innerHTML = `
    <span class="ts text-[10px] mono mb-0.5">${ts()}</span>
    <div class="bubble-user rounded-2xl rounded-br-sm px-4 py-2.5 text-sm max-w-lg">
      ${esc(text)}
    </div>`;
  chatEl.appendChild(el);
  scrollBottom();
}

function addAgentBubble() {
  const wrap = document.createElement('div');
  wrap.className = 'msg-enter agent-row flex gap-3 items-start';
  wrap.innerHTML = `
    <div class="agent-avatar w-7 h-7 rounded-xl flex-shrink-0 mt-0.5 flex items-center justify-center overflow-hidden">
      <img src="images/logo.png" alt="" />
    </div>
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2 mb-1.5">
        <span class="agent-name text-[11px] font-semibold mono">Consumption Agent</span>
        <span class="ts text-[10px] mono">${ts()}</span>
        <button class="copy-btn ml-auto flex items-center gap-1 text-[10px] mono px-2 py-0.5 rounded-lg cursor-pointer"
                aria-label="Copy response to clipboard"
                onclick="copyMsg(this)">
          <svg style="width:11px;height:11px" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3"/>
          </svg>
          Copy
        </button>
      </div>
      <div class="bubble-agent rounded-2xl rounded-tl-sm px-4 py-3 overflow-x-auto">
        <div class="stream-body text-sm streaming" aria-live="polite" aria-busy="true">
          <div class="loading-row" aria-hidden="true">
            <div class="ai-dots">
              <div class="ai-dot"></div><div class="ai-dot"></div><div class="ai-dot"></div>
            </div>
            <span class="loading-text"></span>
          </div>
          <span class="sr-only">Consumption Agent is thinking…</span>
        </div>
      </div>
    </div>`;
  chatEl.appendChild(wrap);
  scrollBottom();

  const body = wrap.querySelector('.stream-body');
  const loadingTextEl = wrap.querySelector('.loading-text');
  const stopLoadingText = startLoadingText(loadingTextEl);
  let loadingStopped = false;

  function clearLoadingState() {
    if (loadingStopped) return;
    loadingStopped = true;
    stopLoadingText();
    const row = body.querySelector('.loading-row');
    if (row) row.remove();
    const sr = body.querySelector('.sr-only');
    if (sr) sr.remove();
  }

  let pendingRaw = null;
  let rafId = 0;
  function paint() {
    rafId = 0;
    clearLoadingState();
    body.classList.add('md');
    body.innerHTML = renderMd(pendingRaw);
    scrollBottom();
  }

  return {
    update(raw) {
      pendingRaw = raw;
      if (!rafId) rafId = requestAnimationFrame(paint);
    },
    finalize(raw) {
      if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }
      clearLoadingState();
      body.classList.remove('streaming');
      body.removeAttribute('aria-busy');
      if (raw) {
        body.classList.add('md');
        body.innerHTML = renderMd(raw);
      } else {
        body.innerHTML =
          '<span class="mono" style="color:var(--text-faint);font-style:italic;font-size:.75rem">No response.</span>';
      }
    },
    setError(msg, onRetry) {
      clearLoadingState();
      body.classList.remove('streaming');
      body.removeAttribute('aria-busy');
      body.innerHTML = `
        <div class="flex items-center gap-2 flex-wrap mono" style="color:var(--rose);font-size:.8rem">
          <span>Error: ${msg}</span>
          <button type="button" class="retry-btn px-2.5 py-1 rounded-lg text-[11px] font-medium cursor-pointer transition-all">
            Retry
          </button>
        </div>`;
      if (onRetry) {
        body.querySelector('.retry-btn').addEventListener('click', onRetry, { once: true });
      }
    },
    get el() { return body; }
  };
}

const LOADING_PHRASES = [
  'Reading meter data…',
  'Querying the consumption database…',
  'Crunching the numbers…',
  'Checking voltage and current readings…',
  'Looking for peak usage…',
  'Scanning for anomalies…',
  'Calculating averages…',
  'Reviewing the last few days…',
  'Lining up the data points…',
  'Putting the answer together…'
];

function pickPhrase(exclude) {
  if (LOADING_PHRASES.length === 1) return LOADING_PHRASES[0];
  let phrase;
  do { phrase = LOADING_PHRASES[Math.floor(Math.random() * LOADING_PHRASES.length)]; }
  while (phrase === exclude);
  return phrase;
}

function startLoadingText(el) {
  let current = pickPhrase();
  el.textContent = current;
  const id = setInterval(() => {
    current = pickPhrase(current);
    el.classList.remove('loading-text');
    void el.offsetWidth;
    el.textContent = current;
    el.classList.add('loading-text');
  }, 1900);
  return () => clearInterval(id);
}

function copyMsg(btn) {
  const body = btn.closest('.flex-1').querySelector('.stream-body');
  if (!body) return;
  navigator.clipboard.writeText(body.innerText).then(showToast);
}

function setLoading(on) {
  btnEl.disabled = on;
  qEl.disabled   = on;
  document.querySelectorAll('.s-card').forEach(c => {
    c.disabled = on;
    c.classList.toggle('opacity-40', on);
    c.classList.toggle('pointer-events-none', on);
  });
}

async function ask(question) {
  addUserBubble(question);
  setLoading(true);

  const bubble = addAgentBubble();
  let accumulated = '';

  try {
    const res = await fetch('/api/consumption/ask/stream', {
      method : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body   : JSON.stringify({ question, conversationId })
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const reader  = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buf += decoder.decode(value, { stream: true });
      const events = buf.split('\n\n');
      buf = events.pop();

      for (const event of events) {
        const chunk = processEvent(event);
        if (chunk === null) continue;
        accumulated += chunk;
      }
      if (accumulated) bubble.update(accumulated);
    }

    if (buf) {
      const chunk = processEvent(buf);
      if (chunk !== null) accumulated += chunk;
    }

    bubble.finalize(accumulated);

  } catch (err) {
    bubble.setError(esc(err.message), () => ask(question));
  } finally {
    await renderMermaid(bubble.el);
    scrollBottom();
    setLoading(false);
    qEl.focus();
  }
}

form.addEventListener('submit', e => {
  e.preventDefault();
  const q = qEl.value.trim();
  if (!q) return;
  qEl.value = '';
  ask(q);
});

document.querySelectorAll('.s-card').forEach(c =>
  c.addEventListener('click', () => ask(c.dataset.q))
);

qEl.focus();
