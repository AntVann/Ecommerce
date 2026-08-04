package com.marketflow.search;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SearchRepository {
    private final JdbcTemplate jdbc;

    public SearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean alreadyProcessed(String consumer, UUID eventId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM processed_message WHERE consumer_name=? AND event_id=?",
                        Integer.class,
                        consumer,
                        eventId);
        return count != null && count > 0;
    }

    public void processed(String consumer, UUID eventId) {
        jdbc.update(
                "INSERT INTO processed_message(consumer_name,event_id,processed_at) VALUES (?,?,now()) ON CONFLICT DO NOTHING",
                consumer,
                eventId);
    }

    public boolean sellerSuspended(UUID sellerId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM seller_projection WHERE seller_id=? AND status IN ('SUSPENDED','REJECTED')",
                        Integer.class,
                        sellerId);
        return count != null && count > 0;
    }

    public void sellerStatus(UUID sellerId, String status, long version) {
        jdbc.update(
                "INSERT INTO seller_projection(seller_id,status,aggregate_version,updated_at) VALUES (?,?,?,now()) ON CONFLICT (seller_id) DO UPDATE SET status=EXCLUDED.status,aggregate_version=EXCLUDED.aggregate_version,updated_at=now() WHERE seller_projection.aggregate_version<EXCLUDED.aggregate_version",
                sellerId,
                status,
                version);
    }

    public UUID startRebuild(String index) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO search_rebuild(id,index_name,status,started_at) VALUES (?,?,'RUNNING',now())",
                id,
                index);
        return id;
    }

    public void finishRebuild(UUID id, long indexed, long failed, String status) {
        jdbc.update(
                "UPDATE search_rebuild SET status=?,indexed_count=?,failed_count=?,completed_at=now() WHERE id=?",
                status,
                indexed,
                failed,
                id);
    }
}
