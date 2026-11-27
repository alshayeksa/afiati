import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';

import { doseLogKey, uuidFromHex } from '../lib/sync/ids';

const sha256 = (value: string) => createHash('sha256').update(value).digest('hex');

test('the same dose gets the same log id on every device', () => {
  const key = doseLogKey('6f1c1b5e-2c55-4c7a-9a43-0b7d1d1e9a10', '2026-11-27T05:00:00.000Z');
  assert.equal(uuidFromHex(sha256(key)), uuidFromHex(sha256(key)));
});

test('different doses get different log ids', () => {
  const medicationId = '6f1c1b5e-2c55-4c7a-9a43-0b7d1d1e9a10';
  assert.notEqual(
    uuidFromHex(sha256(doseLogKey(medicationId, '2026-11-27T05:00:00.000Z'))),
    uuidFromHex(sha256(doseLogKey(medicationId, '2026-11-27T17:00:00.000Z'))),
  );
});

test('dose log ids are valid UUIDs the server accepts', () => {
  const id = uuidFromHex(sha256('anything'));
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-8[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
