'use strict';

const { readConfig } = require('./config');
const { createApp } = require('./app');
const { createPool, closePool } = require('./db/pool');
const { createPositionRepository } = require('./modules/tracking/position-repository');

async function main() {
  const config = readConfig();
  const pool = createPool(config);
  const repository = createPositionRepository(pool, { inactivityMs: config.inactivityMs });
  const app = createApp({ ...config, repository });
  let stopping = false;
  const stop = async (signal) => {
    if (stopping) return;
    stopping = true;
    console.log(`[gps-receiver] stopping (${signal})`);
    try { await app.stop(); await closePool(pool); process.exitCode = 0; }
    catch (error) { console.error('[gps-receiver] shutdown failed:', error.message); process.exitCode = 1; }
  };
  process.on('SIGINT', () => stop('SIGINT'));
  process.on('SIGTERM', () => stop('SIGTERM'));
  try { await app.start(); }
  catch (error) { await closePool(pool); throw error; }
  console.log(`[gps-receiver] listening on http://${config.host}:${config.port}`);
  console.log('[gps-receiver] PostgreSQL persistence enabled');
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[gps-receiver] startup failed:', error.message);
    process.exitCode = 1;
  });
}

module.exports = { main };
