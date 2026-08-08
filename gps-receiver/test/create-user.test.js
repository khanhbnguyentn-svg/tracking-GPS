'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const path = require('node:path');
const { Pool } = require('pg');
const { createAuthRepository } = require('../src/modules/auth/auth-repository');
const { createAuthService } = require('../src/modules/auth/auth-service');

const databaseUrl = process.env.GPS_TEST_DATABASE_URL;
const cli = path.resolve(__dirname, '../scripts/create-user.js');

async function run(args, password) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [cli, ...args], {
      env: { ...process.env, GPS_DATABASE_URL: databaseUrl },
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) resolve({ stdout, stderr });
      else reject(new Error(`${stderr || stdout} (exit ${code})`));
    });
    child.stdin.end(`${password}\n`);
  });
}

test('create-user safely creates and explicitly updates a supported user', { skip: !databaseUrl }, async () => {
  const pool = new Pool({ connectionString: databaseUrl });
  await pool.query('TRUNCATE sessions, users RESTART IDENTITY CASCADE');
  try {
    const created = await run(['--username', 'Admin', '--role', 'admin', '--password-stdin'], 'first secure password');
    assert.doesNotMatch(created.stdout, /first secure password/);
    assert.doesNotMatch(created.stderr, /first secure password/);

    const auth = createAuthService({ repository: createAuthRepository(pool) });
    assert.equal((await auth.login('admin', 'first secure password')).user.role, 'admin');

    await assert.rejects(
      run(['--username', 'admin', '--role', 'dispatcher', '--password-stdin'], 'second secure password'),
      /already exists.*--update/i,
    );
    await run(['--username', 'admin', '--role', 'dispatcher', '--password-stdin', '--update'], 'second secure password');
    assert.equal((await auth.login('admin', 'second secure password')).user.role, 'dispatcher');

    await assert.rejects(
      run(['--username', 'other', '--role', 'owner', '--password-stdin'], 'third secure password'),
      /role.*admin.*dispatcher/i,
    );
  } finally {
    await pool.end();
  }
});
