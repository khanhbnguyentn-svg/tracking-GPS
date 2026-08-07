'use strict';

const { readConfig } = require('../src/config');
const { createPool, closePool } = require('../src/db/pool');
const { createPositionRepository } = require('../src/modules/tracking/position-repository');
const { importJsonl } = require('../src/modules/tracking/jsonl-importer');

const EXCLUDED_DEVICE_IDS = new Set([
  'AND-0123456789ABCDEF', 'AND-FEDCBA9876543210', 'AND-A1B2C3D4E5F60718',
]);

async function main(args = process.argv.slice(2)) {
  const dryRun = args.includes('--dry-run');
  const paths = args.filter((value) => value !== '--dry-run');
  if (!paths.length) throw new Error('Provide at least one JSONL file path.');
  const config = readConfig();
  const pool = createPool(config);
  try {
    const summary = await importJsonl({
      paths,
      repository: createPositionRepository(pool, { inactivityMs: config.inactivityMs }),
      excludedDeviceIds: EXCLUDED_DEVICE_IDS,
      dryRun,
    });
    console.log(JSON.stringify(summary));
  } finally {
    await closePool(pool);
  }
}

if (require.main === module) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}

module.exports = { main, EXCLUDED_DEVICE_IDS };
