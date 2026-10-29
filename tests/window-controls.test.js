'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('window close remains wired while preference initialization is pending', async () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/renderer/main.js'), 'utf8');
  const initStart = source.indexOf('async function init()');
  const initEnd = source.indexOf('\nfunction applyTheme()', initStart);
  const controlsStart = source.indexOf('function bindWindowControls()');
  const controlsEnd = controlsStart >= 0 ? source.indexOf('\n}\n', controlsStart) + 3 : controlsStart;
  const listeners = new Map();
  const calls = [];
  const elements = new Map();
  const $ = (id) => {
    if (!elements.has(id)) elements.set(id, { addEventListener: (event, fn) => listeners.set(`${id}:${event}`, fn) });
    return elements.get(id);
  };
  const script = (controlsStart >= 0 ? source.slice(controlsStart, controlsEnd) : '') + source.slice(initStart, initEnd);
  const context = {
    $, api: { invoke: async (method) => { calls.push(method); if (method === 'os:info') return new Promise(() => {}); }, on() {} },
    state: {}, console,
  };
  vm.createContext(context);
  vm.runInContext(script, context);
  context.init();
  assert.ok(listeners.has('win-close:click'));
  await listeners.get('win-close:click')();
  assert.ok(calls.includes('win:close'));
});
