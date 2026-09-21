const FEEDBACK_WIDTH = 88;
const FEEDBACK_MS = 1000;

const LANGUAGE = {
  zh: [
    { value: 'auto', label: '自动', title: '跟随系统' },
    { value: 'en', label: 'EN', title: 'English' },
    { value: 'zh', label: '中文', title: '简体中文' },
  ],
  en: [
    { value: 'auto', label: 'Auto', title: '跟随系统' },
    { value: 'en', label: 'EN', title: 'English' },
    { value: 'zh', label: '中', title: '简体中文' },
  ],
};

const THEME = {
  zh: [
    { value: 'system', label: '系统', title: '跟随系统' },
    { value: 'dark', label: '深', title: '深色' },
    { value: 'light', label: '浅', title: '浅色' },
  ],
  en: [
    { value: 'system', label: 'Sys', title: '跟随系统' },
    { value: 'dark', label: 'Dark', title: '深色' },
    { value: 'light', label: 'Light', title: '浅色' },
  ],
};

function segmentLabels(kind, uiLang) {
  const table = kind === 'theme' ? THEME : LANGUAGE;
  return table[uiLang] || table.en;
}

function feedbackText(kind, uiLang) {
  if (uiLang === 'zh') return kind === 'ok' ? '完成' : '失败';
  return kind === 'ok' ? 'Done' : 'Failed';
}

module.exports = { FEEDBACK_WIDTH, FEEDBACK_MS, segmentLabels, feedbackText };
