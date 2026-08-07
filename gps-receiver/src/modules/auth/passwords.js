'use strict';

const { Algorithm, hash, verify } = require('@node-rs/argon2');

const POLICY = Object.freeze({ algorithm: Algorithm.Argon2id, memoryCost: 65536, timeCost: 3, parallelism: 1 });

async function hashPassword(password) {
  if (typeof password !== 'string' || password.length < 12) {
    throw new Error('Password must contain at least 12 characters.');
  }
  return hash(password, POLICY);
}

async function verifyPassword(encoded, password) {
  try { return await verify(encoded, password); }
  catch { return false; }
}

function needsRehash(encoded) {
  const match = /^\$argon2id\$v=\d+\$m=(\d+),t=(\d+),p=(\d+)\$/.exec(encoded);
  if (!match) return true;
  return Number(match[1]) < POLICY.memoryCost
    || Number(match[2]) < POLICY.timeCost
    || Number(match[3]) < POLICY.parallelism;
}

module.exports = { POLICY, hashPassword, verifyPassword, needsRehash };
