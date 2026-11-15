-- ============================================================================
-- RailConnect — Phase 19: Operational Master Seed & Live Telemetry Initializer
-- Migration: V22__seed_operational_train_runs_and_telemetry.sql
-- ============================================================================

DO $$
DECLARE
    v_sys_user UUID;
    v_stn_ndls UUID;
    v_stn_mmct UUID;
    v_stn_sbc  UUID;
    v_stn_mas  UUID;
    v_stn_bsb  UUID;
    v_stn_hwh  UUID;
    v_stn_bpl  UUID;
    v_stn_ngp  UUID;
    v_stn_sc   UUID;
    v_stn_st   UUID;
    v_stn_brc  UUID;
    v_stn_kota UUID;

    v_tr_12952 UUID;
    v_sch_12952 UUID;
    v_rv_12952 UUID;
    v_comp_12952 UUID;
    v_run_12952 UUID;

    v_tr_12951 UUID;
    v_sch_12951 UUID;
    v_rv_12951 UUID;
    v_comp_12951 UUID;
    v_run_12951 UUID;

    v_tr_20608 UUID;
    v_sch_20608 UUID;
    v_rv_20608 UUID;
    v_comp_20608 UUID;
    v_run_20608 UUID;

    v_tr_22436 UUID;
    v_sch_22436 UUID;
    v_rv_22436 UUID;
    v_comp_22436 UUID;
    v_run_22436 UUID;

    v_tr_12301 UUID;
    v_sch_12301 UUID;
    v_rv_12301 UUID;
    v_comp_12301 UUID;
    v_run_12301 UUID;

    v_tr_12724 UUID;
    v_sch_12724 UUID;
    v_rv_12724 UUID;
    v_comp_12724 UUID;
    v_run_12724 UUID;

    v_tr_12626 UUID;
    v_sch_12626 UUID;
    v_rv_12626 UUID;
    v_comp_12626 UUID;
    v_run_12626 UUID;

    v_today DATE := CURRENT_DATE;
BEGIN
    -- 1. Ensure system user exists
    SELECT user_id INTO v_sys_user FROM users WHERE role = 'SYSTEM' OR email LIKE '%system%' LIMIT 1;
    IF v_sys_user IS NULL THEN
        SELECT user_id INTO v_sys_user FROM users LIMIT 1;
    END IF;

    -- 2. Lookup standard stations
    SELECT station_id INTO v_stn_ndls FROM stations WHERE station_code = 'NDLS' LIMIT 1;
    SELECT station_id INTO v_stn_mmct FROM stations WHERE station_code = 'MMCT' LIMIT 1;
    SELECT station_id INTO v_stn_sbc  FROM stations WHERE station_code = 'SBC' LIMIT 1;
    SELECT station_id INTO v_stn_mas  FROM stations WHERE station_code = 'MAS' LIMIT 1;
    SELECT station_id INTO v_stn_bsb  FROM stations WHERE station_code = 'BSB' LIMIT 1;
    SELECT station_id INTO v_stn_hwh  FROM stations WHERE station_code = 'HWH' LIMIT 1;
    SELECT station_id INTO v_stn_bpl  FROM stations WHERE station_code = 'BPL' LIMIT 1;
    SELECT station_id INTO v_stn_ngp  FROM stations WHERE station_code = 'NGP' LIMIT 1;
    SELECT station_id INTO v_stn_sc   FROM stations WHERE station_code = 'SC' LIMIT 1;
    SELECT station_id INTO v_stn_st   FROM stations WHERE station_code = 'ST' LIMIT 1;
    SELECT station_id INTO v_stn_brc  FROM stations WHERE station_code = 'BRC' LIMIT 1;
    SELECT station_id INTO v_stn_kota FROM stations WHERE station_code = 'KOTA' LIMIT 1;

    -- 3. Lookup trains, published schedules, route versions and composition versions
    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_12952, v_sch_12952, v_rv_12952, v_comp_12952
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '12952'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_12951, v_sch_12951, v_rv_12951, v_comp_12951
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '12951'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_20608, v_sch_20608, v_rv_20608, v_comp_20608
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '20608'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_22436, v_sch_22436, v_rv_22436, v_comp_22436
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '22436'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_12301, v_sch_12301, v_rv_12301, v_comp_12301
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '12301'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_12724, v_sch_12724, v_rv_12724, v_comp_12724
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '12724'
    ORDER BY s.version_number DESC LIMIT 1;

    SELECT t.train_id, s.schedule_id, s.route_version_id, c.current_version_id
    INTO v_tr_12626, v_sch_12626, v_rv_12626, v_comp_12626
    FROM trains t
    JOIN train_schedules s ON s.train_id = t.train_id AND s.status = 'PUBLISHED'
    LEFT JOIN train_compositions c ON c.train_id = t.train_id
    WHERE t.train_number = '12626'
    ORDER BY s.version_number DESC LIMIT 1;

    -- Ensure fallback dummy composition version if null
    IF v_comp_12952 IS NULL THEN
        SELECT composition_version_id INTO v_comp_12952 FROM train_composition_versions LIMIT 1;
    END IF;
    IF v_comp_12951 IS NULL THEN v_comp_12951 := v_comp_12952; END IF;
    IF v_comp_20608 IS NULL THEN v_comp_20608 := v_comp_12952; END IF;
    IF v_comp_22436 IS NULL THEN v_comp_22436 := v_comp_12952; END IF;
    IF v_comp_12301 IS NULL THEN v_comp_12301 := v_comp_12952; END IF;
    IF v_comp_12724 IS NULL THEN v_comp_12724 := v_comp_12952; END IF;
    IF v_comp_12626 IS NULL THEN v_comp_12626 := v_comp_12952; END IF;

    -- If no composition version exists at all, create an authoritative default one
    IF v_comp_12952 IS NULL THEN
        DECLARE
            v_dummy_comp UUID := '40000000-0000-0000-0000-000000000001';
            v_dummy_ver  UUID := '41000000-0000-0000-0000-000000000001';
        BEGIN
            INSERT INTO train_compositions (composition_id, train_id, created_by, updated_by)
            VALUES (v_dummy_comp, v_tr_12952, v_sys_user, v_sys_user) ON CONFLICT DO NOTHING;

            INSERT INTO train_composition_versions (composition_version_id, composition_id, version_number, total_capacity, snapshot, status, published_at)
            VALUES (v_dummy_ver, v_dummy_comp, 1, 720, '{"coaches": []}', 'PUBLISHED', CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING;

            UPDATE train_compositions SET current_version_id = v_dummy_ver WHERE composition_id = v_dummy_comp;

            v_comp_12952 := v_dummy_ver;
            v_comp_12951 := v_dummy_ver;
            v_comp_20608 := v_dummy_ver;
            v_comp_22436 := v_dummy_ver;
            v_comp_12301 := v_dummy_ver;
            v_comp_12724 := v_dummy_ver;
            v_comp_12626 := v_dummy_ver;
        END;
    END IF;

    -- ========================================================================
    -- 4. INSERT / SEED INVENTORY TRAIN RUNS FOR TODAY
    -- ========================================================================
    -- Train 1: 12952 Mumbai Tejas Rajdhani
    IF v_tr_12952 IS NOT NULL AND v_sch_12952 IS NOT NULL THEN
        v_run_12952 := '60000000-0000-0000-0000-000000000001';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_12952, v_tr_12952, v_today, v_sch_12952, v_rv_12952, v_comp_12952, 'READY',
             '[{"stopIndex": 0, "stationCode": "NDLS"}, {"stopIndex": 1, "stationCode": "KOTA"}, {"stopIndex": 2, "stationCode": "BRC"}, {"stopIndex": 3, "stationCode": "ST"}, {"stopIndex": 4, "stationCode": "MMCT"}]'::jsonb,
             '[{"coachNumber": "B1", "class": "3A", "capacity": 72}, {"coachNumber": "A1", "class": "2A", "capacity": 54}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_12952;

        -- Operational Status: RUNNING (On-Route, Progress: 48%, current: KOTA, next: BRC)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_12952, 'RUNNING', v_stn_kota, v_stn_brc, v_stn_kota, 'DEPARTURE', CURRENT_TIMESTAMP - INTERVAL '12 minutes', 4, 48.00, 'Operating on schedule via Kota division')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- Train 2: 12951 New Delhi Tejas Rajdhani Return
    IF v_tr_12951 IS NOT NULL AND v_sch_12951 IS NOT NULL THEN
        v_run_12951 := '60000000-0000-0000-0000-000000000002';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_12951, v_tr_12951, v_today, v_sch_12951, v_rv_12951, v_comp_12951, 'READY',
             '[{"stopIndex": 0, "stationCode": "MMCT"}, {"stopIndex": 1, "stationCode": "ST"}, {"stopIndex": 2, "stationCode": "BRC"}, {"stopIndex": 3, "stationCode": "KOTA"}, {"stopIndex": 4, "stationCode": "NDLS"}]'::jsonb,
             '[{"coachNumber": "B1", "class": "3A", "capacity": 72}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_12951;

        -- Operational Status: RUNNING (On-Route, Progress: 22%, current: ST, next: BRC)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_12951, 'RUNNING', v_stn_st, v_stn_brc, v_stn_st, 'DEPARTURE', CURRENT_TIMESTAMP - INTERVAL '8 minutes', 0, 22.00, 'Right time departure from Surat')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- Train 3: 20608 Mysuru - Chennai Vande Bharat
    IF v_tr_20608 IS NOT NULL AND v_sch_20608 IS NOT NULL THEN
        v_run_20608 := '60000000-0000-0000-0000-000000000003';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_20608, v_tr_20608, v_today, v_sch_20608, v_rv_20608, v_comp_20608, 'READY',
             '[{"stopIndex": 0, "stationCode": "MYS"}, {"stopIndex": 1, "stationCode": "SBC"}, {"stopIndex": 2, "stationCode": "MAS"}]'::jsonb,
             '[{"coachNumber": "C1", "class": "CC", "capacity": 78}, {"coachNumber": "E1", "class": "EC", "capacity": 52}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_20608;

        -- Operational Status: RUNNING (Departed SBC towards MAS)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_20608, 'RUNNING', v_stn_sbc, v_stn_mas, v_stn_sbc, 'DEPARTURE', CURRENT_TIMESTAMP - INTERVAL '25 minutes', 2, 60.00, 'Smooth transit through KSR Bengaluru')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- Train 4: 22436 Varanasi Vande Bharat Express
    IF v_tr_22436 IS NOT NULL AND v_sch_22436 IS NOT NULL THEN
        v_run_22436 := '60000000-0000-0000-0000-000000000004';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_22436, v_tr_22436, v_today, v_sch_22436, v_rv_22436, v_comp_22436, 'READY',
             '[{"stopIndex": 0, "stationCode": "NDLS"}, {"stopIndex": 1, "stationCode": "CNB"}, {"stopIndex": 2, "stationCode": "PRYJ"}, {"stopIndex": 3, "stationCode": "BSB"}]'::jsonb,
             '[{"coachNumber": "C1", "class": "CC", "capacity": 78}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_22436;

        -- Operational Status: DELAYED (Fog in Northern plains, 28 min delay)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_22436, 'DELAYED', v_stn_ndls, v_stn_bsb, v_stn_ndls, 'DEPARTURE', CURRENT_TIMESTAMP - INTERVAL '40 minutes', 28, 35.00, 'Caution order observed due to dense fog in NCR region')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- Train 5: 12301 Howrah Rajdhani Express
    IF v_tr_12301 IS NOT NULL AND v_sch_12301 IS NOT NULL THEN
        v_run_12301 := '60000000-0000-0000-0000-000000000005';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_12301, v_tr_12301, v_today, v_sch_12301, v_rv_12301, v_comp_12301, 'READY',
             '[{"stopIndex": 0, "stationCode": "HWH"}, {"stopIndex": 1, "stationCode": "DHN"}, {"stopIndex": 2, "stationCode": "GAYA"}, {"stopIndex": 3, "stationCode": "PRYJ"}, {"stopIndex": 4, "stationCode": "CNB"}, {"stopIndex": 5, "stationCode": "NDLS"}]'::jsonb,
             '[{"coachNumber": "B1", "class": "3A", "capacity": 72}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_12301;

        -- Operational Status: SCHEDULED (Awaiting departure from Howrah)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_12301, 'SCHEDULED', v_stn_hwh, v_stn_ndls, v_stn_hwh, NULL, NULL, 0, 0.00, 'Platform rake placement and water filling completed')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id;
    END IF;

    -- Train 6: 12724 Telangana Express
    IF v_tr_12724 IS NOT NULL AND v_sch_12724 IS NOT NULL THEN
        v_run_12724 := '60000000-0000-0000-0000-000000000006';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_12724, v_tr_12724, v_today, v_sch_12724, v_rv_12724, v_comp_12724, 'READY',
             '[{"stopIndex": 0, "stationCode": "NDLS"}, {"stopIndex": 1, "stationCode": "BPL"}, {"stopIndex": 2, "stationCode": "NGP"}, {"stopIndex": 3, "stationCode": "SC"}]'::jsonb,
             '[{"coachNumber": "S1", "class": "SL", "capacity": 80}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_12724;

        -- Operational Status: RUNNING (Approaching Bhopal)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_12724, 'RUNNING', v_stn_bpl, v_stn_ngp, v_stn_bpl, 'ARRIVAL', CURRENT_TIMESTAMP - INTERVAL '5 minutes', 7, 42.00, 'Passing West Central railway section')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- Train 7: 12626 Kerala Express
    IF v_tr_12626 IS NOT NULL AND v_sch_12626 IS NOT NULL THEN
        v_run_12626 := '60000000-0000-0000-0000-000000000007';
        INSERT INTO inventory_train_runs
            (train_run_id, train_id, service_date, schedule_id, route_version_id, composition_version_id, status, route_snapshot, composition_snapshot, created_by)
        VALUES
            (v_run_12626, v_tr_12626, v_today, v_sch_12626, v_rv_12626, v_comp_12626, 'READY',
             '[{"stopIndex": 0, "stationCode": "NDLS"}, {"stopIndex": 1, "stationCode": "BPL"}, {"stopIndex": 2, "stationCode": "NGP"}, {"stopIndex": 3, "stationCode": "ERS"}]'::jsonb,
             '[{"coachNumber": "B1", "class": "3A", "capacity": 72}]'::jsonb,
             v_sys_user)
        ON CONFLICT (train_id, service_date) DO UPDATE SET status = 'READY'
        RETURNING train_run_id INTO v_run_12626;

        -- Operational Status: RUNNING (Passing Nagpur junction)
        INSERT INTO train_run_operations
            (operation_id, train_run_id, operational_status, current_station_id, next_station_id, last_reported_station_id, last_reported_event, last_reported_at, current_delay_minutes, route_progress_percent, assigned_crew_notes)
        VALUES
            (gen_random_uuid(), v_run_12626, 'RUNNING', v_stn_ngp, v_stn_sc, v_stn_ngp, 'PASSING', CURRENT_TIMESTAMP - INTERVAL '15 minutes', 11, 55.00, 'Crew changeover executed at Nagpur')
        ON CONFLICT (train_run_id) DO UPDATE SET
            operational_status = EXCLUDED.operational_status,
            current_station_id = EXCLUDED.current_station_id,
            next_station_id = EXCLUDED.next_station_id,
            last_reported_event = EXCLUDED.last_reported_event,
            current_delay_minutes = EXCLUDED.current_delay_minutes,
            route_progress_percent = EXCLUDED.route_progress_percent;
    END IF;

    -- ========================================================================
    -- 5. SEED ACTIVE OPERATIONAL INCIDENTS & ANNOUNCEMENTS
    -- ========================================================================
    IF v_sys_user IS NOT NULL THEN
        INSERT INTO operational_incidents
            (incident_id, incident_number, category, priority, status, train_run_id, station_id, title, description, reported_by)
        VALUES
            (gen_random_uuid(), 'INC-2026-0891', 'SERVICE_DELAY', 'HIGH', 'INVESTIGATING', v_run_22436, v_stn_ndls,
             'Fog speed regulation in Northern Railway Delhi division',
             'Dense fog reduced visibility below 100 meters, requiring automatic train protection speed restriction to 60 km/h.', v_sys_user),
            (gen_random_uuid(), 'INC-2026-0892', 'TECHNICAL_ISSUE', 'MEDIUM', 'ACKNOWLEDGED', v_run_12952, v_stn_kota,
             'Signal track circuit inspection near Kota Yard',
             'Routine signaling circuit health diagnostic performed during scheduled halt; all lines cleared green.', v_sys_user)
        ON CONFLICT (incident_number) DO NOTHING;

        INSERT INTO operational_announcements
            (announcement_id, title, content, announcement_type, priority, target_scope, status, starts_at)
        VALUES
            (gen_random_uuid(), 'Winter Fog Caution Protocols Active',
             'Speed regulation protocols active across Northern, North Central, and Western railway zones. Trains equipped with fog pilot safety units are running normally.',
             'GENERAL', 'NORMAL', 'ALL', 'PUBLISHED', CURRENT_TIMESTAMP)
        ON CONFLICT DO NOTHING;
    END IF;

END $$;
