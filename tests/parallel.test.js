const test = require('node:test');
const assert = require('node:assert/strict');

async function parallelLimit(limit, tasks) {
  const executing = [];
  const results = [];
  for (const task of tasks) {
    const p = Promise.resolve().then(() => task());
    results.push(p);
    const e = p.then(() => executing.splice(executing.indexOf(e), 1));
    executing.push(e);
    if (executing.length >= limit) {
      await Promise.race(executing);
    }
  }
  return Promise.all(results);
}

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
});
