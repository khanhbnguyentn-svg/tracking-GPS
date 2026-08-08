'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { hashPassword } = require('../src/modules/auth/passwords');

test('creates an opaque hashed session with idle and absolute expiry', async () => {
  const passwordHash = await hashPassword('correct horse battery staple');
  const calls = [];
  const repository = {
    findUser: async (username) => username === 'admin' ? { id: '1', username: 'Admin', passwordHash, role: 'admin', status: 'active' } : null,
    createSession: async (session) => { calls.push(session); return { id: '9', ...session }; },
  };
  const { createAuthService } = require('../src/modules/auth/auth-service');
  const now = new Date('2026-08-08T00:00:00Z');
  const service = createAuthService({ repository, clock: () => now });

  const result = await service.login('  ADMIN ', 'correct horse battery staple', { clientIp: '127.0.0.1' });

  assert.match(result.sessionToken, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(calls[0].tokenHash.length, 32);
  assert.equal(calls[0].tokenHash.includes(Buffer.from(result.sessionToken)), false);
  assert.equal(calls[0].idleExpiresAt.toISOString(), '2026-08-08T08:00:00.000Z');
  assert.equal(calls[0].absoluteExpiresAt.toISOString(), '2026-08-09T00:00:00.000Z');
});

test('uses generic credential failures and enforces session state and roles', async () => {
  const passwordHash = await hashPassword('correct horse battery staple');
  const user = { id: '1', username: 'admin', passwordHash, role: 'dispatcher', status: 'active' };
  let revoked = false;
  const repository = {
    findUser: async (username) => username === 'admin' ? user : null,
    createSession: async () => ({}),
    findSession: async () => revoked ? null : { id: '2', user, csrfSecret: Buffer.alloc(32, 1) },
    touchSession: async () => {},
    revokeSession: async () => { revoked = true; },
  };
  const { createAuthService } = require('../src/modules/auth/auth-service');
  const service = createAuthService({ repository, clock: () => new Date('2026-08-08T00:00:00Z') });

  await assert.rejects(service.login('missing', 'wrong password'), (error) => error.code === 'INVALID_CREDENTIALS');
  await assert.rejects(service.login('admin', 'wrong password'), (error) => error.code === 'INVALID_CREDENTIALS');
  const authenticated = await service.authenticate('opaque-token');
  assert.equal(authenticated.user.role, 'dispatcher');
  assert.throws(() => service.requireRole(authenticated.user, ['admin']), (error) => error.status === 403);
  await service.logout('opaque-token');
  await assert.rejects(service.authenticate('opaque-token'), (error) => error.code === 'UNAUTHENTICATED');
});
