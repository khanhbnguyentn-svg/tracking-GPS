'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { hash } = require('@node-rs/argon2');

test('hashes passwords with Argon2id and verifies only the correct password', async () => {
  const { hashPassword, verifyPassword, needsRehash } = require('../src/modules/auth/passwords');
  const encoded = await hashPassword('correct horse battery staple');
  assert.match(encoded, /^\$argon2id\$/);
  assert.equal(await verifyPassword(encoded, 'correct horse battery staple'), true);
  assert.equal(await verifyPassword(encoded, 'wrong password'), false);
  assert.equal(needsRehash(encoded), false);
  await assert.rejects(hashPassword('too-short'), /12 characters/);
});

test('identifies hashes below the current policy', async () => {
  const { needsRehash } = require('../src/modules/auth/passwords');
  const weak = await hash('correct horse battery staple', { memoryCost: 4096, timeCost: 1, parallelism: 1 });
  assert.equal(needsRehash(weak), true);
});
