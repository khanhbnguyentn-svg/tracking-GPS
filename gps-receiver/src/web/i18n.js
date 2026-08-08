'use strict';

const messages = Object.freeze({
  vi: Object.freeze({ title: 'Đăng nhập quản lý đội xe', username: 'Tên đăng nhập', password: 'Mật khẩu', login: 'Đăng nhập', logout: 'Đăng xuất', invalid: 'Thông tin đăng nhập không hợp lệ' }),
  en: Object.freeze({ title: 'Fleet management login', username: 'Username', password: 'Password', login: 'Sign in', logout: 'Sign out', invalid: 'Invalid credentials' }),
});

function translate(locale, key) {
  return (messages[locale] || messages.vi)[key] || messages.vi[key] || key;
}

module.exports = { translate };
