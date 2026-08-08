'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { loadConfig } = require('../src/core/config');

const databaseUrl = 'postgres://fleet:test@127.0.0.1:5432/fleet_test';
const sessionSecret = '0123456789abcdef0123456789abcdef';
const ingestToken = 'abcdefghijklmnopqrstuvwxyz_0123456789-ABCDE';

test('loads explicit test configuration and freezes the result', () => {
  const config = loadConfig({
    NODE_ENV: 'test',
    GPS_DATABASE_URL: databaseUrl,
    GPS_SESSION_SECRET: sessionSecret,
    GPS_HOST: '127.0.0.1',
    GPS_PORT: '15055',
    GPS_DATA_DIR: 'D:\\fleet-data',
    GPS_RATE_LIMIT: '500',
    GPS_INACTIVITY_MS: '60000',
    GPS_TRUST_PROXY: 'false',
    GPS_INGEST_TOKEN: ingestToken,
  }, 'D:\\fleet');

  assert.deepEqual(config, {
    nodeEnv: 'test',
    host: '127.0.0.1',
    port: 15055,
    databaseUrl,
    sessionSecret,
    dataDir: path.resolve('D:\\fleet-data'),
    rateLimit: 500,
    inactivityMinutes: 1,
    businessTimezone: 'Asia/Ho_Chi_Minh',
    trustProxy: false,
    ingestToken,
  });
  assert.equal(Object.isFrozen(config), true);
});

test('uses safe test defaults rooted in the application directory', () => {
  const config = loadConfig({ NODE_ENV: 'test' }, 'D:\\fleet');

  assert.equal(config.host, '0.0.0.0');
  assert.equal(config.port, 5055);
  assert.equal(config.dataDir, path.resolve('D:\\fleet', 'runtime', 'data'));
  assert.equal(config.databaseUrl, 'postgres://fleet:test@127.0.0.1:5432/fleet_test');
  assert.equal(config.businessTimezone, 'Asia/Ho_Chi_Minh');
  assert.equal(config.ingestToken, null);
});

test('requires database URL and a strong session secret in production', () => {
  assert.throws(
    () => loadConfig({ NODE_ENV: 'production' }, 'D:\\fleet'),
    /GPS_DATABASE_URL is required/,
  );
  assert.throws(
    () => loadConfig({ NODE_ENV: 'production', GPS_DATABASE_URL: databaseUrl }, 'D:\\fleet'),
    /GPS_SESSION_SECRET must contain at least 32 characters/,
  );
});

test('rejects a production database outside loopback', () => {
  assert.throws(() => loadConfig({
    NODE_ENV: 'production',
    GPS_DATABASE_URL: 'postgres://fleet:test@10.0.0.9:5432/fleet',
    GPS_SESSION_SECRET: sessionSecret,
  }, 'D:\\fleet'), /GPS_DATABASE_URL must use a loopback host/);
});

for (const [name, env, expected] of [
  ['unknown environment', { NODE_ENV: 'preview' }, /Invalid NODE_ENV/],
  ['malformed database URL', { NODE_ENV: 'test', GPS_DATABASE_URL: 'not-a-url' }, /Invalid GPS_DATABASE_URL/],
  ['invalid port', { NODE_ENV: 'test', GPS_PORT: '70000' }, /Invalid GPS_PORT/],
  ['invalid rate limit', { NODE_ENV: 'test', GPS_RATE_LIMIT: '0' }, /Invalid GPS_RATE_LIMIT/],
  ['invalid inactivity', { NODE_ENV: 'test', GPS_INACTIVITY_MS: '999' }, /Invalid GPS_INACTIVITY_MS/],
  ['invalid trust proxy', { NODE_ENV: 'test', GPS_TRUST_PROXY: 'yes' }, /Invalid GPS_TRUST_PROXY/],
  ['relative data directory', { NODE_ENV: 'test', GPS_DATA_DIR: 'runtime\\data' }, /Invalid GPS_DATA_DIR/],
  ['empty ingest token', { NODE_ENV: 'test', GPS_INGEST_TOKEN: '' }, /Invalid GPS_INGEST_TOKEN/],
  ['malformed ingest token', { NODE_ENV: 'test', GPS_INGEST_TOKEN: 'short' }, /Invalid GPS_INGEST_TOKEN/],
]) {
  test(`rejects ${name}`, () => {
    assert.throws(() => loadConfig(env, 'D:\\fleet'), expected);
  });
}
