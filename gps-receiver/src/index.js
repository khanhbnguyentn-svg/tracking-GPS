'use strict';

const { readConfig } = require('./config');
const { createApp } = require('./app');

async function main() {
  const config = readConfig();
  const app = createApp(config);
  let stopping = false;
  const stop = async (signal) => {
    if (stopping) return;
    stopping = true;
    console.log(`[gps-receiver] stopping (${signal})`);
    try { await app.stop(); process.exitCode = 0; }
    catch (error) { console.error('[gps-receiver] shutdown failed:', error.message); process.exitCode = 1; }
  };
  process.on('SIGINT', () => stop('SIGINT'));
  process.on('SIGTERM', () => stop('SIGTERM'));
  await app.start();
  console.log(`[gps-receiver] listening on http://${config.host}:${config.port}`);
  console.log(`[gps-receiver] data directory: ${config.dataDir}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[gps-receiver] startup failed:', error.message);
    process.exitCode = 1;
  });
}

module.exports = { main };
