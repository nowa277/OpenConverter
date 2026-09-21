import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import RubberSegment from './RubberSegment.jsx';
import SquishSwitch from './SquishSwitch.jsx';
import copy from '../../shared/ui-copy.js';
import './controls.css';

const FORMAT = [
  { value: 'mp3', label: 'MP3' },
  { value: 'flac', label: 'FLAC' },
  { value: 'wav', label: 'WAV' },
  { value: 'm4a', label: 'M4A' },
  { value: 'ogg', label: 'OGG' },
];
const QUALITY = [
  { value: '320k', label: '320k' },
  { value: '256k', label: '256k' },
  { value: '192k', label: '192k' },
  { value: '128k', label: '128k' },
];

const roots = new Map();

function writeSelect(id, value) {
  const select = document.getElementById(id);
  if (!select || select.value === value) return;
  select.value = value;
  select.dispatchEvent(new Event('change', { bubbles: true }));
}

function writeCheck(id, checked) {
  const input = document.getElementById(id);
  if (!input || input.checked === checked) return;
  input.checked = checked;
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

function useSelect(id) {
  const [value, setValue] = useState(() => document.getElementById(id)?.value || '');
  useEffect(() => {
    const select = document.getElementById(id);
    if (!select) return undefined;
    const sync = () => setValue(select.value);
    select.addEventListener('change', sync);
    sync();
    return () => select.removeEventListener('change', sync);
  }, [id]);
  return value;
}

function useCheck(id) {
  const [checked, setChecked] = useState(() => !!document.getElementById(id)?.checked);
  useEffect(() => {
    const input = document.getElementById(id);
    if (!input) return undefined;
    const sync = () => setChecked(input.checked);
    input.addEventListener('change', sync);
    sync();
    return () => input.removeEventListener('change', sync);
  }, [id]);
  return checked;
}

const segmentColors = {
  trackColor: 'var(--bg-elevated)',
  thumbColor: 'var(--accent)',
  textColor: 'var(--text-secondary)',
  activeTextColor: '#121212',
  size: 'md',
  radius: 999,
  equalSlots: false,
};

function useDisabled(selectId) {
  const [disabled, setDisabled] = useState(() => !!document.getElementById(selectId)?.disabled);
  useEffect(() => {
    const select = document.getElementById(selectId);
    if (!select) return undefined;
    const obs = new MutationObserver(() => setDisabled(select.disabled));
    obs.observe(select, { attributes: true, attributeFilter: ['disabled'] });
    setDisabled(select.disabled);
    return () => obs.disconnect();
  }, [selectId]);
  return disabled;
}

function SegmentIsland({ selectId, items, ariaLabel }) {
  const value = useSelect(selectId);
  const disabled = useDisabled(selectId);
  return (
    <RubberSegment
      {...segmentColors}
      aria-label={ariaLabel}
      items={items}
      value={value}
      disabled={!!disabled}
      onChange={(next) => writeSelect(selectId, next)}
    />
  );
}

function SwitchIsland({ inputId, label }) {
  const checked = useCheck(inputId);
  return (
    <SquishSwitch
      id={`${inputId}-switch`}
      checked={checked}
      ariaLabel={label}
      label=""
      width={52}
      height={28}
      trackColor="#282828"
      trackOnColor="#1ed760"
      thumbColor="#ffffff"
      thumbOnColor="#121212"
      onChange={(next) => writeCheck(inputId, next)}
    />
  );
}

function mount(id, node) {
  const host = document.getElementById(id);
  if (!host) return;
  let root = roots.get(id);
  if (!root) {
    root = createRoot(host);
    roots.set(id, root);
  }
  root.render(node);
}

export function mountIslands() {
  const ui = document.documentElement.lang === 'zh' ? 'zh' : 'en';
  mount('format-island', <SegmentIsland selectId="format-select" items={FORMAT} ariaLabel="format" />);
  mount('quality-island', <SegmentIsland selectId="quality-select" items={QUALITY} ariaLabel="quality" />);
  mount('language-island', <SegmentIsland selectId="language-select" items={copy.segmentLabels('language', ui)} ariaLabel="language" />);
  mount('theme-island', <SegmentIsland selectId="theme-select" items={copy.segmentLabels('theme', ui)} ariaLabel="theme" />);
  mount('lyrics-island', <SwitchIsland inputId="ncm-lyrics-enabled" label="lyrics" />);
  mount('kgg-autoscan-island', <SwitchIsland inputId="kgg-autoscan-checkbox" label="kugou autoscan" />);
}

export function remountAppearanceLabels() {
  const ui = document.documentElement.lang === 'zh' ? 'zh' : 'en';
  mount('language-island', <SegmentIsland selectId="language-select" items={copy.segmentLabels('language', ui)} ariaLabel="language" />);
  mount('theme-island', <SegmentIsland selectId="theme-select" items={copy.segmentLabels('theme', ui)} ariaLabel="theme" />);
}
