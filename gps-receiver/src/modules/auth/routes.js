'use strict';

const fs = require('node:fs');
const path = require('node:path');
const ejs = require('ejs');
const cookie = require('cookie');
const { translate } = require('../../web/i18n');

const template = fs.readFileSync(path.join(__dirname, '../../web/views/login.ejs'), 'utf8');

function renderLogin(locale = 'vi', error = false) {
  return ejs.render(template, { locale, error, t: (key) => translate(locale, key) });
}

function sessionCookies(sessionToken, csrfToken, secure) {
  return [
    cookie.stringifySetCookie({ name: 'fleet_session', value: sessionToken, httpOnly: true, sameSite: 'strict', secure, path: '/' }),
    cookie.stringifySetCookie({ name: 'fleet_csrf', value: csrfToken, sameSite: 'strict', secure, path: '/' }),
  ];
}

function clearSessionCookies(secure) {
  return [
    cookie.stringifySetCookie({ name: 'fleet_session', value: '', httpOnly: true, sameSite: 'strict', secure, path: '/', maxAge: 0 }),
    cookie.stringifySetCookie({ name: 'fleet_csrf', value: '', sameSite: 'strict', secure, path: '/', maxAge: 0 }),
  ];
}

module.exports = { renderLogin, sessionCookies, clearSessionCookies };
