'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { readConfig } = require('../src/config');

test('uses safe runtime defaults', () => {
  const config = readConfig({}, 'C:\\ProgramData');
  assert.deepEqual(config, {
    host: '0.0.0.0', port: 5055,
    dataDir: path.resolve('C:\\ProgramData', 'InternalGpsReceiver', 'data'),
    retentionDays: 30, inactivityMs: 300000, rateLimit: 120,
  });
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
