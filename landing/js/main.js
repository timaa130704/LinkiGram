/* ============ LinkiGram landing — interactions ============ */

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ---------- Хедер при скролле ---------- */
function initHeader() {
  const header = document.querySelector(".header");
  if (!header) return;
  const onScroll = () => header.classList.toggle("scrolled", window.scrollY > 30);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
}

/* ---------- 3D tilt карточек ---------- */
function initTilt() {
  if (reduceMotion || window.matchMedia("(hover: none)").matches) return;
  document.querySelectorAll(".tilt").forEach((el) => {
    const MAX = 7;
    el.addEventListener("pointermove", (e) => {
      const r = el.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width;
      const py = (e.clientY - r.top) / r.height;
      el.style.transform =
        `perspective(900px) rotateY(${(px - 0.5) * MAX * 2}deg) rotateX(${(0.5 - py) * MAX * 2}deg)`;
      el.style.setProperty("--mx", `${px * 100}%`);
      el.style.setProperty("--my", `${py * 100}%`);
    });
    el.addEventListener("pointerleave", () => {
      el.style.transform = "perspective(900px) rotateY(0deg) rotateX(0deg)";
    });
  });
}

/* ---------- Появление при скролле ---------- */
function initReveal() {
  const els = document.querySelectorAll("[data-reveal]");
  if (!("IntersectionObserver" in window)) {
    els.forEach((el) => el.classList.add("visible"));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    entries.forEach((entry, i) => {
      if (entry.isIntersecting) {
        entry.target.style.transitionDelay = `${(i % 6) * 70}ms`;
        entry.target.classList.add("visible");
        io.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
  els.forEach((el) => io.observe(el));
}

/* ---------- Мобильное меню ---------- */
function initNav() {
  const burger = document.querySelector(".burger");
  const nav = document.querySelector(".nav");
  if (!burger || !nav) return;
  const setOpen = (open) => {
    nav.classList.toggle("open", open);
    burger.setAttribute("aria-expanded", String(open));
    document.body.style.overflow = open ? "hidden" : "";
  };
  burger.addEventListener("click", () => setOpen(!nav.classList.contains("open")));
  nav.querySelectorAll("a").forEach((a) => a.addEventListener("click", () => setOpen(false)));
  window.addEventListener("keydown", (e) => { if (e.key === "Escape") setOpen(false); });
}

/* ---------- Печать команды в терминале ---------- */
function initTypeCmd() {
  document.querySelectorAll("[data-cmd]").forEach((el) => {
    const text = el.dataset.cmd || "";
    const term = el.closest(".term");
    if (reduceMotion || !text) {
      el.textContent = text;
      if (term) term.classList.remove("typing");
      return;
    }
    if (term) term.classList.add("typing");
    let i = 0;
    const tick = () => {
      i += 1;
      el.textContent = text.slice(0, i);
      if (i < text.length) {
        setTimeout(tick, 22 + Math.random() * 46);
      } else if (term) {
        setTimeout(() => term.classList.remove("typing"), 600);
      }
    };
    setTimeout(tick, 500);
  });
}

/* ---------- Переключение темы ---------- */
function initThemeToggle() {
  const btn = document.querySelector("[data-theme-toggle]");
  if (!btn) return;
  const label = document.querySelector("[data-theme-label]");
  const meta = document.querySelector('meta[name="theme-color"]');
  const ORDER = ["gray", "purple", "red", "yellow"];

  const apply = (theme) => {
    const root = document.documentElement;
    root.setAttribute("data-theme", theme);
    if (label) label.textContent = theme;
    btn.setAttribute("aria-label", `Сменить тему (сейчас ${theme})`);
    if (meta) {
      const bg = getComputedStyle(root).getPropertyValue("--bg").trim();
      if (bg) meta.setAttribute("content", bg);
    }
    try {
      localStorage.setItem("lg-theme", theme);
    } catch (e) {
      /* приватный режим — просто не сохраняем */
    }
  };

  const current = () => document.documentElement.getAttribute("data-theme") || "gray";

  // Синхронизируем подпись с темой, могшей прийти из localStorage до отрисовки
  apply(current());

  btn.addEventListener("click", () => {
    const i = ORDER.indexOf(current());
    apply(ORDER[(i + 1) % ORDER.length]);
  });
}

/* ---------- Старт ---------- */
initHeader();
initNav();
initTilt();
initReveal();
initTypeCmd();
initThemeToggle();


// A quiet rain layer, with a bounded particle count and no background work.
(() => {
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const canvas = document.createElement('canvas');
  canvas.className = 'ambient-rain';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.prepend(canvas);
  const ctx = canvas.getContext('2d');
  if (!ctx) { canvas.remove(); return; }
  let width = 0, height = 0, drops = [], frame = 0, previous = 0;
  let color = '#b9c1cd';
  let textEdges = [], splashes = [];
  const collectText = () => {
    textEdges = [];
    document.querySelectorAll('h1, h2, h3, .hero__subtitle, .proxy__text > p').forEach(element => {
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        for (let i = 0; i < node.length; i++) {
          if (!node.textContent[i].trim()) continue;
          const range = document.createRange();
          range.setStart(node, i); range.setEnd(node, i + 1);
          const rect = range.getBoundingClientRect();
          if (rect.bottom > 0 && rect.top < innerHeight && rect.width > 0) {
            textEdges.push({left: rect.left, right: rect.right, top: rect.top + rect.height * .22, element});
          }
        }
      }
    });
  };
  let textUpdate = 0;
  const scheduleText = () => {
    if (textUpdate) return;
    textUpdate = requestAnimationFrame(() => { textUpdate = 0; collectText(); });
  };
  addEventListener('scroll', scheduleText, {passive: true});
  if (document.fonts) document.fonts.ready.then(collectText);
  setTimeout(collectText, 1400);
  const refreshColor = () => {
    color = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#b9c1cd';
  };
  const resize = () => {
    width = innerWidth; height = innerHeight;
    const ratio = Math.min(devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    collectText();
    drops = Array.from({length: Math.min(110, Math.max(30, Math.round(width / 16)))}, () => ({
      x: Math.random() * (width + 100), y: Math.random() * height,
      speed: 260 + Math.random() * 330, length: 12 + Math.random() * 25,
      alpha: .15 + Math.random() * .4
    }));
  };
  const draw = now => {
    const dt = previous ? Math.min((now - previous) / 1000, .04) : 0;
    previous = now;
    ctx.clearRect(0, 0, width, height);
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    for (const drop of drops) {
      const oldY = drop.y;
      drop.y += drop.speed * dt;
      drop.x -= drop.speed * dt * .16;
      if (drop.y > height + drop.length || drop.x < -40) {
        drop.y = -drop.length; drop.x = Math.random() * (width + 100);
      }
      const hit = textEdges.find(edge => oldY < edge.top && drop.y >= edge.top && drop.x >= edge.left && drop.x <= edge.right);
      if (hit) {
        for (let i = 0; i < 4 && splashes.length < 160; i++) {
          splashes.push({x: drop.x, y: hit.top, vx: (Math.random() - .5) * 110, vy: -35 - Math.random() * 65, life: .3 + Math.random() * .2});
        }
        hit.element.classList.remove('rain-wet');
        void hit.element.offsetWidth;
        hit.element.classList.add('rain-wet');
        drop.y = -drop.length; drop.x = Math.random() * (width + 100);
      }
      ctx.globalAlpha = drop.alpha;
      ctx.beginPath(); ctx.moveTo(drop.x, drop.y);
      ctx.lineTo(drop.x + drop.length * .16, drop.y - drop.length); ctx.stroke();
    }
    for (let i = splashes.length - 1; i >= 0; i--) {
      const splash = splashes[i];
      splash.life -= dt;
      if (splash.life <= 0) { splashes.splice(i, 1); continue; }
      splash.x += splash.vx * dt; splash.y += splash.vy * dt; splash.vy += 240 * dt;
      ctx.globalAlpha = Math.min(1, splash.life * 3);
      ctx.beginPath(); ctx.arc(splash.x, splash.y, 1.1, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill();
    }
    frame = requestAnimationFrame(draw);
  };
  const sync = () => {
    cancelAnimationFrame(frame); previous = 0;
    if (!document.hidden && !motion.matches) frame = requestAnimationFrame(draw);
    else ctx.clearRect(0, 0, width, height);
  };
  new MutationObserver(refreshColor).observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
  addEventListener('resize', resize, {passive: true});
  document.addEventListener('visibilitychange', sync);
  motion.addEventListener('change', sync);
  refreshColor(); resize(); sync();
})();
