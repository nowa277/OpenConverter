'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const { toLrc } = require('../src/main/netease-lyric-parser');

test('nested json lines become lrc', () => {
  const inner = '{"t":0,"c":[{"tx":"Hello "}]}\n{"t":1230,"c":[{"tx":"World"}]}';
  const outer = JSON.stringify({ lrc: inner });
  assert.strictEqual(toLrc(Buffer.from(outer)), '[00:00.00]Hello \n[00:01.23]World');
});

test('already lrc passthrough', () => {
  const body = '[00:00.00]Hello\n[00:01.23]World';
  assert.strictEqual(toLrc(JSON.stringify({ lrc: body })), body);
});

test('raw lrc without wrapper', () => {
  assert.strictEqual(toLrc('[00:00.00]Hello'), '[00:00.00]Hello');
});

test('empty or garbage returns null', () => {
  assert.strictEqual(toLrc(Buffer.alloc(0)), null);
  assert.strictEqual(toLrc('{}'), null);
  assert.strictEqual(toLrc('not lyrics'), null);
});

test('lrc object lyric field becomes lrc', () => {
  assert.strictEqual(toLrc('{"lrc":{"lyric":"[00:00.00]Hello"}}'), '[00:00.00]Hello');
});

test('nested lrc object must not dump json', () => {
  const lrc = toLrc('{"lrc":{"version":1,"lyric":"[00:00.00]Hello"}}');
  assert.strictEqual(lrc, '[00:00.00]Hello');
  assert.ok(!lrc.includes('{'));
  assert.ok(!lrc.includes('"lyric"'));
});

test('tlyric is appended after lrc', () => {
  const input = JSON.stringify({
    lrc: { lyric: '[00:00.00]Hello' },
    tlyric: { lyric: '[00:00.00]你好' },
  });
  assert.strictEqual(toLrc(input), '[00:00.00]Hello\n[00:00.00]你好');
  const nested = JSON.stringify({
    lrc: { lyric: '[00:00.00]Hello', tlyric: '[00:00.00]你好' },
  });
  assert.strictEqual(toLrc(nested), '[00:00.00]Hello\n[00:00.00]你好');
});

test('nested parse keeps lines before a failure', () => {
  const inner = '{"t":0,"c":[{"tx":"Hello"}]}\nnot-json\n{"t":1000,"c":[{"tx":"World"}]}';
  assert.strictEqual(toLrc(inner), '[00:00.00]Hello');
});
