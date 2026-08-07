'use strict';

const { readConfig } = require('../src/config');
const { createPool, closePool } = require('../src/db/pool');
const { createAuthRepository } = require('../src/modules/auth/auth-repository');
const { hashPassword } = require('../src/modules/auth/passwords');

function parseArgs(args) {
  const values = {};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--update' || argument === '--password-stdin') values[argument.slice(2)] = true;
    else if (argument === '--username' || argument === '--role') values[argument.slice(2)] = args[++index];
    else throw new Error(`Unknown argument: ${argument}`);
  }
  const username = String(values.username || '').trim().toLowerCase();
  if (!/^[a-z0-9._-]{3,64}$/.test(username)) throw new Error('Username must contain 3-64 letters, numbers, dot, underscore or hyphen.');
  if (!['admin', 'dispatcher'].includes(values.role)) throw new Error('Role must be admin or dispatcher.');
  if (!values['password-stdin']) throw new Error('--password-stdin is required.');
  return { username, role: values.role, update: Boolean(values.update) };
}

async function readPassword() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8').replace(/[\r\n]+$/, '');
}

async function main() {
  const input = parseArgs(process.argv.slice(2));
  const passwordHash = await hashPassword(await readPassword());
  const pool = createPool(readConfig());
  try {
    await createAuthRepository(pool).saveUser({ ...input, passwordHash }, input.update);
    console.log(`User ${input.username} saved with role ${input.role}.`);
  } finally {
    await closePool(pool);
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

module.exports = { parseArgs, main };
