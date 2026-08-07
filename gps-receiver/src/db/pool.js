'use strict';

const { Pool } = require('pg');

function createPool(config) {
  return new Pool({
    connectionString: config.databaseUrl,
    application_name: 'internal-gps-receiver',
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
  });
}

async function closePool(pool) {
  if (pool) await pool.end();
}

module.exports = { createPool, closePool };
