CREATE TABLE vendors (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code text NOT NULL,
  name text NOT NULL,
  contact_name text,
  contact_phone text,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX vendors_code_upper_uidx ON vendors (upper(btrim(code)));

CREATE TABLE vehicles (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  vehicle_code text NOT NULL,
  plate_number text NOT NULL,
  vehicle_type text,
  make text,
  model text,
  production_year integer CHECK (production_year BETWEEN 1900 AND 2200),
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'maintenance')),
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX vehicles_code_upper_uidx ON vehicles (upper(btrim(vehicle_code)));
CREATE UNIQUE INDEX vehicles_plate_upper_uidx ON vehicles (upper(regexp_replace(plate_number, '\s+', '', 'g')));

CREATE TABLE vehicle_inspections (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  vehicle_id bigint NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
  inspection_number text NOT NULL,
  inspection_authority text,
  inspected_on date NOT NULL,
  expires_on date NOT NULL,
  notes text,
  created_by bigint REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_on >= inspected_on)
);
CREATE INDEX vehicle_inspections_vehicle_date_idx
  ON vehicle_inspections (vehicle_id, inspected_on DESC);

CREATE TABLE drivers (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  driver_code text NOT NULL,
  full_name text NOT NULL,
  phone text,
  license_number text,
  license_expires_on date,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX drivers_code_upper_uidx ON drivers (upper(btrim(driver_code)));
CREATE UNIQUE INDEX drivers_license_upper_uidx ON drivers (upper(btrim(license_number)))
  WHERE license_number IS NOT NULL;

CREATE TABLE tracking_devices (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  device_id text NOT NULL UNIQUE CHECK (device_id ~ '^AND-[0-9A-F]{16}$'),
  display_name text,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
  last_received_at timestamptz,
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE assignments (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  vendor_id bigint NOT NULL REFERENCES vendors(id) ON DELETE RESTRICT,
  vehicle_id bigint NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
  driver_id bigint REFERENCES drivers(id) ON DELETE RESTRICT,
  device_id bigint NOT NULL REFERENCES tracking_devices(id) ON DELETE RESTRICT,
  effective_from timestamptz NOT NULL,
  effective_to timestamptz,
  effective_range tstzrange GENERATED ALWAYS AS
    (tstzrange(effective_from, effective_to, '[)')) STORED,
  notes text,
  created_by bigint REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  EXCLUDE USING gist (vehicle_id WITH =, effective_range WITH &&),
  EXCLUDE USING gist (device_id WITH =, effective_range WITH &&)
);
CREATE INDEX assignments_effective_from_idx ON assignments (effective_from DESC);
CREATE INDEX assignments_driver_idx ON assignments (driver_id, effective_from DESC)
  WHERE driver_id IS NOT NULL;
