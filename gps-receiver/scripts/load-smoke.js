'use strict';

const path = require('node:path');
const { createPool, closePool } = require('../src/db/pool');
const { migrate } = require('../src/db/migrator');
const { createPositionRepository } = require('../src/modules/tracking/position-repository');
const { createApp } = require('../src/app');

function argument(name, fallback) {
  const index = process.argv.indexOf(name);
  return index < 0 ? fallback : Number(process.argv[index + 1]);
}

async function main() {
  const databaseUrl = process.env.GPS_TEST_DATABASE_URL;
  const parsed = new URL(databaseUrl);
  const database = parsed.pathname.slice(1);
  if (!database.startsWith('fleet_test_')) throw new Error('Load smoke requires a fleet_test_* database.');
  const durationSeconds = argument('--duration-seconds', 600);
  const rate = argument('--rate', 7);
  if (!Number.isInteger(durationSeconds) || durationSeconds < 1 || !Number.isInteger(rate) || rate < 1) {
    throw new Error('Duration and rate must be positive integers.');
  }

  const pool = createPool({ databaseUrl });
  let app;
  const latencies = [];
  const memoryStart = process.memoryUsage().rss;
  const started = Date.now();
  let sent = 0;
  let before;
  let after;
  try {
    await migrate(pool, path.join(__dirname, '../src/db/migrations'));
    const repository = createPositionRepository(pool);
    before = Number((await pool.query('SELECT count(*) FROM gps_positions')).rows[0].count);
    app = createApp({ host: '127.0.0.1', port: 0, repository, rateLimit: 100000 });
    await app.start();
    const base = `http://127.0.0.1:${app.server.address().port}`;
    for (let second = 0; second < durationSeconds; second += 1) {
      const batchStarted = Date.now();
      const requests = Array.from({ length: rate }, async (_, offset) => {
        const sequence = second * rate + offset;
        const device = `AND-${(sequence % 100).toString(16).toUpperCase().padStart(16, '0')}`;
        const requestStarted = performance.now();
        const response = await fetch(`${base}/?${new URLSearchParams({
          id: device, lat: '10.7769', lon: '106.7009', timestamp: String(started + sequence), accuracy: '8',
        })}`);
        latencies.push(performance.now() - requestStarted);
        if (response.status !== 200) throw new Error(`Request failed with HTTP ${response.status}.`);
      });
      await Promise.all(requests);
      sent += rate;
      const remaining = 1000 - (Date.now() - batchStarted);
      if (remaining > 0) await new Promise((resolve) => setTimeout(resolve, remaining));
    }
    after = Number((await pool.query('SELECT count(*) FROM gps_positions')).rows[0].count);
  } finally {
    if (app) await app.stop();
    await closePool(pool);
  }
  const memoryEnd = process.memoryUsage().rss;
  latencies.sort((left, right) => left - right);
  const p95 = latencies[Math.ceil(latencies.length * 0.95) - 1];
  console.log(JSON.stringify({ sent, persisted: after - before, p95Ms: Number(p95.toFixed(2)), memoryStart, memoryEnd }));
  if (after - before !== sent) throw new Error(`Expected ${sent} new rows, found ${after - before}.`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
