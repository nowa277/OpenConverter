const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const HistoryStore = require('../src/main/history');

test('HistoryStore tests', async (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'openconverter-test-'));
  const store = new HistoryStore(tmpDir, 5);

  t.after(() => {
    try {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    } catch {}
  });

  await t.test('Initial state is empty', async () => {
    const records = await store.readAll();
    assert.deepEqual(records, []);
  });

  await t.test('Appends and reads records in reverse order (newest first)', async () => {
    const r1 = { ts: 100, inputName: '1.ncm', targetFormat: 'mp3', status: 'success', outputName: '1.mp3', error: null };
    const r2 = { ts: 200, inputName: '2.ncm', targetFormat: 'flac', status: 'failed', outputName: null, error: 'failed' };

    await store.append(r1);
    await store.append(r2);

    const records = await store.readAll();
    assert.strictEqual(records.length, 2);
    assert.deepEqual(records[0], r2);
    assert.deepEqual(records[1], r1);
  });

  await t.test('Trims records to max entries limit (5)', async () => {
    for (let i = 3; i <= 8; i++) {
      await store.append({
        ts: i * 100,
        inputName: `${i}.ncm`,
        targetFormat: 'mp3',
        status: 'success',
        outputName: `${i}.mp3`,
        error: null
      });
    }

    const records = await store.readAll();
    assert.strictEqual(records.length, 5);
    // Newest first, max entries 5. Total added: r1 (100), r2 (200), 3 (300), 4 (400), 5 (500), 6 (600), 7 (700), 8 (800).
    // The last 5 entries added are 4, 5, 6, 7, 8.
    // Reversed (newest first): 8, 7, 6, 5, 4.
    assert.strictEqual(records[0].inputName, '8.ncm');
    assert.strictEqual(records[4].inputName, '4.ncm');
  });

  await t.test('Clears all history records', async () => {
    await store.clear();
    const records = await store.readAll();
    assert.deepEqual(records, []);
  });
});
