'use strict';

const assert = require('node:assert/strict');
const { after, before, beforeEach, test } = require('node:test');
const { Pool } = require('pg');

const databaseUrl = process.env.GPS_TEST_DATABASE_URL;
const enabled = databaseUrl && /^fleet_test_/.test(new URL(databaseUrl).pathname.slice(1));
const integrationTest = enabled ? test : test.skip;
let pool;
let repository;

before(async () => {
  if (!enabled) return;
  pool = new Pool({ connectionString: databaseUrl });
  ({ createPositionRepository: repository } = require('../src/modules/tracking/position-repository'));
  repository = repository(pool);
});

beforeEach(async () => {
  if (!enabled) return;
  await pool.query('TRUNCATE gps_positions, assignments, tracking_devices RESTART IDENTITY CASCADE');
});

after(async () => {
  if (pool) await pool.end();
});

integrationTest('stores a position once with normalized device and PostGIS coordinates', async () => {
  const command = sampleCommand();
  const first = await repository.insert(command);
  const second = await repository.insert(command);

  assert.equal(first.duplicate, false);
  assert.equal(second.duplicate, true);
  assert.equal(second.record.id, first.record.id);
  assert.equal(first.record.assignmentId, null);

  const result = await pool.query(`
    SELECT d.device_id, ST_X(p.location::geometry) AS lon,
           ST_Y(p.location::geometry) AS lat, count(*) OVER ()::int AS rows
    FROM gps_positions p JOIN tracking_devices d ON d.id = p.tracking_device_id
  `);
  assert.deepEqual(result.rows[0], {
    device_id: 'AND-A1B2C3D4E5F60718', lon: 106.7, lat: 10.8, rows: 1,
  });
});

integrationTest('rolls back the unknown device when the position insert fails', async () => {
  await assert.rejects(repository.insert({ ...sampleCommand(), source: null }));
  const result = await pool.query('SELECT count(*)::int AS count FROM tracking_devices');
  assert.equal(result.rows[0].count, 0);
});

function sampleCommand() {
  return {
    deviceId: 'and-a1b2c3d4e5f60718',
    latitude: 10.8,
    longitude: 106.7,
    deviceTime: new Date('2026-08-08T00:00:00.000Z'),
    receivedAt: new Date('2026-08-08T00:00:01.000Z'),
    speedKnots: 12.5,
    accuracyMeters: 4,
    source: 'android',
    dedupeKey: 'test-dedupe-key',
  };
}
