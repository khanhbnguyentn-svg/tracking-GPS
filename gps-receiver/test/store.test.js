'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { createStore } = require('../src/store');

const location = {
  deviceId: 'AND-0123456789ABCDEF',
  latitude: 10.1,
  longitude: 106.1,
  deviceTime: '2026-08-07T17:24:16.000Z',
  speedKnots: 3.2,
  accuracyMeters: 8,
};

async function tempDir(t) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'gps-store-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  return dir;
}

test('appends daily JSONL and exposes latest device and stats', async (t) => {
  const dir = await tempDir(t);
  const now = () => new Date('2026-08-07T18:00:00.000Z').getTime();
  const store = createStore({ dataDir: dir, now, retentionDays: 30, inactivityMs: 300000 });
  await store.init();
  await store.append(location, { source: '127.0.0.1' });
  await store.flush();

  const lines = (await fs.readFile(path.join(dir, 'locations-2026-08-07.jsonl'), 'utf8')).trim().split('\n');
  assert.equal(lines.length, 1);
  assert.equal(JSON.parse(lines[0]).deviceId, location.deviceId);
  assert.equal(store.devices()[0].status, 'active');
  assert.deepEqual(store.stats(), { total: 1, accepted: 1, rejected: 0, devices: 1, recentPerSecond: 1 });
});

test('does not update state when persistence fails', async (t) => {
  const dir = await tempDir(t);
  const store = createStore({
    dataDir: dir,
    now: () => Date.now(),
    io: { appendFile: async () => { throw new Error('disk full'); } },
  });
  await store.init();
  await assert.rejects(store.append(location, { source: 'local' }), /disk full/);
  assert.equal(store.devices().length, 0);
  assert.equal(store.health().writable, false);
});

test('reloads the latest-device snapshot', async (t) => {
  const dir = await tempDir(t);
  const now = () => new Date('2026-08-07T18:00:00.000Z').getTime();
  const first = createStore({ dataDir: dir, now, snapshotDelayMs: 0 });
  await first.init();
  await first.append(location, { source: 'local' });
  await first.close();

  const second = createStore({ dataDir: dir, now });
  await second.init();
  assert.equal(second.devices()[0].deviceId, location.deviceId);
  await second.close();
});

test('quarantines a corrupt snapshot and starts empty', async (t) => {
  const dir = await tempDir(t);
  await fs.writeFile(path.join(dir, 'latest-devices.json'), '{broken');
  const store = createStore({ dataDir: dir });
  await store.init();
  assert.equal(store.devices().length, 0);
  const names = await fs.readdir(dir);
  assert.ok(names.some((name) => /^latest-devices\.json\.corrupt-\d+$/.test(name)));
});

test('retention deletes only old matching daily files', async (t) => {
  const dir = await tempDir(t);
  await Promise.all([
    fs.writeFile(path.join(dir, 'locations-2026-07-01.jsonl'), 'old'),
    fs.writeFile(path.join(dir, 'locations-2026-08-07.jsonl'), 'new'),
    fs.writeFile(path.join(dir, 'important.jsonl'), 'keep'),
  ]);
  const store = createStore({
    dataDir: dir,
    now: () => new Date('2026-08-07T18:00:00.000Z').getTime(),
    retentionDays: 30,
  });
  await store.init();
  await store.applyRetention();
  assert.equal(await fs.stat(path.join(dir, 'locations-2026-07-01.jsonl')).then(() => true, () => false), false);
  assert.equal(await fs.stat(path.join(dir, 'locations-2026-08-07.jsonl')).then(() => true, () => false), true);
  assert.equal(await fs.stat(path.join(dir, 'important.jsonl')).then(() => true, () => false), true);
});
