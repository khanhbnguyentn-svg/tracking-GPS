'use strict';

const DEVICE_ID = /^AND-[0-9A-F]{16}$/i;
const MIN_TIME_MS = Date.UTC(2000, 0, 1);

function failure(code, field) {
  return { ok: false, code, field };
}

function numberField(input, field, required, min, max) {
  const raw = input[field];
  if (raw === undefined || raw === null || raw === '') {
    return required ? failure('REQUIRED', field) : { ok: true, value: null };
  }
  const value = typeof raw === 'number' ? raw : Number(String(raw).trim());
  if (!Number.isFinite(value)) return failure('INVALID_NUMBER', field);
  if (value < min || value > max) return failure('OUT_OF_RANGE', field);
  return { ok: true, value };
}

function normalizeLocation(input, nowMs = Date.now()) {
  if (!input || typeof input !== 'object') return failure('REQUIRED', 'body');

  if (input.id === undefined || input.id === null || input.id === '') {
    return failure('REQUIRED', 'id');
  }
  const rawDeviceId = String(input.id);
  if (!DEVICE_ID.test(rawDeviceId)) return failure('INVALID_FORMAT', 'id');
  const deviceId = rawDeviceId.toUpperCase();

  const latitude = numberField(input, 'lat', true, -90, 90);
  if (!latitude.ok) return latitude;
  const longitude = numberField(input, 'lon', true, -180, 180);
  if (!longitude.ok) return longitude;
  const timestamp = numberField(input, 'timestamp', true, 0, Number.MAX_SAFE_INTEGER);
  if (!timestamp.ok) return timestamp;
  const speed = numberField(input, 'speed', false, 0, 10000);
  if (!speed.ok) return speed;
  const accuracy = numberField(input, 'accuracy', false, 0, 1000000);
  if (!accuracy.ok) return accuracy;

  const deviceTimeMs = timestamp.value < 1e12 ? timestamp.value * 1000 : timestamp.value;
  if (deviceTimeMs < MIN_TIME_MS || deviceTimeMs > nowMs + 24 * 60 * 60 * 1000) {
    return failure('OUT_OF_RANGE', 'timestamp');
  }

  return {
    ok: true,
    value: {
      deviceId,
      latitude: latitude.value,
      longitude: longitude.value,
      deviceTime: new Date(deviceTimeMs).toISOString(),
      speedKnots: speed.value,
      accuracyMeters: accuracy.value,
    },
  };
}

module.exports = { normalizeLocation };
