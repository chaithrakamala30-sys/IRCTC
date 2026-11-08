CREATE INDEX stations_active_code_idx
    ON stations(lower(station_code))
    WHERE status = 'ACTIVE';

CREATE INDEX stations_active_search_vector_idx
    ON stations USING GIN (
        to_tsvector('simple', station_name || ' ' || city || ' ' || alternate_names::text)
    )
    WHERE status = 'ACTIVE';

CREATE INDEX train_schedules_passenger_search_idx
    ON train_schedules(train_id, effective_from, effective_to, route_version_id)
    WHERE status IN ('PUBLISHED', 'SUPERSEDED') AND published_at IS NOT NULL;

CREATE INDEX train_route_stops_station_version_idx
    ON train_route_stops(station_id, route_version_id, stop_sequence);
