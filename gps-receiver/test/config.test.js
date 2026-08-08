'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { readConfig } = require('../src/config');

test('uses safe runtime defaults', () => {
  const config = readConfig({}, 'C:\\ProgramData');
  assert.equal(config.nodeEnv, 'development');
  assert.equal(config.host, '0.0.0.0');
  assert.equal(config.port, 5055);
  assert.equal(config.databaseUrl, 'postgres://fleet:test@127.0.0.1:5432/fleet_test');
  assert.equal(config.dataDir, path.resolve('C:\\ProgramData', 'InternalGpsReceiver', 'data'));
  assert.equal(config.retentionDays, 30);
  assert.equal(config.inactivityMs, 300000);
  assert.equal(config.rateLimit, 120);
  assert.equal(config.businessTimezone, 'Asia/Ho_Chi_Minh');
  assert.equal(config.trustProxy, false);
});

test('reads explicit environment values', () => {
  const config = readConfig({
    GPS_HOST: '127.0.0.1', GPS_PORT: '15055', GPS_DATA_DIR: 'D:\\gps-data',
    GPS_RETENTION_DAYS: '7', GPS_INACTIVITY_MS: '60000', GPS_RATE_LIMIT: '500',
  }, 'C:\\ProgramData');
  assert.equal(config.port, 15055);
  assert.equal(config.retentionDays, 7);
  assert.equal(config.rateLimit, 500);
});

for (const [name, env] of [
  ['port', { GPS_PORT: '70000' }],
  ['retention', { GPS_RETENTION_DAYS: '0' }],
  ['inactivity', { GPS_INACTIVITY_MS: '999' }],
  ['rate limit', { GPS_RATE_LIMIT: '0' }],
]) {
  test(`rejects invalid ${name}`, () => assert.throws(() => readConfig(env, 'C:\\ProgramData'), /Invalid/));
}
