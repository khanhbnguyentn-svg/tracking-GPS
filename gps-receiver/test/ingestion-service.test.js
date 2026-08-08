'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');

test('normalizes input, creates a stable dedupe key and waits for persistence', async () => {
  let release;
  const pending = new Promise((resolve) => { release = resolve; });
  const commands = [];
  const repository = { insert: async (command) => { commands.push(command); await pending; return { record: { id: '1' }, duplicate: false }; } };
  const { createIngestionService } = require('../src/modules/tracking/ingestion-service');
  const service = createIngestionService({ repository, clock: () => new Date('2026-08-08T00:00:01Z') });
  const raw = { id: 'and-a1b2c3d4e5f60718', lat: 10.8, lon: 106.7, timestamp: 1786147200 };

  let settled = false;
  const resultPromise = service.ingest(raw, { source: '127.0.0.1' }).then((value) => { settled = true; return value; });
  await new Promise(setImmediate);
  assert.equal(settled, false);
  release();
  const result = await resultPromise;

  assert.equal(result.deviceId, 'AND-A1B2C3D4E5F60718');
  assert.equal(result.receivedAt, '2026-08-08T00:00:01.000Z');
  assert.match(commands[0].dedupeKey, /^[a-f0-9]{64}$/);
  const repeated = await service.ingest(raw, { source: '127.0.0.1' });
  assert.equal(commands[1].dedupeKey, commands[0].dedupeKey);
  assert.equal(repeated.accepted, true);
});

test('maps validation and repository failures to public application errors', async () => {
  const { createIngestionService } = require('../src/modules/tracking/ingestion-service');
  const service = createIngestionService({
    repository: { insert: async () => { throw new Error('secret database detail'); } },
    clock: () => new Date('2026-08-08T00:00:01Z'),
  });

  await assert.rejects(service.ingest({ id: 'bad' }, {}), (error) => error.code === 'INVALID_FORMAT' && error.status === 400);
  await assert.rejects(service.ingest({ id: 'AND-A1B2C3D4E5F60718', lat: 10, lon: 106, timestamp: 1786147200 }, {}),
    (error) => error.code === 'PERSISTENCE_UNAVAILABLE' && error.status === 503 && !error.message.includes('secret'));
});
