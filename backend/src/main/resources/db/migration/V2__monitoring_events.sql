-- Persistent receipt and idempotency ledger for at-least-once monitoring delivery.
CREATE TABLE monitoring_event (
    event_id uuid PRIMARY KEY,
    resource_id text NOT NULL,
    payload jsonb NOT NULL,
    checked_at timestamptz NOT NULL,
    event_type text NOT NULL CHECK (event_type IN ('STATUS_CHANGED', 'HEARTBEAT')),
    service_id bigint REFERENCES service(id),
    received_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX monitoring_event_unbound_idx ON monitoring_event (resource_id, checked_at)
    WHERE service_id IS NULL;

-- At equal check times a status transition takes precedence over a heartbeat.
ALTER TABLE service_status ADD COLUMN event_type text NOT NULL DEFAULT 'STATUS_CHANGED'
    CHECK (event_type IN ('STATUS_CHANGED', 'HEARTBEAT'));
