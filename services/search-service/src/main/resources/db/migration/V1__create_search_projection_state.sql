CREATE TABLE processed_message (
    consumer_name VARCHAR(120) NOT NULL, event_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name,event_id)
);
CREATE TABLE search_rebuild (
    id UUID PRIMARY KEY, index_name VARCHAR(160) NOT NULL, status VARCHAR(24) NOT NULL,
    indexed_count BIGINT NOT NULL DEFAULT 0, failed_count BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ
);
CREATE TABLE seller_projection (
    seller_id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL,
    aggregate_version BIGINT NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
