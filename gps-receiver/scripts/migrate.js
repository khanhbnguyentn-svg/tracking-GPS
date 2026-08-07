'use strict';

const path = require('node:path');
const { loadConfig } = require('../src/core/config');
const { createPool, closePool } = require('../src/db/pool');
const { migrate } = require('../src/db/migrator');

async function main() {
  const rootDir = path.resolve(__dirname, '..');
  const config = loadConfig(process.env, rootDir);
  const pool = createPool(config);
  try {
    const applied = await migrate(pool, path.join(rootDir, 'src', 'db', 'migrations'));
    for (const name of applied) process.stdout.write(`${name}\n`);
  } finally {
    await closePool(pool);
  }
}

main().catch((error) => {
  process.stderr.write(`Database migration failed: ${error.message}\n`);
  process.exitCode = 1;
});
