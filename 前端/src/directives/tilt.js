/**
 * 指针跟随：把鼠标在卡片里的相对位置写成 CSS 变量。
 * 倾斜角度和光晕位置都由 CSS 消费，这里只负责算坐标。
 *
 * 关掉：删掉这个文件，再撤掉 main.js 里的注册和模板上的 v-tilt。
 */

const MAX_DEG = 6;

const prefersReduced = () =>
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export const tilt = {
  mounted (el) {
    if (prefersReduced()) return;

    let raf = 0;
    let px = 0.5;
    let py = 0.5;
    let active = false;

    const paint = () => {
      raf = 0;
      if (!active) return;
      el.style.setProperty('--tilt-x', ((0.5 - py) * MAX_DEG).toFixed(2) + 'deg');
      el.style.setProperty('--tilt-y', ((px - 0.5) * MAX_DEG).toFixed(2) + 'deg');
      el.style.setProperty('--glow-x', (px * 100).toFixed(1) + '%');
      el.style.setProperty('--glow-y', (py * 100).toFixed(1) + '%');
    };

    const onMove = (e) => {
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) return;
      px = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
      py = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
      active = true;
      if (!raf) raf = requestAnimationFrame(paint);
    };

    const onLeave = () => {
      active = false;
      if (raf) {
        cancelAnimationFrame(raf);
        raf = 0;
      }
      el.style.removeProperty('--tilt-x');
      el.style.removeProperty('--tilt-y');
      el.style.removeProperty('--glow-x');
      el.style.removeProperty('--glow-y');
    };

    el.addEventListener('pointermove', onMove);
    el.addEventListener('pointerleave', onLeave);

    el.__tiltDispose = () => {
      el.removeEventListener('pointermove', onMove);
      el.removeEventListener('pointerleave', onLeave);
      if (raf) cancelAnimationFrame(raf);
      raf = 0;
    };
  },

  unmounted (el) {
    el.__tiltDispose?.();
    delete el.__tiltDispose;
  }
};
