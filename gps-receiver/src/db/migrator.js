'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash } = require('node:crypto');

const MIGRATION_LOCK_ID = 735155014;
const MIGRATION_PATTERN = /^\d{3}_[a-z0-9_-]+\.sql$/i;

function checksum(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

async function migrationFiles(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isFile() && MIGRATION_PATTERN.test(entry.name))
    .map((entry) => entry.name)
    .sort((left, right) => left.localeCompare(right, 'en', { numeric: true }));
}

async function migrate(pool, directory) {
  const files = await migrationFiles(directory);
  const client = await pool.connect();
  const appliedNow = [];
  try {
    await client.query('BEGIN');
    await client.query('SELECT pg_advisory_xact_lock($1)', [MIGRATION_LOCK_ID]);
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        name text PRIMARY KEY,
        sha256 text NOT NULL,
        applied_at timestamptz NOT NULL DEFAULT now()
      )
    `);
    const result = await client.query('SELECT name, sha256 FROM schema_migrations ORDER BY name');
    const applied = new Map(result.rows.map((row) => [row.name, row.sha256]));

    for (const name of files) {
      const bytes = await fs.readFile(path.join(directory, name));
      const hash = checksum(bytes);
      if (applied.has(name)) {
        if (applied.get(name) !== hash) throw new Error(`Migration checksum mismatch: ${name}`);
        continue;
      }
      await client.query(bytes.toString('utf8'));
      await client.query(
        'INSERT INTO schema_migrations (name, sha256) VALUES ($1, $2)',
        [name, hash],
      );
      appliedNow.push(name);
    }
    await client.query('COMMIT');
    return appliedNow;
  } catch (error) {
    try {
      await client.query('ROLLBACK');
    } catch {
      // Preserve the original migration error.
    }
    throw error;
  } finally {
    client.release();
  }
}

module.exports = { migrate, migrationFiles };
