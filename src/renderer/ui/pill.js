import { gsap } from 'gsap';

export function mountPills() {
  const buttons = document.querySelectorAll('.nav-item');
  const timelines = [];
  buttons.forEach((pill, index) => {
    const circle = document.createElement('span');
    circle.className = 'pill-circle';
    const hover = document.createElement('span');
    hover.className = 'pill-label-hover';
    const face = pill.querySelector('span[data-i18n]') || pill.querySelector('span');
    hover.textContent = face ? face.textContent : '';
    pill.append(circle, hover);
    timelines[index] = null;
  });

  const layout = () => {
    buttons.forEach((pill, index) => {
      const circle = pill.querySelector('.pill-circle');
      const label = pill.querySelector('span[data-i18n]');
      const hover = pill.querySelector('.pill-label-hover');
      const rect = pill.getBoundingClientRect();
      const w = rect.width;
      const h = rect.height || 40;
      const R = ((w * w) / 4 + h * h) / (2 * h);
      const D = Math.ceil(2 * R) + 2;
      const delta = Math.ceil(R - Math.sqrt(Math.max(0, R * R - (w * w) / 4))) + 1;
      const originY = D - delta;
      circle.style.width = `${D}px`;
      circle.style.height = `${D}px`;
      circle.style.bottom = `-${delta}px`;
      gsap.set(circle, { xPercent: -50, scale: 0, transformOrigin: `50% ${originY}px` });
      if (label) gsap.set(label, { y: 0 });
      const tl = gsap.timeline({ paused: true });
      tl.to(circle, { scale: 1.2, xPercent: -50, duration: 2, ease: 'power3.easeOut' }, 0);
      if (label) tl.to(label, { y: -(h + 8), duration: 2, ease: 'power3.easeOut' }, 0);
      if (hover) {
        gsap.set(hover, { y: Math.ceil(h + 100), opacity: 0 });
        tl.to(hover, { y: 0, opacity: 1, duration: 2, ease: 'power3.easeOut' }, 0);
      }
      timelines[index] = tl;
    });
  };
  layout();
  window.addEventListener('resize', layout);

  buttons.forEach((pill, index) => {
    pill.addEventListener('mouseenter', () => {
      if (pill.classList.contains('active')) return;
      timelines[index]?.tweenTo(timelines[index].duration(), { duration: 0.3, ease: 'power3.easeOut' });
    });
    pill.addEventListener('mouseleave', () => {
      timelines[index]?.tweenTo(0, { duration: 0.2, ease: 'power3.easeOut' });
    });
  });
}

export function refreshPillLabels() {
  document.querySelectorAll('.nav-item').forEach((pill) => {
    const face = pill.querySelector('span[data-i18n]');
    const hover = pill.querySelector('.pill-label-hover');
    if (face && hover) hover.textContent = face.textContent;
  });
}
