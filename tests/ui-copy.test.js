const test = require('node:test');
const assert = require('node:assert/strict');
const { segmentLabels, feedbackText, FEEDBACK_WIDTH, FEEDBACK_MS } = require('../src/shared/ui-copy');

test('chinese language abbreviations', () => {
  const rows = segmentLabels('language', 'zh');
  assert.deepEqual(rows.map((r) => r.label), ['自动', 'EN', '中文']);
  assert.deepEqual(rows.map((r) => r.value), ['auto', 'en', 'zh']);
  assert.equal(rows[0].title, '跟随系统');
  assert.equal(rows[1].title, 'English');
  assert.equal(rows[2].title, '简体中文');
});

test('english language abbreviations', () => {
  const rows = segmentLabels('language', 'en');
  assert.deepEqual(rows.map((r) => r.label), ['Auto', 'EN', '中']);
  assert.equal(rows[2].title, '简体中文');
});

test('theme abbreviations', () => {
  assert.deepEqual(segmentLabels('theme', 'zh').map((r) => r.label), ['系统', '深', '浅']);
  assert.deepEqual(segmentLabels('theme', 'en').map((r) => r.label), ['Sys', 'Dark', 'Light']);
  assert.equal(segmentLabels('theme', 'zh')[1].title, '深色');
  assert.equal(segmentLabels('theme', 'en')[0].title, '跟随系统');
});

test('feedback copy and constants', () => {
  assert.equal(FEEDBACK_WIDTH, 88);
  assert.equal(FEEDBACK_MS, 1000);
  assert.equal(feedbackText('ok', 'zh'), '完成');
  assert.equal(feedbackText('bad', 'zh'), '失败');
  assert.equal(feedbackText('ok', 'en'), 'Done');
  assert.equal(feedbackText('bad', 'en'), 'Failed');
});
