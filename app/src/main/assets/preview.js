/* Markdown preview: markdown-it + footnotes, sup/sub, KaTeX math, task lists,
   Foam wikilinks / embeds and Mermaid diagrams. `window.Android` is the native bridge. */
(function () {
  'use strict';
  const stub = {
    resolve: () => '', readNote: () => '', openWikilink() {}, openLink() {}, toggleTask() {}
  };
  // Looked up on each call: the desktop app adds window.Android after the page has loaded.
  const bridge = () => window.Android || stub;
  const IMAGE = /\.(png|jpe?g|gif|svg|webp|bmp|avif)$/i;
  let embedDepth = 0;

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function repoUrl(path) {
    return '/repo/' + path.split('/').map(encodeURIComponent).join('/');
  }

  // [[target]], [[target|alias]], [[target#section]] and ![[embed]]
  function wikilinks(md) {
    function rule(embed) {
      return function (state, silent) {
        const start = state.pos;
        const open = embed ? '![[' : '[[';
        if (!state.src.startsWith(open, start)) return false;
        const close = state.src.indexOf(']]', start + open.length);
        if (close < 0) return false;
        const inner = state.src.slice(start + open.length, close);
        if (!inner.trim() || inner.includes('\n') || inner.includes('[')) return false;
        if (!silent) {
          const tok = state.push(embed ? 'wikiembed' : 'wikilink', '', 0);
          const pipe = inner.indexOf('|');
          tok.meta = {
            target: (pipe >= 0 ? inner.slice(0, pipe) : inner).trim(),
            alias: pipe >= 0 ? inner.slice(pipe + 1).trim() : null
          };
        }
        state.pos = close + 2;
        return true;
      };
    }
    md.inline.ruler.before('link', 'wikilink', rule(false));
    md.inline.ruler.before('image', 'wikiembed', rule(true));

    md.renderer.rules.wikilink = function (tokens, idx) {
      const { target, alias } = tokens[idx].meta;
      const resolved = target.startsWith('#') ? 'self' : bridge().resolve(target);
      const cls = 'wikilink' + (resolved ? '' : ' missing');
      return '<a class="' + cls + '" href="#" data-target="' + escapeHtml(target) + '">' +
        escapeHtml(alias || target) + '</a>';
    };
    md.renderer.rules.wikiembed = function (tokens, idx, options, env) {
      const { target, alias } = tokens[idx].meta;
      const path = bridge().resolve(target);
      if (path && IMAGE.test(path)) {
        return '<img src="' + repoUrl(path) + '" alt="' + escapeHtml(alias || target) + '">';
      }
      if (!path || embedDepth >= 2) {
        return '<a class="wikilink' + (path ? '' : ' missing') + '" href="#" data-target="' +
          escapeHtml(target) + '">' + escapeHtml(alias || target) + '</a>';
      }
      embedDepth++;
      try {
        const html = md.render(stripFrontMatter(bridge().readNote(path)), { embedded: true });
        return '<div class="embed"><div class="embed-title"><a class="wikilink" href="#" data-target="' +
          escapeHtml(target) + '">' + escapeHtml(target) + '</a></div>' + html + '</div>';
      } finally {
        embedDepth--;
      }
    };
  }

  // - [ ] / - [x] task lists. Each checkbox carries its 0-based source line.
  function taskLists(md) {
    md.core.ruler.after('inline', 'task-lists', function (state) {
      const tokens = state.tokens;
      for (let i = 2; i < tokens.length; i++) {
        const tok = tokens[i];
        if (tok.type !== 'inline' || tokens[i - 1].type !== 'paragraph_open' ||
            tokens[i - 2].type !== 'list_item_open') continue;
        const m = /^\[([ xX])\](?:\s|$)/.exec(tok.content);
        if (!m || !tok.children.length || tok.children[0].type !== 'text') continue;
        const first = tok.children[0];
        first.content = first.content.replace(/^\[[ xX]\]\s?/, '');
        const line = tokens[i - 1].map ? tokens[i - 1].map[0] : -1;
        const box = new state.Token('html_inline', '', 0);
        box.content = '<input type="checkbox" class="task" data-line="' + line + '"' +
          (m[1] !== ' ' ? ' checked' : '') + (state.env.embedded ? ' disabled' : '') + '>';
        tok.children.unshift(box);
        tokens[i - 2].attrJoin('class', 'task-list-item');
      }
    });
  }

  // ```mermaid fences become diagrams.
  function mermaidFences(md) {
    const fallback = md.renderer.rules.fence;
    md.renderer.rules.fence = function (tokens, idx, options, env, self) {
      const tok = tokens[idx];
      if (tok.info.trim().split(/\s+/)[0] === 'mermaid') {
        return '<pre class="mermaid">' + escapeHtml(tok.content) + '</pre>';
      }
      return fallback(tokens, idx, options, env, self);
    };
  }

  // Replace YAML front matter with blank lines so source line numbers stay aligned.
  function stripFrontMatter(src) {
    const m = /^---\r?\n[\s\S]*?\r?\n---[ \t]*(\r?\n|$)/.exec(src);
    if (!m) return src;
    return m[0].replace(/[^\n]/g, '') + src.slice(m[0].length);
  }

  const md = window.markdownit({ html: true, linkify: true })
    .use(window.markdownitFootnote)
    .use(window.markdownitSup)
    .use(window.markdownitSub)
    .use(texmath, { engine: katex, delimiters: ['dollars', 'brackets'], katexOptions: { throwOnError: false } })
    .use(taskLists)
    .use(wikilinks)
    .use(mermaidFences);

  let mermaidTheme = null;
  const content = document.getElementById('content');

  window.render = function (src, baseDir, dark) {
    document.documentElement.classList.toggle('dark', !!dark);
    document.getElementById('base').href = repoUrl(baseDir || '').replace(/\/?$/, '/');
    const y = window.scrollY;
    let html;
    try {
      html = md.render(stripFrontMatter(src || ''), {});
    } catch (e) {
      html = '<pre>' + escapeHtml(String(e && e.stack || e)) + '</pre>';
    }
    content.innerHTML = html.trim() ? html : '<p class="empty">Empty note</p>';
    window.scrollTo(0, y);
    const nodes = content.querySelectorAll('pre.mermaid');
    if (nodes.length && window.mermaid) {
      const theme = dark ? 'dark' : 'default';
      if (theme !== mermaidTheme) {
        mermaid.initialize({ startOnLoad: false, theme: theme, securityLevel: 'strict' });
        mermaidTheme = theme;
      }
      mermaid.run({ nodes: nodes, suppressErrors: false }).catch(err => console.error(err));
    }
  };

  document.addEventListener('click', function (e) {
    const box = e.target.closest('input.task');
    if (box) {
      e.preventDefault();
      if (!box.disabled) bridge().toggleTask(Number(box.dataset.line));
      return;
    }
    const a = e.target.closest('a');
    if (!a) return;
    e.preventDefault();
    if (a.classList.contains('wikilink')) {
      const t = a.dataset.target;
      if (t.startsWith('#')) scrollToHeading(t.slice(1)); else bridge().openWikilink(t);
      return;
    }
    const href = a.getAttribute('href') || '';
    if (href.startsWith('#')) {
      const el = document.getElementById(decodeURIComponent(href.slice(1)));
      if (el) el.scrollIntoView({ behavior: 'smooth' });
      return;
    }
    bridge().openLink(a.href);
  });

  function scrollToHeading(text) {
    const want = text.trim().toLowerCase();
    const h = Array.from(content.querySelectorAll('h1,h2,h3,h4,h5,h6'))
      .find(el => el.textContent.trim().toLowerCase() === want);
    if (h) h.scrollIntoView({ behavior: 'smooth' });
  }

  if (window.Android && window.Android.ready) window.Android.ready();
})();
