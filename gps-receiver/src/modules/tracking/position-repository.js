'use strict';

function createPositionRepository(pool, { inactivityMs = 300000 } = {}) {
  return {
    async insert(command) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const deviceId = command.deviceId.toUpperCase();
        const device = await client.query(`
          INSERT INTO tracking_devices (device_id, last_received_at)
          VALUES ($1, $2)
          ON CONFLICT (device_id) DO UPDATE
            SET last_received_at = GREATEST(tracking_devices.last_received_at, EXCLUDED.last_received_at),
                updated_at = now()
          RETURNING id
        `, [deviceId, command.receivedAt]);
        const trackingDeviceId = device.rows[0].id;
        const assignment = await client.query(`
          SELECT id, vehicle_id, driver_id, vendor_id
          FROM assignments
          WHERE device_id = $1 AND $2::timestamptz <@ effective_range
          LIMIT 1
        `, [trackingDeviceId, command.deviceTime]);
        const assigned = assignment.rows[0] || {};
        const values = [
          trackingDeviceId, assigned.id || null, assigned.vehicle_id || null,
          assigned.driver_id || null, assigned.vendor_id || null, command.deviceTime,
          command.receivedAt, command.longitude, command.latitude, command.speedKnots,
          command.accuracyMeters, command.source, command.dedupeKey,
        ];
        let result = await client.query(`
          INSERT INTO gps_positions (
            tracking_device_id, assignment_id, vehicle_id, driver_id, vendor_id,
            device_time, received_at, location, speed_knots, accuracy_meters, source, dedupe_key
          ) VALUES (
            $1, $2, $3, $4, $5, $6, $7,
            ST_SetSRID(ST_MakePoint($8, $9), 4326)::geography, $10, $11, $12, $13
          )
          ON CONFLICT (device_time, dedupe_key) DO NOTHING
          RETURNING id, assignment_id, device_time, received_at
        `, values);
        const duplicate = result.rowCount === 0;
        if (duplicate) {
          result = await client.query(`
            SELECT id, assignment_id, device_time, received_at
            FROM gps_positions WHERE device_time = $1 AND dedupe_key = $2
          `, [command.deviceTime, command.dedupeKey]);
        }
        await client.query('COMMIT');
        return { record: mapRecord(result.rows[0]), duplicate };
      } catch (error) {
        await client.query('ROLLBACK');
        throw error;
      } finally {
        client.release();
      }
    },

    async health() {
      const started = Date.now();
      try {
        await pool.query('SELECT 1');
        return { writable: true, latencyMs: Date.now() - started };
      } catch {
        return { writable: false, latencyMs: Date.now() - started };
      }
    },

    async devices() {
      const result = await pool.query(`
        SELECT DISTINCT ON (d.id)
          d.device_id, p.device_time, p.received_at,
          ST_Y(p.location::geometry) AS latitude,
          ST_X(p.location::geometry) AS longitude,
          p.speed_knots, p.accuracy_meters,
          CASE WHEN p.received_at >= now() - ($1 * interval '1 millisecond')
            THEN 'active' ELSE 'inactive' END AS status
        FROM tracking_devices d
        JOIN gps_positions p ON p.tracking_device_id = d.id
        ORDER BY d.id, p.received_at DESC
      `, [inactivityMs]);
      return result.rows.map((row) => ({
        deviceId: row.device_id,
        deviceTime: row.device_time,
        receivedAt: row.received_at,
        latitude: row.latitude,
        longitude: row.longitude,
        speedKnots: row.speed_knots,
        accuracyMeters: row.accuracy_meters,
        status: row.status,
      }));
    },

    async stats() {
      const result = await pool.query(`
        SELECT
          (SELECT count(*)::int FROM gps_positions) AS accepted,
          (SELECT count(*)::int FROM ingestion_rejections) AS rejected,
          (SELECT count(*)::int FROM tracking_devices) AS devices,
          (SELECT count(*)::int FROM gps_positions WHERE received_at >= now() - interval '1 second') AS recent
      `);
      const row = result.rows[0];
      return { accepted: row.accepted, rejected: row.rejected, devices: row.devices, recentPerSecond: row.recent };
    },
  };
}

function mapRecord(row) {
  return {
    id: row.id,
    assignmentId: row.assignment_id,
    deviceTime: row.device_time,
    receivedAt: row.received_at,
  };
}

module.exports = { createPositionRepository };
