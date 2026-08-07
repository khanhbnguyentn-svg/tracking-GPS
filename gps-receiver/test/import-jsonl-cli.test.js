'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { parseArgs } = require('../scripts/import-jsonl');

test('requires a source directory and defaults to dry-run', () => {
  assert.deepEqual(parseArgs(['--source', 'runtime/data']), { source: 'runtime/data', dryRun: true });
  assert.deepEqual(parseArgs(['--source', 'runtime/data', '--apply']), { source: 'runtime/data', dryRun: false });
  assert.throws(() => parseArgs([]), /--source/);
  assert.throws(() => parseArgs(['--source', 'data', '--dry-run', '--apply']), /Choose/);
});
