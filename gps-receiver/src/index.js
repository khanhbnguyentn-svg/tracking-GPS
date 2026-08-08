'use strict';

const path = require('node:path');
const { readConfig } = require('./config');
const { createApp } = require('./app');
const { createPool, closePool } = require('./db/pool');
const { createPositionRepository } = require('./modules/tracking/position-repository');
const { createAuthRepository } = require('./modules/auth/auth-repository');
const { createAuthService } = require('./modules/auth/auth-service');
const { migrate } = require('./db/migrator');

function withTimeout(promise, milliseconds) {
  let timer;
  const timeout = new Promise((resolve, reject) => {
    timer = setTimeout(() => reject(new Error('HTTP shutdown timed out.')), milliseconds);
    timer.unref?.();
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

async function startRuntime({
  pool, app, processRef = process, logger = console, migrateFn = migrate,
  closePoolFn = closePool, migrationsDir = path.join(__dirname, 'db', 'migrations'), shutdownTimeoutMs = 15000,
}) {
  try {
    await migrateFn(pool, migrationsDir);
    await app.start();
  } catch (error) {
    try { await closePoolFn(pool); } catch {}
    throw error;
  }

  let stoppingPromise = null;
  const stop = (signal) => {
    if (stoppingPromise) return stoppingPromise;
    stoppingPromise = (async () => {
      logger.log(`[gps-receiver] stopping (${signal})`);
      let failure = null;
      try {
        await withTimeout(app.stop(), shutdownTimeoutMs);
      } catch (error) {
        failure = error;
        app.forceStop?.();
      }
      try { await closePoolFn(pool); }
      catch (error) { failure ||= error; }
      if (failure) {
        logger.error('[gps-receiver] shutdown failed:', failure.message);
        processRef.exitCode = 1;
      } else {
        processRef.exitCode = 0;
      }
      processRef.removeListener('SIGINT', onSigint);
      processRef.removeListener('SIGTERM', onSigterm);
    })();
    return stoppingPromise;
  };
  const onSigint = () => stop('SIGINT');
  const onSigterm = () => stop('SIGTERM');
  processRef.on('SIGINT', onSigint);
  processRef.on('SIGTERM', onSigterm);
  return { stop, stopped: () => stoppingPromise || Promise.resolve() };
}

async function main() {
  const config = readConfig();
  const pool = createPool(config);
  const repository = createPositionRepository(pool, { inactivityMs: config.inactivityMs });
  const authService = createAuthService({ repository: createAuthRepository(pool) });
  const app = createApp({ ...config, repository, authService });
  await startRuntime({ pool, app });
  console.log(`[gps-receiver] listening on http://${config.host}:${config.port}`);
  console.log('[gps-receiver] PostgreSQL persistence enabled');
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[gps-receiver] startup failed:', error.message);
    process.exitCode = 1;
  });
}

module.exports = { main, startRuntime };
