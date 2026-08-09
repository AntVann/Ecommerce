CREATE TABLE notification_job (
  id UUID PRIMARY KEY, source_event_id UUID NOT NULL, customer_id UUID NOT NULL,
  order_id UUID NOT NULL, kind VARCHAR(64) NOT NULL, recipient VARCHAR(320) NOT NULL,
  template_key VARCHAR(128) NOT NULL, template_version INTEGER NOT NULL,
  variables JSONB NOT NULL, status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','DELIVERED','RETRY_SCHEDULED','DEAD_LETTERED')), attempt_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE(source_event_id, kind)
);
CREATE INDEX notification_job_due_idx ON notification_job(status, next_attempt_at);
CREATE TABLE notification_attempt (
  id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES notification_job(id),
  attempt_number INTEGER NOT NULL, status VARCHAR(32) NOT NULL,
  provider_message_id VARCHAR(128), failure_code VARCHAR(64), created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(job_id, attempt_number)
);
CREATE TABLE notification_template (
  template_key VARCHAR(128) NOT NULL, version INTEGER NOT NULL, subject VARCHAR(256) NOT NULL,
  body TEXT NOT NULL, active BOOLEAN NOT NULL DEFAULT true, PRIMARY KEY(template_key, version)
);
CREATE TABLE notification_inbox (
  consumer VARCHAR(128) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(consumer, event_id)
);
CREATE TABLE notification_outbox (
  id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES notification_job(id),
  routing_key VARCHAR(128) NOT NULL, payload JSONB NOT NULL, published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO notification_template(template_key,version,subject,body) VALUES
 ('order-confirmation',1,'Order {{orderId}} confirmed','Your MarketFlow order {{orderId}} has been confirmed.'),
 ('shipment-created',1,'Shipment for order {{orderId}}','Your order {{orderId}} has shipped. Tracking: {{trackingNumber}}.');
