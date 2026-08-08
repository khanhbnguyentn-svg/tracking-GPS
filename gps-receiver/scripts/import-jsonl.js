'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const { readConfig } = require('../src/config');
const { createPool, closePool } = require('../src/db/pool');
const { createPositionRepository } = require('../src/modules/tracking/position-repository');
const { importJsonl } = require('../src/modules/tracking/jsonl-importer');

const EXCLUDED_DEVICE_IDS = new Set([
  'AND-0123456789ABCDEF', 'AND-FEDCBA9876543210', 'AND-A1B2C3D4E5F60718',
]);

async function main(args = process.argv.slice(2)) {
  const { source, dryRun } = parseArgs(args);
  const directory = path.resolve(source);
  const paths = (await fs.readdir(directory))
    .filter((name) => name.toLowerCase().endsWith('.jsonl'))
    .sort()
    .map((name) => path.join(directory, name));
  if (!paths.length) throw new Error('Source directory contains no JSONL files.');
  const config = readConfig();
  const pool = createPool(config);
  try {
    const summary = await importJsonl({
      paths,
      repository: createPositionRepository(pool, { inactivityMs: config.inactivityMs }),
      excludedDeviceIds: EXCLUDED_DEVICE_IDS,
      dryRun,
    });
    console.log(JSON.stringify({ files: paths.map((file) => path.basename(file)), dryRun, ...summary }));
    if (summary.failed > 0) process.exitCode = 1;
  } finally {
    await closePool(pool);
  }
}

function parseArgs(args) {
  const sourceIndex = args.indexOf('--source');
  if (sourceIndex < 0 || !args[sourceIndex + 1] || args[sourceIndex + 1].startsWith('--')) {
    throw new Error('Provide --source <directory>.');
  }
  const apply = args.includes('--apply');
  const explicitDryRun = args.includes('--dry-run');
  if (apply && explicitDryRun) throw new Error('Choose either --dry-run or --apply.');
  const known = new Set(['--source', sourceIndex + 1, '--apply', '--dry-run']);
  for (let index = 0; index < args.length; index += 1) {
    if (index === sourceIndex + 1) continue;
    if (!known.has(args[index])) throw new Error(`Unknown argument: ${args[index]}`);
  }
  return { source: args[sourceIndex + 1], dryRun: !apply };
}

if (require.main === module) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}

module.exports = { main, parseArgs, EXCLUDED_DEVICE_IDS };
