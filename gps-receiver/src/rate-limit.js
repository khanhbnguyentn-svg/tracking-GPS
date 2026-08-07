'use strict';

function createRateLimiter({ limit = 120, windowMs = 60000, now = Date.now } = {}) {
  const clients = new Map();
  return function allow(key) {
    const current = now();
    const entry = clients.get(key);
    if (!entry || current - entry.startedAt >= windowMs) {
      clients.set(key, { startedAt: current, count: 1 });
      return true;
    }
    entry.count += 1;
    return entry.count <= limit;
  };
}

module.exports = { createRateLimiter };
