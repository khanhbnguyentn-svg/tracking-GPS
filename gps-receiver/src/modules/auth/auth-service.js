'use strict';

const { createHash, randomBytes } = require('node:crypto');
const { ApplicationError } = require('../../core/errors');
const { verifyPassword } = require('./passwords');

const DUMMY_HASH = '$argon2id$v=19$m=65536,t=3,p=1$pT6tgmcFQlnjF16Mm2gPVw$1N/ssAJKe9knXXcBjy5Hntbq9fPp6KC1bILC4ype8ic';
const HOUR = 60 * 60 * 1000;

function createAuthService({ repository, clock = () => new Date() }) {
  return {
    async login(username, password, meta = {}) {
      const user = await repository.findUser(String(username || '').trim().toLowerCase());
      const valid = await verifyPassword(user?.passwordHash || DUMMY_HASH, String(password || ''));
      if (!user || !valid || user.status !== 'active') throw new ApplicationError('INVALID_CREDENTIALS', 401);
      const now = clock();
      const sessionToken = randomBytes(32).toString('base64url');
      const csrfSecret = randomBytes(32);
      await repository.createSession({
        userId: user.id,
        tokenHash: tokenHash(sessionToken),
        csrfSecret,
        idleExpiresAt: new Date(now.getTime() + 8 * HOUR),
        absoluteExpiresAt: new Date(now.getTime() + 24 * HOUR),
        clientIp: meta.clientIp || null,
        userAgent: meta.userAgent || null,
      });
      return { sessionToken, csrfToken: csrfSecret.toString('base64url'), user: publicUser(user) };
    },

    async authenticate(sessionToken) {
      if (!sessionToken) throw new ApplicationError('UNAUTHENTICATED', 401);
      const now = clock();
      const session = await repository.findSession(tokenHash(sessionToken), now);
      if (!session) throw new ApplicationError('UNAUTHENTICATED', 401);
      const nextIdle = new Date(Math.min(now.getTime() + 8 * HOUR, session.absoluteExpiresAt?.getTime() || Infinity));
      await repository.touchSession(session.id, now, nextIdle);
      return { user: publicUser(session.user), csrfToken: session.csrfSecret.toString('base64url') };
    },

    async logout(sessionToken) {
      if (sessionToken) await repository.revokeSession(tokenHash(sessionToken), clock());
    },

    requireRole(user, roles) {
      if (!user || !roles.includes(user.role)) throw new ApplicationError('FORBIDDEN', 403);
    },
  };
}

function tokenHash(token) {
  return createHash('sha256').update(token).digest();
}

function publicUser(user) {
  return { id: user.id, username: user.username, role: user.role, locale: user.locale || 'vi' };
}

module.exports = { createAuthService, tokenHash };
