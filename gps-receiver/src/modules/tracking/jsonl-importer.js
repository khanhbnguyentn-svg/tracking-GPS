'use strict';

const fs = require('node:fs');
const readline = require('node:readline');
const { normalizeLocation } = require('../../validation');
const { dedupeKey } = require('./ingestion-service');

async function importJsonl({ paths, repository, excludedDeviceIds = new Set(), dryRun = false, onProgress = () => {} }) {
  const summary = { files: 0, lines: 0, imported: 0, duplicates: 0, excluded: 0, invalid: 0, failed: 0 };
  for (const file of paths) {
    summary.files += 1;
    const lines = readline.createInterface({ input: fs.createReadStream(file, { encoding: 'utf8' }), crlfDelay: Infinity });
    for await (const line of lines) {
      if (!line.trim()) continue;
      summary.lines += 1;
      let raw;
      try { raw = JSON.parse(line); }
      catch { summary.invalid += 1; onProgress({ ...summary }); continue; }
      const normalized = normalizeLegacy(raw);
      if (!normalized.ok) { summary.invalid += 1; onProgress({ ...summary }); continue; }
      if (excludedDeviceIds.has(normalized.value.deviceId)) {
        summary.excluded += 1;
        onProgress({ ...summary });
        continue;
      }
      if (dryRun) {
        summary.imported += 1;
        onProgress({ ...summary });
        continue;
      }
      const receivedAt = validDate(raw.receivedAt) || new Date(normalized.value.deviceTime);
      try {
        const result = await repository.insert({
          ...normalized.value,
          deviceTime: new Date(normalized.value.deviceTime),
          receivedAt,
          source: 'jsonl-import',
          dedupeKey: dedupeKey(normalized.value),
        });
        summary[result.duplicate ? 'duplicates' : 'imported'] += 1;
      } catch {
        summary.failed += 1;
      }
      onProgress({ ...summary });
    }
  }
  return summary;
}

function normalizeLegacy(raw) {
  const deviceTime = validDate(raw.deviceTime);
  return normalizeLocation({
    id: raw.deviceId ?? raw.id,
    lat: raw.latitude ?? raw.lat,
    lon: raw.longitude ?? raw.lon,
    timestamp: deviceTime ? deviceTime.getTime() : raw.timestamp,
    speed: raw.speedKnots ?? raw.speed,
    accuracy: raw.accuracyMeters ?? raw.accuracy,
  });
}

function validDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}

module.exports = { importJsonl };
