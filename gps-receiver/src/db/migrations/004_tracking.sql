CREATE TABLE gps_positions (
  id bigint GENERATED ALWAYS AS IDENTITY,
  tracking_device_id bigint NOT NULL REFERENCES tracking_devices(id) ON DELETE RESTRICT,
  assignment_id bigint REFERENCES assignments(id) ON DELETE SET NULL,
  vehicle_id bigint REFERENCES vehicles(id) ON DELETE SET NULL,
  driver_id bigint REFERENCES drivers(id) ON DELETE SET NULL,
  vendor_id bigint REFERENCES vendors(id) ON DELETE SET NULL,
  device_time timestamptz NOT NULL,
  received_at timestamptz NOT NULL,
  location geography(Point, 4326) NOT NULL,
  speed_knots double precision,
  accuracy_meters double precision,
  source text NOT NULL,
  quality_status text NOT NULL DEFAULT 'pending'
    CHECK (quality_status IN ('pending', 'accepted', 'excluded')),
  quality_reason text,
  dedupe_key text NOT NULL,
  PRIMARY KEY (device_time, id),
  UNIQUE (device_time, dedupe_key),
  CHECK (speed_knots IS NULL OR speed_knots >= 0),
  CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0)
) PARTITION BY RANGE (device_time);

CREATE OR REPLACE FUNCTION create_gps_positions_partition(month_start date)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  normalized_start date := date_trunc('month', month_start)::date;
  normalized_end date := (date_trunc('month', month_start) + interval '1 month')::date;
  partition_name text := 'gps_positions_' || to_char(normalized_start, 'YYYY_MM');
BEGIN
  EXECUTE format(
    'CREATE TABLE IF NOT EXISTS %I PARTITION OF gps_positions FOR VALUES FROM (%L) TO (%L)',
    partition_name,
    normalized_start::timestamptz,
    normalized_end::timestamptz
  );
  EXECUTE format(
    'CREATE INDEX IF NOT EXISTS %I ON %I (tracking_device_id, device_time DESC)',
    partition_name || '_device_time_idx',
    partition_name
  );
  EXECUTE format(
    'CREATE INDEX IF NOT EXISTS %I ON %I (vehicle_id, device_time DESC) WHERE vehicle_id IS NOT NULL',
    partition_name || '_vehicle_time_idx',
    partition_name
  );
END;
$$;

SELECT create_gps_positions_partition(date_trunc('month', now())::date);
SELECT create_gps_positions_partition((date_trunc('month', now()) + interval '1 month')::date);

CREATE TABLE ingestion_rejections (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  received_at timestamptz NOT NULL DEFAULT now(),
  source text NOT NULL,
  error_code text NOT NULL,
  error_field text,
  correlation_id uuid
);
CREATE INDEX ingestion_rejections_received_idx ON ingestion_rejections (received_at DESC);
