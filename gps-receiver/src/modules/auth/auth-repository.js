'use strict';

function createAuthRepository(pool) {
  return {
    async findUser(username) {
      const result = await pool.query(`
        SELECT id, username, password_hash, role, locale, status
        FROM users WHERE lower(username) = lower($1) LIMIT 1
      `, [username]);
      return result.rows[0] && mapUser(result.rows[0]);
    },

    async createSession(session) {
      const result = await pool.query(`
        INSERT INTO sessions (
          user_id, token_hash, csrf_secret, idle_expires_at, absolute_expires_at, client_ip, user_agent
        ) VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id
      `, [session.userId, session.tokenHash, session.csrfSecret, session.idleExpiresAt,
        session.absoluteExpiresAt, session.clientIp, session.userAgent]);
      return { id: result.rows[0].id, ...session };
    },

    async findSession(hash, now) {
      const result = await pool.query(`
        SELECT s.id, s.csrf_secret, s.absolute_expires_at,
               u.id AS user_id, u.username, u.password_hash, u.role, u.locale, u.status
        FROM sessions s JOIN users u ON u.id = s.user_id
        WHERE s.token_hash = $1 AND s.revoked_at IS NULL
          AND s.idle_expires_at > $2 AND s.absolute_expires_at > $2 AND u.status = 'active'
        LIMIT 1
      `, [hash, now]);
      const row = result.rows[0];
      return row && {
        id: row.id,
        csrfSecret: row.csrf_secret,
        absoluteExpiresAt: row.absolute_expires_at,
        user: mapUser({ ...row, id: row.user_id }),
      };
    },

    async touchSession(id, now, idleExpiresAt) {
      await pool.query(`UPDATE sessions SET last_seen_at = $2, idle_expires_at = $3 WHERE id = $1`, [id, now, idleExpiresAt]);
    },

    async revokeSession(hash, now) {
      await pool.query(`UPDATE sessions SET revoked_at = COALESCE(revoked_at, $2) WHERE token_hash = $1`, [hash, now]);
    },
  };
}

function mapUser(row) {
  return {
    id: row.id, username: row.username, passwordHash: row.password_hash,
    role: row.role, locale: row.locale, status: row.status,
  };
}

module.exports = { createAuthRepository };
