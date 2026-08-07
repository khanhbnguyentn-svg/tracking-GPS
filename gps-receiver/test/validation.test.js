'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizeLocation } = require('../src/validation');

const valid = {
  id: 'AND-0123456789ABCDEF',
  lat: '10.1',
  lon: 106.1,
  timestamp: 1786123456,
  speed: '3.2',
  accuracy: 8,
};

test('normalizes a valid location and converts Unix seconds to milliseconds', () => {
  const result = normalizeLocation(valid, 1786123456000);
  assert.equal(result.ok, true);
  assert.deepEqual(result.value, {
    deviceId: valid.id,
    latitude: 10.1,
    longitude: 106.1,
    deviceTime: '2026-08-07T17:24:16.000Z',
    speedKnots: 3.2,
    accuracyMeters: 8,
  });
});

test('accepts Unix milliseconds', () => {
  const result = normalizeLocation({ ...valid, timestamp: 1786123456000 }, 1786123456000);
  assert.equal(result.ok, true);
  assert.equal(result.value.deviceTime, '2026-08-07T17:24:16.000Z');
});

test('accepts the lowercase Android device ID and normalizes it to uppercase', () => {
  const result = normalizeLocation({ ...valid, id: 'AND-0123456789abcdef' }, 1786123456000);
  assert.equal(result.ok, true);
  assert.equal(result.value.deviceId, 'AND-0123456789ABCDEF');
});

for (const [name, patch, code, field] of [
  ['missing id', { id: undefined }, 'REQUIRED', 'id'],
  ['invalid id', { id: 'phone-1' }, 'INVALID_FORMAT', 'id'],
  ['latitude too high', { lat: 90.1 }, 'OUT_OF_RANGE', 'lat'],
  ['longitude too low', { lon: -180.1 }, 'OUT_OF_RANGE', 'lon'],
  ['invalid timestamp', { timestamp: 'nope' }, 'INVALID_NUMBER', 'timestamp'],
  ['negative speed', { speed: -1 }, 'OUT_OF_RANGE', 'speed'],
  ['negative accuracy', { accuracy: -1 }, 'OUT_OF_RANGE', 'accuracy'],
]) {
  test(`rejects ${name}`, () => {
    const result = normalizeLocation({ ...valid, ...patch }, 1786123456000);
    assert.deepEqual(result, { ok: false, code, field });
  });
}

test('accepts boundary coordinates and optional speed/accuracy', () => {
  const result = normalizeLocation({
    id: valid.id,
    lat: -90,
    lon: 180,
    timestamp: valid.timestamp,
  }, 1786123456000);
  assert.equal(result.ok, true);
  assert.equal(result.value.speedKnots, null);
  assert.equal(result.value.accuracyMeters, null);
});
