'use strict';

const path = require('node:path');
const { loadConfig } = require('./core/config');

function integer(env, key, fallback, min, max) {
  if (env[key] === undefined || env[key] === '') return fallback;
  const value = Number(env[key]);
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error(`Invalid ${key}.`);
  return value;
}

function readConfig(env = process.env, programData = process.env.ProgramData || 'C:\\ProgramData') {
  const configuredDir = env.GPS_DATA_DIR || path.join(programData, 'InternalGpsReceiver', 'data');
  const core = loadConfig({ ...env, GPS_DATA_DIR: configuredDir }, path.resolve(__dirname, '..'));
  return {
    host: core.host,
    port: core.port,
    dataDir: core.dataDir,
    retentionDays: integer(env, 'GPS_RETENTION_DAYS', 30, 1, 3650),
    inactivityMs: core.inactivityMinutes * 60000,
    rateLimit: core.rateLimit,
  };
}

module.exports = { readConfig, loadConfig };
