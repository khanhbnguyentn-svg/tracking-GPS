'use strict';

const { createHash } = require('node:crypto');
const { ApplicationError } = require('../../core/errors');
const { normalizeLocation } = require('../../validation');

function createIngestionService({ repository, clock = () => new Date() }) {
  return {
    async ingest(raw, context = {}) {
      const receivedAt = clock();
      const normalized = normalizeLocation(raw, receivedAt.getTime());
      if (!normalized.ok) throw new ApplicationError(normalized.code, 400, normalized.field);
      const location = normalized.value;
      const command = {
        ...location,
        deviceTime: new Date(location.deviceTime),
        receivedAt,
        source: context.source || 'unknown',
        dedupeKey: dedupeKey(location),
      };
      let persisted;
      try {
        persisted = await repository.insert(command);
      } catch {
        throw new ApplicationError('PERSISTENCE_UNAVAILABLE', 503);
      }
      return {
        accepted: true,
        duplicate: persisted.duplicate,
        deviceId: location.deviceId,
        receivedAt: receivedAt.toISOString(),
        record: persisted.record,
      };
    },
  };
}

function dedupeKey(location) {
  return createHash('sha256')
    .update(`${location.deviceId}|${location.deviceTime}|${location.latitude}|${location.longitude}`)
    .digest('hex');
}

module.exports = { createIngestionService, dedupeKey };
