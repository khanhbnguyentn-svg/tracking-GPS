'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { createApp } = require('../src/app');
const { ApplicationError } = require('../src/core/errors');

const nowMs = new Date('2026-08-07T18:00:00.000Z').getTime();
const payload = {
  id: 'AND-0123456789ABCDEF', lat: 10.1, lon: 106.1,
  timestamp: Math.floor(nowMs / 1000), speed: 3.2, accuracy: 8,
};
const ingestToken = 'abcdefghijklmnopqrstuvwxyz_0123456789-ABCDE';

async function runningApp(t, options = {}) {
  const dataDir = await fs.mkdtemp(path.join(os.tmpdir(), 'gps-app-'));
  const app = createApp({ host: '127.0.0.1', port: 0, dataDir, now: () => nowMs, ...options });
  await app.start();
  t.after(async () => {
    await app.stop();
    await fs.rm(dataDir, { recursive: true, force: true });
  });
  return `http://127.0.0.1:${app.server.address().port}`;
}

test('accepts the current OsmAnd GET contract', async (t) => {
  const base = await runningApp(t);
  const response = await fetch(`${base}/?${new URLSearchParams(payload)}`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    accepted: true,
    deviceId: payload.id,
    receivedAt: '2026-08-07T18:00:00.000Z',
  });
});

test('accepts JSON POST and exposes devices, stats, health and dashboard', async (t) => {
  const base = await runningApp(t);
  const response = await fetch(`${base}/api/locations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload),
  });
  assert.equal(response.status, 200);
  assert.equal((await fetch(`${base}/api/devices`).then((r) => r.json())).length, 1);
  assert.equal((await fetch(`${base}/api/stats`).then((r) => r.json())).accepted, 1);
  assert.equal((await fetch(`${base}/health`).then((r) => r.json())).status, 'ok');
  const dashboard = await fetch(`${base}/dashboard`);
  assert.equal(dashboard.status, 200);
  assert.match(await dashboard.text(), /Thiết bị GPS/);
});

test('requires the ingestion token before accepting public GPS uploads', async (t) => {
  let inserts = 0;
  const repository = {
    insert: async () => {
      inserts += 1;
      return { record: { id: '9' }, duplicate: false };
    },
    health: async () => ({ writable: true, latencyMs: 1 }),
    devices: async () => [],
    stats: async () => ({ devices: 0, accepted: inserts, rejected: 0, recentPerSecond: 0 }),
  };
  const base = await runningApp(t, { ingestToken, repository });
  const params = new URLSearchParams(payload);

  for (const response of [
    await fetch(`${base}/?${params}`),
    await fetch(`${base}/?${params}`, { headers: { authorization: 'Bearer wrong' } }),
  ]) {
    assert.equal(response.status, 401);
    const body = await response.json();
    assert.deepEqual(body, { accepted: false, error: 'UNAUTHORIZED_DEVICE' });
    assert.doesNotMatch(JSON.stringify(body), new RegExp(ingestToken));
  }
  assert.equal(inserts, 0);

  assert.equal((await fetch(`${base}/?${params}`, {
    headers: { authorization: `Bearer ${ingestToken}` },
  })).status, 200);
  assert.equal((await fetch(`${base}/api/locations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${ingestToken}` },
    body: JSON.stringify(payload),
  })).status, 200);
  assert.equal(inserts, 2);
  assert.equal((await fetch(`${base}/health`)).status, 200);
});

test('returns stable errors for invalid requests', async (t) => {
  const base = await runningApp(t);
  const invalid = await fetch(`${base}/?id=bad`);
  assert.equal(invalid.status, 400);
  assert.deepEqual(await invalid.json(), { accepted: false, error: 'INVALID_FORMAT', field: 'id' });

  const malformed = await fetch(`${base}/api/locations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{bad',
  });
  assert.equal(malformed.status, 400);
  assert.equal((await malformed.json()).error, 'INVALID_JSON');

  const wrongType = await fetch(`${base}/api/locations`, { method: 'POST', body: '{}' });
  assert.equal(wrongType.status, 415);
  assert.equal((await fetch(`${base}/api/locations`)).status, 405);
  assert.equal((await fetch(`${base}/missing`)).status, 404);
});

test('rejects JSON bodies larger than 16 KiB', async (t) => {
  const base = await runningApp(t);
  const response = await fetch(`${base}/api/locations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ padding: 'x'.repeat(17000) }),
  });
  assert.equal(response.status, 413);
});

test('rate limits ingestion without blocking health', async (t) => {
  const base = await runningApp(t, { rateLimit: 1 });
  assert.equal((await fetch(`${base}/?${new URLSearchParams(payload)}`)).status, 200);
  assert.equal((await fetch(`${base}/?${new URLSearchParams(payload)}`)).status, 429);
  assert.equal((await fetch(`${base}/health`)).status, 200);
});

test('returns 503 and does not claim acceptance when persistence fails', async (t) => {
  const failingStore = {
    init: async () => {}, close: async () => {}, append: async () => { throw new Error('disk'); },
    recordRejected: () => {}, devices: () => [], stats: () => ({}), health: () => ({ writable: false }),
  };
  const base = await runningApp(t, { store: failingStore });
  const response = await fetch(`${base}/?${new URLSearchParams(payload)}`);
  assert.equal(response.status, 503);
  assert.equal((await response.json()).accepted, false);
  assert.equal((await fetch(`${base}/health`)).status, 503);
});

test('preserves the HTTP contract when PostgreSQL repository is used', async (t) => {
  const commands = [];
  const repository = {
    insert: async (command) => {
      commands.push(command);
      return { record: { id: '9' }, duplicate: false };
    },
    health: async () => ({ writable: true, latencyMs: 1 }),
    devices: async () => [],
    stats: async () => ({ devices: 0, accepted: 1, rejected: 0, recentPerSecond: 0 }),
  };
  const base = await runningApp(t, { repository });
  const response = await fetch(`${base}/?${new URLSearchParams(payload)}`);

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    accepted: true, deviceId: payload.id, receivedAt: '2026-08-07T18:00:00.000Z',
  });
  assert.equal(commands.length, 1);
  assert.equal(commands[0].deviceId, payload.id);
  assert.match(commands[0].dedupeKey, /^[a-f0-9]{64}$/);
  assert.equal((await fetch(`${base}/health`)).status, 200);
});

test('protects management routes with safe login, CSRF and roles while ingestion stays public', async (t) => {
  const revoked = [];
  const authService = {
    login: async (username, password) => {
      if (username !== 'admin' || password !== 'correct password') throw new ApplicationError('INVALID_CREDENTIALS', 401);
      return { sessionToken: 'session-admin', csrfToken: 'csrf-admin', user: { role: 'admin' } };
    },
    authenticate: async (token) => {
      if (token === 'session-admin') return { user: { role: 'admin', locale: 'vi' }, csrfToken: 'csrf-admin' };
      if (token === 'session-dispatcher') return { user: { role: 'dispatcher', locale: 'vi' }, csrfToken: 'csrf-dispatcher' };
      throw new ApplicationError('UNAUTHENTICATED', 401);
    },
    logout: async (token) => { revoked.push(token); },
    requireRole: (user, roles) => { if (!roles.includes(user.role)) throw new ApplicationError('FORBIDDEN', 403); },
  };
  const base = await runningApp(t, { authService, enableAuthTestRoutes: true });

  const redirect = await fetch(`${base}/dashboard`, { redirect: 'manual' });
  assert.equal(redirect.status, 302);
  assert.equal(redirect.headers.get('location'), '/login');
  assert.equal((await fetch(`${base}/login`)).status, 200);

  const invalid = await fetch(`${base}/login`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'missing', password: 'wrong password' }),
  });
  assert.equal(invalid.status, 401);
  assert.doesNotMatch(await invalid.text(), /missing/);

  const login = await fetch(`${base}/login`, {
    method: 'POST', redirect: 'manual', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'admin', password: 'correct password' }),
  });
  assert.equal(login.status, 303);
  const setCookie = login.headers.getSetCookie().join('; ');
  assert.match(setCookie, /fleet_session=session-admin/);
  assert.match(setCookie, /HttpOnly/);
  assert.match(setCookie, /SameSite=Strict/);

  assert.equal((await fetch(`${base}/dashboard`, { headers: { cookie: 'fleet_session=session-admin' } })).status, 200);
  assert.equal((await fetch(`${base}/api/admin-test`, {
    method: 'POST', headers: { cookie: 'fleet_session=session-dispatcher', 'x-csrf-token': 'csrf-dispatcher' },
  })).status, 403);
  assert.equal((await fetch(`${base}/api/admin-test`, {
    method: 'POST', headers: { cookie: 'fleet_session=session-admin' },
  })).status, 403);
  assert.equal((await fetch(`${base}/api/admin-test`, {
    method: 'POST', headers: { cookie: 'fleet_session=session-admin', 'x-csrf-token': 'csrf-admin' },
  })).status, 204);
  const logout = await fetch(`${base}/logout`, {
    method: 'POST', redirect: 'manual',
    headers: { cookie: 'fleet_session=session-admin', 'x-csrf-token': 'csrf-admin' },
  });
  assert.equal(logout.status, 303);
  assert.deepEqual(revoked, ['session-admin']);
  assert.match(logout.headers.getSetCookie().join('; '), /fleet_session=;.*Max-Age=0/);
  assert.equal((await fetch(`${base}/?${new URLSearchParams(payload)}`)).status, 200);
});
