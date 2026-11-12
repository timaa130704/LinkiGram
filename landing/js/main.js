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

/* ---------- Уведомление о смене канала ---------- */
function initChannelNotice() {
  const notice = document.getElementById("channel-notice");
  if (!notice) return;
  const KEY = "lg-channel-notice-2.9";
  const WEEK = 7 * 24 * 60 * 60 * 1000;

  let dismissed = 0;
  try {
    dismissed = parseInt(localStorage.getItem(KEY) || "0", 10) || 0;
  } catch (e) {
    /* приватный режим — показываем всегда */
  }
  if (dismissed && Date.now() - dismissed < WEEK) return;

  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    notice.hidden = true;
    document.body.classList.remove("notice-open");
    try {
      localStorage.setItem(KEY, String(Date.now()));
    } catch (e) {
      /* не критично */
    }
    document.removeEventListener("keydown", onKey);
  };
  const onKey = (e) => {
    if (e.key === "Escape") close();
  };

  notice.hidden = false;
  document.body.classList.add("notice-open");
  notice.querySelectorAll("[data-notice-close]").forEach((el) => {
    el.addEventListener("click", close);
  });
  document.addEventListener("keydown", onKey);
  const primary = notice.querySelector(".notice__actions .btn--primary");
  if (primary) primary.focus({ preventScroll: true });
}

/* ---------- Старт ---------- */
initHeader();
initNav();
initTilt();
initReveal();
initTypeCmd();
initThemeToggle();
initChannelNotice();


// A quiet snowfall, with a bounded flake count and no background work.
(() => {
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const canvas = document.createElement('canvas');
  canvas.className = 'ambient-snow';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.prepend(canvas);
  const ctx = canvas.getContext('2d');
  if (!ctx) { canvas.remove(); return; }
  let width = 0, height = 0, flakes = [], frame = 0, previous = 0, clock = 0;
  let tint = '#e8eef6';
  const refreshColor = () => {
    const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-bright').trim();
    tint = accent || '#e8eef6';
  };
  const makeFlake = (spawnOnTop) => {
    const radius = 0.8 + Math.random() * 2.4;
    return {
      x: Math.random() * (width + 120) - 60,
      y: spawnOnTop ? -radius * 4 : Math.random() * height,
      r: radius,
      speed: 14 + radius * 11 + Math.random() * 16,
      drift: 5 + Math.random() * 18,
      phase: Math.random() * Math.PI * 2,
      spin: 0.4 + Math.random() * 1.1,
      alpha: 0.3 + Math.random() * 0.55
    };
  };
  const resize = () => {
    width = innerWidth; height = innerHeight;
    const ratio = Math.min(devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    refreshColor();
    const count = Math.min(150, Math.max(40, Math.round(width / 11)));
    flakes = Array.from({length: count}, () => makeFlake(false));
  };
  const draw = now => {
    const dt = previous ? Math.min((now - previous) / 1000, .04) : 0;
    previous = now;
    clock += dt;
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = tint;
    for (const flake of flakes) {
      flake.y += flake.speed * dt;
      flake.x += Math.cos(clock * flake.spin + flake.phase) * flake.drift * dt - 6 * dt;
      if (flake.y > height + flake.r * 4) {
        Object.assign(flake, makeFlake(true));
      } else if (flake.x < -70) {
        flake.x = width + 60;
      }
      ctx.globalAlpha = flake.alpha;
      ctx.beginPath();
      ctx.arc(flake.x, flake.y, flake.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
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
