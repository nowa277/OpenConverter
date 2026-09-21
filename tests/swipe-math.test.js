const test = require('node:test');
const assert = require('node:assert/strict');
const { rubberOffset, snapTarget, shouldCollapse } = require('../src/shared/swipe-math');

test('rubber resists past the action', () => {
  const raw = rubberOffset(-200, 88);
  assert.ok(raw < 0);
  assert.ok(raw > -200);
});

test('snap chooses the nearer rest', () => {
  assert.equal(snapTarget(-10, 88), 0);
  assert.equal(snapTarget(-70, 88), -88);
});

test('full swipe collapses', () => {
  assert.equal(shouldCollapse(-300, 320), true);
  assert.equal(shouldCollapse(-40, 320), false);
});
