'use strict';

const cookie = require('cookie');
const { ApplicationError } = require('../../core/errors');

function sessionToken(request) {
  return cookie.parseCookie(request.headers.cookie || '').fleet_session || null;
}

async function loadSession(request, authService) {
  return authService.authenticate(sessionToken(request));
}

function requireCsrf(request, session) {
  const supplied = request.headers['x-csrf-token'];
  if (!supplied || supplied !== session.csrfToken) throw new ApplicationError('CSRF_INVALID', 403);
}

module.exports = { sessionToken, loadSession, requireCsrf };
