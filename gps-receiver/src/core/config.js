'use strict';

const path = require('node:path');

const ALLOWED_ENVIRONMENTS = new Set(['development', 'test', 'production']);
const DEFAULT_DATABASE_URL = 'postgres://fleet:test@127.0.0.1:5432/fleet_test';
const DEFAULT_SESSION_SECRET = 'development-only-session-secret-32';
const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1']);

function integer(env, key, fallback, min, max) {
  if (env[key] === undefined || env[key] === '') return fallback;
  const value = Number(env[key]);
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    throw new Error(`Invalid ${key}.`);
  }
  return value;
}

function boolean(env, key, fallback) {
  if (env[key] === undefined || env[key] === '') return fallback;
  if (env[key] === 'true') return true;
  if (env[key] === 'false') return false;
  throw new Error(`Invalid ${key}.`);
}

function databaseUrl(env, nodeEnv) {
  const raw = env.GPS_DATABASE_URL;
  if (!raw && nodeEnv === 'production') throw new Error('GPS_DATABASE_URL is required.');

  let parsed;
  try {
    parsed = new URL(raw || DEFAULT_DATABASE_URL);
  } catch {
    throw new Error('Invalid GPS_DATABASE_URL.');
  }
  if (!['postgres:', 'postgresql:'].includes(parsed.protocol)) {
    throw new Error('Invalid GPS_DATABASE_URL.');
  }
  if (!LOOPBACK_HOSTS.has(parsed.hostname)) {
    throw new Error('GPS_DATABASE_URL must use a loopback host.');
  }
  return parsed.toString();
}

function loadConfig(env = process.env, rootDir = path.resolve(__dirname, '..', '..')) {
  const nodeEnv = env.NODE_ENV || 'development';
  if (!ALLOWED_ENVIRONMENTS.has(nodeEnv)) throw new Error('Invalid NODE_ENV.');

  const host = env.GPS_HOST || '0.0.0.0';
  if (/[:/\\]/.test(host.replace(/^\d+\.\d+\.\d+\.\d+$/, ''))) {
    throw new Error('Invalid GPS_HOST.');
  }

  const configuredDir = env.GPS_DATA_DIR;
  if (configuredDir && !path.isAbsolute(configuredDir)) {
    throw new Error('Invalid GPS_DATA_DIR.');
  }

  const resolvedDatabaseUrl = databaseUrl(env, nodeEnv);
  const sessionSecret = env.GPS_SESSION_SECRET || (nodeEnv === 'production' ? '' : DEFAULT_SESSION_SECRET);
  if (sessionSecret.length < 32) {
    throw new Error('GPS_SESSION_SECRET must contain at least 32 characters.');
  }

  const businessTimezone = env.GPS_BUSINESS_TIMEZONE || 'Asia/Ho_Chi_Minh';
  if (businessTimezone !== 'Asia/Ho_Chi_Minh') {
    throw new Error('Invalid GPS_BUSINESS_TIMEZONE.');
  }

  const inactivityMs = integer(env, 'GPS_INACTIVITY_MS', 300000, 1000, 86400000);
  return Object.freeze({
    nodeEnv,
    host,
    port: integer(env, 'GPS_PORT', 5055, 1, 65535),
    databaseUrl: resolvedDatabaseUrl,
    sessionSecret,
    dataDir: path.resolve(configuredDir || path.join(rootDir, 'runtime', 'data')),
    rateLimit: integer(env, 'GPS_RATE_LIMIT', 120, 1, 100000),
    inactivityMinutes: inactivityMs / 60000,
    businessTimezone,
    trustProxy: boolean(env, 'GPS_TRUST_PROXY', false),
  });
}

module.exports = { loadConfig };
