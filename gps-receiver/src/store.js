'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');

function createStore(options) {
  const dataDir = path.resolve(options.dataDir);
  const now = options.now || Date.now;
  const retentionDays = options.retentionDays ?? 30;
  const inactivityMs = options.inactivityMs ?? 300000;
  const snapshotDelayMs = options.snapshotDelayMs ?? 5000;
  const io = { ...fs, ...(options.io || {}) };
  const latest = new Map();
  const acceptedTimes = [];
  let counters = { total: 0, accepted: 0, rejected: 0 };
  let queue = Promise.resolve();
  let snapshotTimer = null;
  let dirty = false;
  let writable = true;

  const snapshotPath = path.join(dataDir, 'latest-devices.json');

  async function init() {
    await fs.mkdir(dataDir, { recursive: true });
    try {
      const snapshot = JSON.parse(await fs.readFile(snapshotPath, 'utf8'));
      for (const device of snapshot.devices || []) latest.set(device.deviceId, device);
      if (snapshot.counters) counters = { ...counters, ...snapshot.counters };
    } catch (error) {
      if (error.code !== 'ENOENT') {
        await fs.rename(snapshotPath, `${snapshotPath}.corrupt-${now()}`);
      }
    }
    await applyRetention();
  }

  function enqueue(work) {
    const operation = queue.catch(() => undefined).then(work);
    queue = operation;
    return operation;
  }

  function dailyPath(receivedAt) {
    return path.join(dataDir, `locations-${receivedAt.slice(0, 10)}.jsonl`);
  }

  function scheduleSnapshot() {
    if (snapshotTimer) return;
    snapshotTimer = setTimeout(() => {
      snapshotTimer = null;
      enqueue(saveSnapshot).catch(() => { writable = false; });
    }, snapshotDelayMs);
    if (snapshotTimer.unref) snapshotTimer.unref();
  }

  async function append(location, meta = {}) {
    return enqueue(async () => {
      const receivedAt = new Date(now()).toISOString();
      const record = {
        ...location,
        receivedAt,
        source: meta.source === '127.0.0.1' || meta.source === '::1' ? 'loopback' : 'lan',
      };
      try {
        await io.appendFile(dailyPath(receivedAt), `${JSON.stringify(record)}\n`, 'utf8');
      } catch (error) {
        writable = false;
        throw error;
      }
      writable = true;
      latest.set(location.deviceId, record);
      counters.total += 1;
      counters.accepted += 1;
      acceptedTimes.push(now());
      dirty = true;
      scheduleSnapshot();
      return record;
    });
  }

  function recordRejected() {
    counters.total += 1;
    counters.rejected += 1;
  }

  function devices() {
    const current = now();
    return [...latest.values()]
      .map((device) => ({
        ...device,
        status: current - Date.parse(device.receivedAt) <= inactivityMs ? 'active' : 'inactive',
      }))
      .sort((a, b) => Date.parse(b.receivedAt) - Date.parse(a.receivedAt));
  }

  function stats() {
    const cutoff = now() - 1000;
    while (acceptedTimes.length && acceptedTimes[0] < cutoff) acceptedTimes.shift();
    return { ...counters, devices: latest.size, recentPerSecond: acceptedTimes.length };
  }

  function health() {
    return { writable };
  }

  async function saveSnapshot() {
    if (!dirty) return;
    const temporary = `${snapshotPath}.tmp-${process.pid}-${now()}`;
    const content = JSON.stringify({ version: 1, devices: [...latest.values()], counters });
    await fs.writeFile(temporary, content, 'utf8');
    await fs.rm(snapshotPath, { force: true });
    await fs.rename(temporary, snapshotPath);
    dirty = false;
  }

  async function flush() {
    await queue;
    await saveSnapshot();
  }

  async function close() {
    if (snapshotTimer) {
      clearTimeout(snapshotTimer);
      snapshotTimer = null;
    }
    await flush();
  }

  async function applyRetention() {
    const cutoff = new Date(now() - retentionDays * 86400000);
    cutoff.setUTCHours(0, 0, 0, 0);
    const names = await fs.readdir(dataDir);
    for (const name of names) {
      const match = /^locations-(\d{4}-\d{2}-\d{2})\.jsonl$/.exec(name);
      if (!match) continue;
      const fileDate = new Date(`${match[1]}T00:00:00.000Z`);
      if (Number.isFinite(fileDate.getTime()) && fileDate < cutoff) {
        await fs.rm(path.join(dataDir, name));
      }
    }
  }

  return { init, append, recordRejected, devices, stats, health, flush, close, applyRetention };
}

module.exports = { createStore };
