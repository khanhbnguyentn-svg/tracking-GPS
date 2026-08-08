'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { after, before, beforeEach, test } = require('node:test');
const { Pool } = require('pg');

const databaseUrl = process.env.GPS_TEST_DATABASE_URL;
const enabled = databaseUrl && /^fleet_test_/.test(new URL(databaseUrl).pathname.slice(1));
const integrationTest = enabled ? test : test.skip;
const excluded = new Set([
  'AND-0123456789ABCDEF', 'AND-FEDCBA9876543210', 'AND-A1B2C3D4E5F60718',
]);
let pool;
let repository;

before(() => {
  if (!enabled) return;
  pool = new Pool({ connectionString: databaseUrl });
  repository = require('../src/modules/tracking/position-repository').createPositionRepository(pool);
});

beforeEach(async () => {
  if (enabled) await pool.query('TRUNCATE gps_positions, assignments, tracking_devices RESTART IDENTITY CASCADE');
});

after(async () => { if (pool) await pool.end(); });

integrationTest('streams JSONL, excludes smoke IDs and preserves the source file', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'fleet-import-'));
  const file = path.join(directory, 'locations.jsonl');
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const valid = legacy('AND-1111111111111111', 21.1, 105.8, '2026-08-08T01:00:00Z');
  const lines = [
    valid,
    ...[...excluded].map((id) => legacy(id, 21.2, 105.9, '2026-08-08T01:01:00Z')),
    valid,
    '{bad json',
    legacy('AND-2222222222222222', 21.3, 106, '2026-08-08T01:02:00Z'),
  ];
  await fs.writeFile(file, `${lines.join('\n')}\n`, 'utf8');
  const { importJsonl } = require('../src/modules/tracking/jsonl-importer');

  const summary = await importJsonl({ paths: [file], repository, excludedDeviceIds: excluded });

  assert.deepEqual(summary, { files: 1, lines: 7, imported: 2, duplicates: 1, excluded: 3, invalid: 1, failed: 0 });
  assert.equal((await pool.query('SELECT count(*)::int AS count FROM gps_positions')).rows[0].count, 2);
  assert.equal((await fs.stat(file)).isFile(), true);
});

function legacy(deviceId, latitude, longitude, deviceTime) {
  return JSON.stringify({
    deviceId, latitude, longitude, deviceTime,
    receivedAt: new Date(new Date(deviceTime).getTime() + 1000).toISOString(),
    speedKnots: 4, accuracyMeters: 8,
  });
}
