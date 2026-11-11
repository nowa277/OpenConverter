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

test('decodes html entities in lyrics', () => {
  const input = '[00:01.00]Tom &amp; Jerry &quot;Show&quot; &#39;Special&#39; &lt;Live&gt;';
  assert.strictEqual(toLrc(input), '[00:01.00]Tom & Jerry "Show" \'Special\' <Live>');
});

test('returns null for pure music placeholder lyrics', () => {
  assert.strictEqual(toLrc(JSON.stringify({ nolyric: true })), null);
  assert.strictEqual(toLrc(JSON.stringify({ lrc: { lyric: '[00:00.00]纯音乐，请欣赏' } })), null);
  assert.strictEqual(toLrc(JSON.stringify({ lrc: { lyric: '[00:00.00]本歌曲为纯音乐，无歌词\n[00:01.00]作词 : 纯音乐' } })), null);
  assert.strictEqual(toLrc(JSON.stringify({ lrc: { lyric: '[00:00.00]该歌曲为纯音乐，请欣赏' } })), null);
  assert.strictEqual(toLrc(JSON.stringify({ lrc: { lyric: '[00:00.00]暂无歌词' } })), null);
});

test('merges bilingual lyrics by timestamp in chronological order', () => {
  const input = JSON.stringify({
    lrc: { lyric: '[00:01.00]First line\n[00:05.00]Second line' },
    tlyric: { lyric: '[00:01.00]第一行\n[00:05.00]第二行' },
  });
  assert.strictEqual(toLrc(input), '[00:01.00]First line\n[00:01.00]第一行\n[00:05.00]Second line\n[00:05.00]第二行');
});

test('bilingual merges with varying timestamp precision and metadata headers', () => {
  const input = JSON.stringify({
    lrc: { lyric: '[ti:Test Title]\n[ar:Artist]\n[00:01.5]First\n[00:04.120]Second' },
    tlyric: { lyric: '[00:01.50]第一\n[00:04.12]第二' },
  });
  const expected = '[ti:Test Title]\n[ar:Artist]\n[00:01.5]First\n[00:01.50]第一\n[00:04.120]Second\n[00:04.12]第二';
  assert.strictEqual(toLrc(input), expected);
});

test('decodes hex and decimal html entities', () => {
  const input = '[00:01.00]&#65;&#66;&#67; &#x44;&#x45;&#x46; &apos;apostrophe&apos;';
  assert.strictEqual(toLrc(input), "[00:01.00]ABC DEF 'apostrophe'");
});

test('out of order lines are sorted chronologically', () => {
  const input = JSON.stringify({
    lrc: { lyric: '[00:05.00]Second\n[00:01.00]First' },
    tlyric: { lyric: '[00:05.00]第二\n[00:01.00]第一' },
  });
  const expected = '[00:01.00]First\n[00:01.00]第一\n[00:05.00]Second\n[00:05.00]第二';
  assert.strictEqual(toLrc(input), expected);
});


