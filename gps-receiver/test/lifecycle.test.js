'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { startRuntime } = require('../src/index');

function fakeProcess() {
  const processRef = new EventEmitter();
  processRef.exitCode = undefined;
  return processRef;
}

test('migrates before listening and closes HTTP before PostgreSQL once', async () => {
  const calls = [];
  const processRef = fakeProcess();
  const pool = {};
  const app = {
    start: async () => { calls.push('listen'); },
    stop: async () => { calls.push('http-stop'); },
  };
  const runtime = await startRuntime({
    pool, app, processRef, logger: { log() {}, error() {} },
    migrateFn: async () => { calls.push('migrate'); },
    closePoolFn: async () => { calls.push('pool-close'); },
  });

  assert.deepEqual(calls, ['migrate', 'listen']);
  processRef.emit('SIGTERM');
  processRef.emit('SIGINT');
  await runtime.stopped();
  assert.deepEqual(calls, ['migrate', 'listen', 'http-stop', 'pool-close']);
  assert.equal(processRef.exitCode, 0);
});

test('migration failure never listens and still closes PostgreSQL', async () => {
  const calls = [];
  await assert.rejects(startRuntime({
    pool: {},
    app: { start: async () => { calls.push('listen'); }, stop: async () => {} },
    processRef: fakeProcess(), logger: { log() {}, error() {} },
    migrateFn: async () => { calls.push('migrate'); throw new Error('migration failed'); },
    closePoolFn: async () => { calls.push('pool-close'); },
  }), /migration failed/);
  assert.deepEqual(calls, ['migrate', 'pool-close']);
});

test('bounds a stuck HTTP shutdown and closes PostgreSQL', async () => {
  const calls = [];
  const processRef = fakeProcess();
  const runtime = await startRuntime({
    pool: {}, processRef, shutdownTimeoutMs: 5,
    logger: { log() {}, error() { calls.push('error'); } },
    migrateFn: async () => {},
    app: {
      start: async () => {},
      stop: async () => new Promise(() => {}),
      forceStop: () => { calls.push('force-stop'); },
    },
    closePoolFn: async () => { calls.push('pool-close'); },
  });
  await runtime.stop('test');
  assert.deepEqual(calls, ['force-stop', 'pool-close', 'error']);
  assert.equal(processRef.exitCode, 1);
});
