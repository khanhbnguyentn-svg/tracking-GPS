'use strict';

class ApplicationError extends Error {
  constructor(code, status, field) {
    super(code);
    this.code = code;
    this.status = status;
    this.field = field;
  }
}

module.exports = { ApplicationError };
