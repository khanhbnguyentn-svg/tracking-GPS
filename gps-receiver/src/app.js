'use strict';

const http = require('node:http');
const { createHash, timingSafeEqual } = require('node:crypto');
const { URL } = require('node:url');
const { EventEmitter } = require('node:events');
const { normalizeLocation } = require('./validation');
const { createStore } = require('./store');
const { createRateLimiter } = require('./rate-limit');
const { dashboardHtml } = require('./dashboard');
const { ApplicationError } = require('./core/errors');
const { createIngestionService } = require('./modules/tracking/ingestion-service');
const { loadSession, requireCsrf, sessionToken } = require('./modules/auth/middleware');
const { clearSessionCookies, renderLogin, sessionCookies } = require('./modules/auth/routes');

const BODY_LIMIT = 16 * 1024;

function authorized(request, expectedToken) {
  if (!expectedToken) return true;
  const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(String(request.headers.authorization || ''));
  const supplied = match?.[1] || '';
  const expectedHash = createHash('sha256').update(expectedToken).digest();
  const suppliedHash = createHash('sha256').update(supplied).digest();
  return timingSafeEqual(expectedHash, suppliedHash);
}

function sendJson(response, status, body) {
  const content = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(content),
    'cache-control': 'no-store',
  });
  response.end(content);
}

function redirect(response, status, location, cookies) {
  const headers = { location, 'cache-control': 'no-store' };
  if (cookies) headers['set-cookie'] = cookies;
  response.writeHead(status, headers);
  response.end();
}

function sendHtml(response, status, content) {
  response.writeHead(status, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
  response.end(content);
}

function readForm(request) {
  return new Promise((resolve) => {
    let size = 0;
    const chunks = [];
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size <= BODY_LIMIT) chunks.push(chunk);
    });
    request.on('end', () => resolve(size > BODY_LIMIT ? null : Object.fromEntries(new URLSearchParams(Buffer.concat(chunks).toString('utf8')))));
    request.on('error', () => resolve(null));
  });
}

function readJson(request) {
  return new Promise((resolve) => {
    let size = 0;
    const chunks = [];
    let finished = false;
    request.on('data', (chunk) => {
      if (finished) return;
      size += chunk.length;
      if (size > BODY_LIMIT) {
        finished = true;
        resolve({ error: 'TOO_LARGE' });
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => {
      if (finished) return;
      try { resolve({ value: JSON.parse(Buffer.concat(chunks).toString('utf8')) }); }
      catch { resolve({ error: 'INVALID_JSON' }); }
    });
    request.on('error', () => { if (!finished) resolve({ error: 'INVALID_JSON' }); });
  });
}

function createApp(options) {
  const host = options.host ?? '0.0.0.0';
  const port = options.port ?? 5055;
  const now = options.now || Date.now;
  const store = options.repository ? null : (options.store || createStore({
    dataDir: options.dataDir,
    now,
    retentionDays: options.retentionDays,
    inactivityMs: options.inactivityMs,
  }));
  const readModel = options.readModel || options.repository || store;
  const ingestionService = options.repository
    ? createIngestionService({ repository: options.repository, clock: () => new Date(now()) })
    : null;
  const authService = options.authService || null;
  const ingestToken = options.ingestToken || null;
  const allow = createRateLimiter({ limit: options.rateLimit ?? 120, now });
  const updates = new EventEmitter();
  const eventClients = new Set();

  async function ingest(input, request, response) {
    if (!authorized(request, ingestToken)) {
      return sendJson(response, 401, { accepted: false, error: 'UNAUTHORIZED_DEVICE' });
    }
    const source = request.socket.remoteAddress || 'unknown';
    if (!allow(source)) {
      store?.recordRejected();
      return sendJson(response, 429, { accepted: false, error: 'RATE_LIMITED' });
    }
    if (ingestionService) {
      try {
        const result = await ingestionService.ingest(input, { source });
        updates.emit('location', result);
        return sendJson(response, 200, {
          accepted: true, deviceId: result.deviceId, receivedAt: result.receivedAt,
        });
      } catch (error) {
        if (error instanceof ApplicationError) {
          const body = { accepted: false, error: error.code };
          if (error.field) body.field = error.field;
          return sendJson(response, error.status, body);
        }
        throw error;
      }
    }
    const normalized = normalizeLocation(input, now());
    if (!normalized.ok) {
      store.recordRejected();
      return sendJson(response, 400, { accepted: false, error: normalized.code, field: normalized.field });
    }
    try {
      const record = await store.append(normalized.value, { source });
      updates.emit('location', record);
      return sendJson(response, 200, { accepted: true, deviceId: record.deviceId, receivedAt: record.receivedAt });
    } catch {
      return sendJson(response, 503, { accepted: false, error: 'PERSISTENCE_UNAVAILABLE' });
    }
  }

  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
    try {
      if (url.pathname === '/' && request.method === 'GET') {
        return await ingest(Object.fromEntries(url.searchParams), request, response);
      }
      if (url.pathname === '/api/locations') {
        if (request.method !== 'POST') return sendJson(response, 405, { error: 'METHOD_NOT_ALLOWED' });
        if (!authorized(request, ingestToken)) {
          return sendJson(response, 401, { accepted: false, error: 'UNAUTHORIZED_DEVICE' });
        }
        if (!String(request.headers['content-type'] || '').toLowerCase().startsWith('application/json')) {
          return sendJson(response, 415, { error: 'UNSUPPORTED_MEDIA_TYPE' });
        }
        const body = await readJson(request);
        if (body.error === 'TOO_LARGE') return sendJson(response, 413, { error: 'BODY_TOO_LARGE' });
        if (body.error) return sendJson(response, 400, { error: body.error });
        return await ingest(body.value, request, response);
      }
      if (authService && url.pathname === '/login' && request.method === 'GET') {
        const locale = String(request.headers['accept-language'] || '').toLowerCase().startsWith('en') ? 'en' : 'vi';
        return sendHtml(response, 200, renderLogin(locale));
      }
      if (authService && url.pathname === '/login' && request.method === 'POST') {
        if (!String(request.headers['content-type'] || '').toLowerCase().startsWith('application/x-www-form-urlencoded')) {
          return sendJson(response, 415, { error: 'UNSUPPORTED_MEDIA_TYPE' });
        }
        const form = await readForm(request);
        if (!form) return sendJson(response, 413, { error: 'BODY_TOO_LARGE' });
        try {
          const login = await authService.login(form.username, form.password, {
            clientIp: request.socket.remoteAddress, userAgent: request.headers['user-agent'],
          });
          return redirect(response, 303, '/dashboard', sessionCookies(login.sessionToken, login.csrfToken, Boolean(request.socket.encrypted)));
        } catch (error) {
          if (error instanceof ApplicationError && error.code === 'INVALID_CREDENTIALS') {
            return sendHtml(response, 401, renderLogin('vi', true));
          }
          throw error;
        }
      }

      const protectedPaths = new Set(['/api/devices', '/api/stats', '/dashboard', '/events', '/logout', '/api/admin-test']);
      let session = null;
      if (authService && protectedPaths.has(url.pathname)) {
        try { session = await loadSession(request, authService); }
        catch (error) {
          if (request.method === 'GET' && !url.pathname.startsWith('/api/')) return redirect(response, 302, '/login');
          throw error;
        }
      }
      if (authService && url.pathname === '/logout' && request.method === 'POST') {
        requireCsrf(request, session);
        await authService.logout(sessionToken(request));
        return redirect(response, 303, '/login', clearSessionCookies(Boolean(request.socket.encrypted)));
      }
      if (authService && options.enableAuthTestRoutes && url.pathname === '/api/admin-test' && request.method === 'POST') {
        requireCsrf(request, session);
        authService.requireRole(session.user, ['admin']);
        response.writeHead(204);
        return response.end();
      }
      if (url.pathname === '/api/devices' && request.method === 'GET') return sendJson(response, 200, await readModel.devices());
      if (url.pathname === '/api/stats' && request.method === 'GET') return sendJson(response, 200, await readModel.stats());
      if (url.pathname === '/health' && request.method === 'GET') {
        const healthy = (await readModel.health()).writable;
        return sendJson(response, healthy ? 200 : 503, { status: healthy ? 'ok' : 'degraded' });
      }
      if (url.pathname === '/dashboard' && request.method === 'GET') {
        response.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
        return response.end(dashboardHtml);
      }
      if (url.pathname === '/events' && request.method === 'GET') {
        response.writeHead(200, {
          'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive',
        });
        response.write(': connected\n\n');
        eventClients.add(response);
        request.on('close', () => eventClients.delete(response));
        return;
      }
      const known = ['/api/devices', '/api/stats', '/health', '/dashboard', '/events'].includes(url.pathname);
      return sendJson(response, known ? 405 : 404, { error: known ? 'METHOD_NOT_ALLOWED' : 'NOT_FOUND' });
    } catch (error) {
      if (!response.headersSent && error instanceof ApplicationError) sendJson(response, error.status, { error: error.code });
      else if (!response.headersSent) sendJson(response, 500, { error: 'INTERNAL_ERROR' });
      else response.end();
    }
  });

  const onLocation = (record) => {
    const message = `data: ${JSON.stringify({ deviceId: record.deviceId, receivedAt: record.receivedAt })}\n\n`;
    for (const client of eventClients) client.write(message);
  };
  updates.on('location', onLocation);

  async function start() {
    if (store) await store.init();
    await new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(port, host, () => { server.off('error', reject); resolve(); });
    });
  }

  async function stop() {
    for (const client of eventClients) client.end();
    eventClients.clear();
    if (server.listening) await new Promise((resolve) => server.close(resolve));
    if (store) await store.close();
  }

  function forceStop() {
    server.closeAllConnections?.();
    if (server.listening) server.close();
  }

  return { server, start, stop, forceStop };
}

module.exports = { createApp };
