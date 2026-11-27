import assert from 'node:assert/strict';
import test from 'node:test';

import { getSyncPrecondition, mergeDoseState, shouldApplyRemoteVersion } from '../lib/sync/conflictPolicy';

test('does not replace a newer local medication with an older remote version', () => {
  assert.equal(
    shouldApplyRemoteVersion(
      { updated_at: '2026-07-15T10:00:00.000Z' },
      { updated_at: '2026-07-15T09:00:00.000Z' },
    ),
    false,
  );
});

test('applies a newer remote medication version', () => {
  assert.equal(
    shouldApplyRemoteVersion(
      { updated_at: '2026-07-15T09:00:00.000Z' },
      { updated_at: '2026-07-15T10:00:00.000Z' },
    ),
    true,
  );
});

test('a taken dose never returns to pending', () => {
  const merged = mergeDoseState(
    { status: 'taken', taken_at: '2026-07-15T08:01:00.000Z', updated_at: '2026-07-15T08:01:00.000Z' },
    { status: 'pending', taken_at: null, updated_at: '2026-07-15T09:00:00.000Z' },
  );
  assert.equal(merged.status, 'taken');
  assert.equal(merged.taken_at, '2026-07-15T08:01:00.000Z');
});

test('remote taken wins over local pending', () => {
  const merged = mergeDoseState(
    { status: 'pending', taken_at: null, updated_at: '2026-07-15T08:00:00.000Z' },
    { status: 'taken', taken_at: '2026-07-15T08:02:00.000Z', updated_at: '2026-07-15T08:02:00.000Z' },
  );
  assert.equal(merged.status, 'taken');
});

test('offline mode stops before any cloud synchronization', () => {
  assert.equal(getSyncPrecondition({ configured: true, online: false, hasPatient: true }), 'offline');
  assert.equal(getSyncPrecondition({ configured: true, online: true, hasPatient: true }), 'ready');
});

