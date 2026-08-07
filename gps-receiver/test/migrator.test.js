'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { migrate } = require('../src/db/migrator');

function sha256(text) {
  return createHash('sha256').update(Buffer.from(text, 'utf8')).digest('hex');
}

function fakePool(initial = {}, failPattern = null) {
  const applied = new Map(Object.entries(initial));
  const queries = [];
  const client = {
    released: false,
    async query(input, params = []) {
      const text = typeof input === 'string' ? input : input.text;
      queries.push({ text, params });
      if (failPattern && failPattern.test(text)) throw new Error('injected SQL failure');
      if (/SELECT name, sha256 FROM schema_migrations/.test(text)) {
        return { rows: [...applied].map(([name, hash]) => ({ name, sha256: hash })) };
      }
      if (/INSERT INTO schema_migrations/.test(text)) {
        applied.set(params[0], params[1]);
      }
      return { rows: [], rowCount: 0 };
    },
    release() { this.released = true; },
  };
  return {
    client,
    queries,
    applied,
    async connect() { return client; },
  };
}

async function migrations(t, files) {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'fleet-migrations-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  for (const [name, sql] of Object.entries(files)) {
    await fs.writeFile(path.join(directory, name), sql, 'utf8');
  }
  return directory;
}

test('applies pending SQL files in numeric order under one transaction lock', async (t) => {
  const directory = await migrations(t, {
    '002_second.sql': 'SELECT 2 AS second;',
    '001_first.sql': 'SELECT 1 AS first;',
    'notes.txt': 'ignored',
  });
  const pool = fakePool();

  const result = await migrate(pool, directory);

  assert.deepEqual(result, ['001_first.sql', '002_second.sql']);
  assert.equal(pool.queries[0].text, 'BEGIN');
  assert.match(pool.queries[1].text, /pg_advisory_xact_lock/);
  assert.ok(pool.queries.findIndex((query) => query.text.includes('SELECT 1 AS first'))
    < pool.queries.findIndex((query) => query.text.includes('SELECT 2 AS second')));
  assert.equal(pool.queries.at(-1).text, 'COMMIT');
  assert.equal(pool.client.released, true);
});

test('is idempotent when migration names and checksums match', async (t) => {
  const sql = 'SELECT 1;';
  const directory = await migrations(t, { '001_first.sql': sql });
  const pool = fakePool({ '001_first.sql': sha256(sql) });

  assert.deepEqual(await migrate(pool, directory), []);
  assert.equal(pool.queries.some((query) => query.text === sql), false);
  assert.equal(pool.queries.at(-1).text, 'COMMIT');
});

test('rejects checksum drift without executing the changed migration', async (t) => {
  const sql = 'SELECT 2;';
  const directory = await migrations(t, { '001_first.sql': sql });
  const pool = fakePool({ '001_first.sql': sha256('SELECT 1;') });

  await assert.rejects(() => migrate(pool, directory), /checksum mismatch.*001_first\.sql/i);
  assert.equal(pool.queries.some((query) => query.text === sql), false);
  assert.equal(pool.queries.at(-1).text, 'ROLLBACK');
  assert.equal(pool.client.released, true);
});

test('rolls back and releases the client when migration SQL fails', async (t) => {
  const directory = await migrations(t, { '001_broken.sql': 'SELECT broken_statement;' });
  const pool = fakePool({}, /broken_statement/);

  await assert.rejects(() => migrate(pool, directory), /injected SQL failure/);
  assert.equal(pool.queries.at(-1).text, 'ROLLBACK');
  assert.equal(pool.client.released, true);
});
