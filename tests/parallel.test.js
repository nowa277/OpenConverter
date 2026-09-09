const test = require('node:test');
const assert = require('node:assert/strict');

const { parallelLimit } = require('../src/main/pipeline');

test('parallelLimit concurrency control', async (t) => {
  await t.test('respects concurrency limit', async () => {
    let active = 0;
    let maxActive = 0;

    const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    const tasks = Array.from({ length: 5 }, (_, i) => async () => {
      active++;
      maxActive = Math.max(maxActive, active);
      await delay(50);
      active--;
      return i;
    });

    const results = await parallelLimit(2, tasks);
    assert.deepEqual(results, [0, 1, 2, 3, 4]);
    assert.equal(maxActive, 2);
  });

  await t.test('handles limit larger than task count', async () => {
    let active = 0;
    let maxActive = 0;

    const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    const tasks = Array.from({ length: 3 }, (_, i) => async () => {
      active++;
      maxActive = Math.max(maxActive, active);
      await delay(50);
      active--;
      return i;
    });

    const results = await parallelLimit(5, tasks);
    assert.deepEqual(results, [0, 1, 2]);
    assert.equal(maxActive, 3);
  });

  await t.test('a rejected task does not stall the queue', async () => {
    const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    let ran = 0;
    const tasks = [
      async () => { await delay(10); throw new Error('boom'); },
      async () => { await delay(10); ran++; return 'a'; },
      async () => { await delay(10); ran++; return 'b'; },
      async () => { await delay(10); ran++; return 'c'; },
    ];
    await assert.rejects(parallelLimit(1, tasks), /boom/);
    // Give the remaining tasks time to be scheduled and complete.
    await delay(100);
    assert.equal(ran, 3);
  });
});
