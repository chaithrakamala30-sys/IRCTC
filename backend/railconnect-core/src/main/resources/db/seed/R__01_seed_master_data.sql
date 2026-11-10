-- ============================================================================
-- RailConnect / IRCTC Master Seed Data (Schema: railconnect)
-- ============================================================================
-- Target Schema: railconnect
-- ============================================================================

SET search_path TO railconnect, public;

DO $$
DECLARE
    -- Zones (12 Major Zones)
    v_nr_zone_id   UUID := '10000000-0000-0000-0000-000000000001';
    v_wr_zone_id   UUID := '10000000-0000-0000-0000-000000000002';
    v_sr_zone_id   UUID := '10000000-0000-0000-0000-000000000003';
    v_swr_zone_id  UUID := '10000000-0000-0000-0000-000000000004';
    v_er_zone_id   UUID := '10000000-0000-0000-0000-000000000005';
    v_cr_zone_id   UUID := '10000000-0000-0000-0000-000000000006';
    v_ncr_zone_id  UUID := '10000000-0000-0000-0000-000000000007';
    v_scr_zone_id  UUID := '10000000-0000-0000-0000-000000000008';
    v_ser_zone_id  UUID := '10000000-0000-0000-0000-000000000009';
    v_ecor_zone_id UUID := '10000000-0000-0000-0000-000000000010';
    v_nwr_zone_id  UUID := '10000000-0000-0000-0000-000000000011';
    v_wcr_zone_id  UUID := '10000000-0000-0000-0000-000000000012';

    -- Divisions
    v_dli_div_id   UUID := '11000000-0000-0000-0000-000000000001';
    v_bct_div_id   UUID := '11000000-0000-0000-0000-000000000002';
    v_mas_div_id   UUID := '11000000-0000-0000-0000-000000000003';
    v_sbc_div_id   UUID := '11000000-0000-0000-0000-000000000004';
    v_hwh_div_id   UUID := '11000000-0000-0000-0000-000000000005';
    v_cstm_div_id  UUID := '11000000-0000-0000-0000-000000000006';
    v_ald_div_id   UUID := '11000000-0000-0000-0000-000000000007';
    v_sc_div_id    UUID := '11000000-0000-0000-0000-000000000008';
    v_tvc_div_id   UUID := '11000000-0000-0000-0000-000000000009';
    v_jp_div_id    UUID := '11000000-0000-0000-0000-000000000010';
    v_bpl_div_id   UUID := '11000000-0000-0000-0000-000000000011';

    -- Core Station IDs
    v_stn_ndls UUID := '20000000-0000-0000-0000-000000000001';
    v_stn_mmct UUID := '20000000-0000-0000-0000-000000000002';
    v_stn_kota UUID := '20000000-0000-0000-0000-000000000003';
    v_stn_brc  UUID := '20000000-0000-0000-0000-000000000004';
    v_stn_st   UUID := '20000000-0000-0000-0000-000000000005';
    v_stn_mas  UUID := '20000000-0000-0000-0000-000000000006';
    v_stn_sbc  UUID := '20000000-0000-0000-0000-000000000007';
    v_stn_mys  UUID := '20000000-0000-0000-0000-000000000008';
    v_stn_hwh  UUID := '20000000-0000-0000-0000-000000000009';
    v_stn_cnb  UUID := '20000000-0000-0000-0000-000000000010';
    v_stn_pryj UUID := '20000000-0000-0000-0000-000000000011';
    v_stn_bsb  UUID := '20000000-0000-0000-0000-000000000012';
    v_stn_lko  UUID := '20000000-0000-0000-0000-000000000013';
    v_stn_cstm UUID := '20000000-0000-0000-0000-000000000014';
    v_stn_pnbe UUID := '20000000-0000-0000-0000-000000000015';
    v_stn_sc   UUID := '20000000-0000-0000-0000-000000000016';
    v_stn_hyb  UUID := '20000000-0000-0000-0000-000000000017';
    v_stn_bpl  UUID := '20000000-0000-0000-0000-000000000018';
    v_stn_ngp  UUID := '20000000-0000-0000-0000-000000000019';
    v_stn_tvc  UUID := '20000000-0000-0000-0000-000000000020';
    v_stn_ers  UUID := '20000000-0000-0000-0000-000000000021';
    v_stn_jp   UUID := '20000000-0000-0000-0000-000000000022';
    v_stn_adi  UUID := '20000000-0000-0000-0000-000000000023';
    v_stn_pune UUID := '20000000-0000-0000-0000-000000000024';
    v_stn_gwl  UUID := '20000000-0000-0000-0000-000000000025';
    v_stn_agc  UUID := '20000000-0000-0000-0000-000000000026';
    v_stn_sda  UUID := '20000000-0000-0000-0000-000000000027';
    v_stn_bkn  UUID := '20000000-0000-0000-0000-000000000028';

    -- Categories & Types
    v_cat_rajdhani UUID := '30000000-0000-0000-0000-000000000001';
    v_cat_vb       UUID := '30000000-0000-0000-0000-000000000002';
    v_cat_shatabdi UUID := '30000000-0000-0000-0000-000000000003';
    v_cat_duronto  UUID := '30000000-0000-0000-0000-000000000004';
    v_cat_sf       UUID := '30000000-0000-0000-0000-000000000005';
    v_cat_mail_exp UUID := '30000000-0000-0000-0000-000000000006';

    v_type_rajdhani    UUID := '31000000-0000-0000-0000-000000000001';
    v_type_vb          UUID := '31000000-0000-0000-0000-000000000002';
    v_type_shatabdi    UUID := '31000000-0000-0000-0000-000000000003';
    v_type_duronto     UUID := '31000000-0000-0000-0000-000000000004';
    v_type_superfast   UUID := '31000000-0000-0000-0000-000000000005';

    -- Coach Classes
    v_class_1a UUID := '40000000-0000-0000-0000-000000000001';
    v_class_2a UUID := '40000000-0000-0000-0000-000000000002';
    v_class_3a UUID := '40000000-0000-0000-0000-000000000003';
    v_class_3e UUID := '40000000-0000-0000-0000-000000000004';
    v_class_sl UUID := '40000000-0000-0000-0000-000000000005';
    v_class_cc UUID := '40000000-0000-0000-0000-000000000006';
    v_class_ec UUID := '40000000-0000-0000-0000-000000000007';
    v_class_2s UUID := '40000000-0000-0000-0000-000000000008';

    -- Coach Types
    v_ctype_lhb UUID := '41000000-0000-0000-0000-000000000001';
    v_ctype_vb  UUID := '41000000-0000-0000-0000-000000000002';
    v_ctype_icf UUID := '41000000-0000-0000-0000-000000000003';

    -- Configurations
    v_cfg_1a UUID := '42000000-0000-0000-0000-000000000001';
    v_cfg_2a UUID := '42000000-0000-0000-0000-000000000002';
    v_cfg_3a UUID := '42000000-0000-0000-0000-000000000003';
    v_cfg_sl UUID := '42000000-0000-0000-0000-000000000004';
    v_cfg_cc UUID := '42000000-0000-0000-0000-000000000005';
    v_cfg_ec UUID := '42000000-0000-0000-0000-000000000006';

    -- 10 Real National Routes & Versions
    v_r_ndls_mmct UUID := '50000000-0000-0000-0000-000000000001';
    v_rv_ndls_mmct UUID := '51000000-0000-0000-0000-000000000001';

    v_r_mmct_ndls UUID := '50000000-0000-0000-0000-000000000002';
    v_rv_mmct_ndls UUID := '51000000-0000-0000-0000-000000000002';

    v_r_mys_mas UUID := '50000000-0000-0000-0000-000000000003';
    v_rv_mys_mas UUID := '51000000-0000-0000-0000-000000000003';

    v_r_ndls_bsb UUID := '50000000-0000-0000-0000-000000000005';
    v_rv_ndls_bsb UUID := '51000000-0000-0000-0000-000000000005';

    v_r_hwh_ndls UUID := '50000000-0000-0000-0000-000000000006';
    v_rv_hwh_ndls UUID := '51000000-0000-0000-0000-000000000006';

    v_r_ndls_lko UUID := '50000000-0000-0000-0000-000000000007';
    v_rv_ndls_lko UUID := '51000000-0000-0000-0000-000000000007';

    v_r_ndls_hyb UUID := '50000000-0000-0000-0000-000000000008';
    v_rv_ndls_hyb UUID := '51000000-0000-0000-0000-000000000008';

    v_r_ndls_tvc UUID := '50000000-0000-0000-0000-000000000009';
    v_rv_ndls_tvc UUID := '51000000-0000-0000-0000-000000000009';

    v_r_bkn_sda  UUID := '50000000-0000-0000-0000-000000000010';
    v_rv_bkn_sda  UUID := '51000000-0000-0000-0000-000000000010';

    v_r_csmt_mas UUID := '50000000-0000-0000-0000-000000000011';
    v_rv_csmt_mas UUID := '51000000-0000-0000-0000-000000000011';

    -- Trains & Schedules
    v_train_12952 UUID := '52000000-0000-0000-0000-000000000001';
    v_sch_12952   UUID := '53000000-0000-0000-0000-000000000001';

    v_train_12951 UUID := '52000000-0000-0000-0000-000000000002';
    v_sch_12951   UUID := '53000000-0000-0000-0000-000000000002';

    v_train_20608 UUID := '52000000-0000-0000-0000-000000000003';
    v_sch_20608   UUID := '53000000-0000-0000-0000-000000000003';

    v_train_22436 UUID := '52000000-0000-0000-0000-000000000004';
    v_sch_22436   UUID := '53000000-0000-0000-0000-000000000004';

    v_train_12301 UUID := '52000000-0000-0000-0000-000000000005';
    v_sch_12301   UUID := '53000000-0000-0000-0000-000000000005';

    v_train_12004 UUID := '52000000-0000-0000-0000-000000000006';
    v_sch_12004   UUID := '53000000-0000-0000-0000-000000000006';

    v_train_12724 UUID := '52000000-0000-0000-0000-000000000007';
    v_sch_12724   UUID := '53000000-0000-0000-0000-000000000007';

    v_train_12626 UUID := '52000000-0000-0000-0000-000000000008';
    v_sch_12626   UUID := '53000000-0000-0000-0000-000000000008';

    v_train_12260 UUID := '52000000-0000-0000-0000-000000000009';
    v_sch_12260   UUID := '53000000-0000-0000-0000-000000000009';

    v_train_22159 UUID := '52000000-0000-0000-0000-000000000010';
    v_sch_22159   UUID := '53000000-0000-0000-0000-000000000010';

BEGIN
    -- ------------------------------------------------------------------------
    -- 1. OFFICIAL RAILWAY ZONES
    -- ------------------------------------------------------------------------
    INSERT INTO railway_zones (zone_id, zone_code, zone_name, status) VALUES
        (v_nr_zone_id,   'NR',   'Northern Railway',              'ACTIVE'),
        (v_wr_zone_id,   'WR',   'Western Railway',               'ACTIVE'),
        (v_sr_zone_id,   'SR',   'Southern Railway',              'ACTIVE'),
        (v_swr_zone_id,  'SWR',  'South Western Railway',         'ACTIVE'),
        (v_er_zone_id,   'ER',   'Eastern Railway',               'ACTIVE'),
        (v_cr_zone_id,   'CR',   'Central Railway',               'ACTIVE'),
        (v_ncr_zone_id,  'NCR',  'North Central Railway',         'ACTIVE'),
        (v_scr_zone_id,  'SCR',  'South Central Railway',         'ACTIVE'),
        (v_ser_zone_id,  'SER',  'South Eastern Railway',         'ACTIVE'),
        (v_ecor_zone_id, 'ECOR', 'East Coast Railway',            'ACTIVE'),
        (v_nwr_zone_id,  'NWR',  'North Western Railway',         'ACTIVE'),
        (v_wcr_zone_id,  'WCR',  'West Central Railway',          'ACTIVE')
    ON CONFLICT (zone_code) DO NOTHING;

    -- ------------------------------------------------------------------------
    -- 2. OFFICIAL RAILWAY DIVISIONS
    -- ------------------------------------------------------------------------
    INSERT INTO railway_divisions (division_id, zone_id, division_code, division_name, status) VALUES
        (v_dli_div_id,  v_nr_zone_id,  'DLI',  'Delhi Division',              'ACTIVE'),
        (v_bct_div_id,  v_wr_zone_id,  'MMCT', 'Mumbai Western Division',     'ACTIVE'),
        (v_mas_div_id,  v_sr_zone_id,  'MAS',  'Chennai Division',            'ACTIVE'),
        (v_sbc_div_id,  v_swr_zone_id, 'SBC',  'Bengaluru Division',          'ACTIVE'),
        (v_hwh_div_id,  v_er_zone_id,  'HWH',  'Howrah Division',             'ACTIVE'),
        (v_cstm_div_id, v_cr_zone_id,  'CSMT', 'Mumbai Central Division',     'ACTIVE'),
        (v_ald_div_id,  v_ncr_zone_id, 'PRYJ', 'Prayagraj Division',          'ACTIVE'),
        (v_sc_div_id,   v_scr_zone_id, 'SC',   'Secunderabad Division',       'ACTIVE'),
        (v_tvc_div_id,  v_sr_zone_id,  'TVC',  'Thiruvananthapuram Division', 'ACTIVE'),
        (v_jp_div_id,   v_nwr_zone_id, 'JP',   'Jaipur Division',             'ACTIVE'),
        (v_bpl_div_id,  v_wcr_zone_id, 'BPL',  'Bhopal Division',             'ACTIVE')
    ON CONFLICT (division_code) DO NOTHING;

    -- ------------------------------------------------------------------------
    -- 3. 50+ REAL ACCURATE RAILWAY STATIONS
    -- ------------------------------------------------------------------------
    INSERT INTO stations (
        station_id, station_code, station_name, alternate_names, city, state,
        country, latitude, longitude, zone_id, division_id, station_category,
        code_verified, status
    ) VALUES
    -- === Central Stations ===
    (
        v_stn_ndls, 'NDLS', 'New Delhi Central', '["New Delhi", "Delhi Central", "DLI"]'::jsonb,
        'New Delhi', 'Delhi', 'India', 28.641529, 77.220977, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_mmct, 'MMCT', 'Mumbai Central', '["Bombay Central", "BCT", "Mumbai Western"]'::jsonb,
        'Mumbai', 'Maharashtra', 'India', 18.969632, 72.819383, v_wr_zone_id, v_bct_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_cnb, 'CNB', 'Kanpur Central', '["Kanpur Jn", "Cawnpore Central"]'::jsonb,
        'Kanpur', 'Uttar Pradesh', 'India', 26.454700, 80.350700, v_ncr_zone_id, v_ald_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_mas, 'MAS', 'MGR Chennai Central', '["Chennai Central", "Madras Central"]'::jsonb,
        'Chennai', 'Tamil Nadu', 'India', 13.082680, 80.270718, v_sr_zone_id, v_mas_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_tvc, 'TVC', 'Thiruvananthapuram Central', '["Trivandrum Central", "TVM Central"]'::jsonb,
        'Thiruvananthapuram', 'Kerala', 'India', 8.487100, 76.953000, v_sr_zone_id, v_tvc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),

    -- === Major Terminals ===
    (
        v_stn_cstm, 'CSMT', 'Chhatrapati Shivaji Maharaj Terminus', '["VT", "Victoria Terminus", "Mumbai CSMT"]'::jsonb,
        'Mumbai', 'Maharashtra', 'India', 18.940100, 72.835400, v_cr_zone_id, v_cstm_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'ANVT', 'Anand Vihar Terminal', '["Anand Vihar", "Delhi East"]'::jsonb,
        'Delhi', 'Delhi', 'India', 28.647500, 77.315000, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'BDTS', 'Bandra Terminus', '["Bandra", "Mumbai BDTS"]'::jsonb,
        'Mumbai', 'Maharashtra', 'India', 19.062000, 72.842000, v_wr_zone_id, v_bct_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'LTT', 'Lokmanya Tilak Terminus', '["Kurla Terminus", "Mumbai LTT"]'::jsonb,
        'Mumbai', 'Maharashtra', 'India', 19.069400, 72.890600, v_cr_zone_id, v_cstm_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_hwh, 'HWH', 'Howrah Junction Terminus', '["Howrah Station", "Kolkata HWH"]'::jsonb,
        'Howrah', 'West Bengal', 'India', 22.583900, 88.343400, v_er_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_sda, 'SDAH', 'Sealdah Terminus', '["Sealdah Station", "Kolkata SDAH"]'::jsonb,
        'Kolkata', 'West Bengal', 'India', 22.569700, 88.371300, v_er_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),

    -- === Major Junction Stations ===
    (
        v_stn_pune, 'PUNE', 'Pune Junction', '["Poona", "Pune Jn"]'::jsonb,
        'Pune', 'Maharashtra', 'India', 18.528400, 73.874300, v_cr_zone_id, v_cstm_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_kota, 'KOTA', 'Kota Junction', '["Kota Jn"]'::jsonb,
        'Kota', 'Rajasthan', 'India', 25.221535, 75.864723, v_wcr_zone_id, v_bpl_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_brc, 'BRC', 'Vadodara Junction', '["Baroda Jn", "Vadodara"]'::jsonb,
        'Vadodara', 'Gujarat', 'India', 22.310700, 73.181200, v_wr_zone_id, v_bct_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_st, 'ST', 'Surat Junction', '["Surat", "Surat Jn"]'::jsonb,
        'Surat', 'Gujarat', 'India', 21.204400, 72.840600, v_wr_zone_id, v_bct_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_adi, 'ADI', 'Ahmedabad Junction', '["Kalupur Jn", "Ahmedabad Jn"]'::jsonb,
        'Ahmedabad', 'Gujarat', 'India', 23.027000, 72.601200, v_wr_zone_id, v_bct_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_sbc, 'SBC', 'KSR Bengaluru Junction', '["Bangalore City", "Bengaluru Central"]'::jsonb,
        'Bengaluru', 'Karnataka', 'India', 12.978129, 77.569668, v_swr_zone_id, v_sbc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_mys, 'MYS', 'Mysuru Junction', '["Mysore Jn"]'::jsonb,
        'Mysuru', 'Karnataka', 'India', 12.316400, 76.649800, v_swr_zone_id, v_sbc_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_pryj, 'PRYJ', 'Prayagraj Junction', '["Allahabad Jn", "Prayagraj"]'::jsonb,
        'Prayagraj', 'Uttar Pradesh', 'India', 25.448400, 81.833300, v_ncr_zone_id, v_ald_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_bsb, 'BSB', 'Varanasi Junction', '["Varanasi Cantt", "Kashi"]'::jsonb,
        'Varanasi', 'Uttar Pradesh', 'India', 25.328300, 82.986300, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'DDU', 'Pt. Deen Dayal Upadhyaya Junction', '["Mughalsarai Jn", "MGS"]'::jsonb,
        'Mughalsarai', 'Uttar Pradesh', 'India', 25.279600, 83.118600, v_ecor_zone_id, v_ald_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_lko, 'LKO', 'Lucknow Charbagh Junction', '["Lucknow NR", "Charbagh Jn"]'::jsonb,
        'Lucknow', 'Uttar Pradesh', 'India', 26.832200, 80.923100, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_agc, 'AGC', 'Agra Cantt Junction', '["Agra Cantonment", "Taj Station"]'::jsonb,
        'Agra', 'Uttar Pradesh', 'India', 27.158700, 77.990400, v_ncr_zone_id, v_ald_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'VGLJ', 'Virangana Lakshmibai Jhansi Junction', '["Jhansi Jn", "JHS"]'::jsonb,
        'Jhansi', 'Uttar Pradesh', 'India', 25.448400, 78.568500, v_ncr_zone_id, v_ald_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_gwl, 'GWL', 'Gwalior Junction', '["Gwalior Jn"]'::jsonb,
        'Gwalior', 'Madhya Pradesh', 'India', 26.216300, 78.196300, v_ncr_zone_id, v_ald_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_bpl, 'BPL', 'Bhopal Junction', '["Bhopal Jn"]'::jsonb,
        'Bhopal', 'Madhya Pradesh', 'India', 23.266200, 77.412400, v_wcr_zone_id, v_bpl_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'ET', 'Itarsi Junction', '["Itarsi Jn"]'::jsonb,
        'Itarsi', 'Madhya Pradesh', 'India', 22.613300, 77.762800, v_wcr_zone_id, v_bpl_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'JBP', 'Jabalpur Junction', '["Jabalpur Jn"]'::jsonb,
        'Jabalpur', 'Madhya Pradesh', 'India', 23.161000, 79.957000, v_wcr_zone_id, v_bpl_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_ngp, 'NGP', 'Nagpur Junction', '["Nagpur Jn"]'::jsonb,
        'Nagpur', 'Maharashtra', 'India', 21.152400, 79.088800, v_cr_zone_id, v_cstm_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'BPQ', 'Balharshah Junction', '["Balharshah Jn", "Ballarpur"]'::jsonb,
        'Ballarpur', 'Maharashtra', 'India', 19.851000, 79.352000, v_cr_zone_id, v_cstm_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'BZA', 'Vijayawada Junction', '["Vijayawada Jn", "Bezawada"]'::jsonb,
        'Vijayawada', 'Andhra Pradesh', 'India', 16.518600, 80.619800, v_scr_zone_id, v_sc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'VSKP', 'Visakhapatnam Junction', '["Vizag Jn", "Waltair"]'::jsonb,
        'Visakhapatnam', 'Andhra Pradesh', 'India', 17.721400, 83.287200, v_ecor_zone_id, v_sc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_hyb, 'HYB', 'Hyderabad Deccan Nampally', '["Hyderabad Deccan", "Nampally Station"]'::jsonb,
        'Hyderabad', 'Telangana', 'India', 17.392400, 78.468200, v_scr_zone_id, v_sc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_sc, 'SC', 'Secunderabad Junction', '["Secunderabad Jn"]'::jsonb,
        'Hyderabad', 'Telangana', 'India', 17.433400, 78.504200, v_scr_zone_id, v_sc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'GTL', 'Guntakal Junction', '["Guntakal Jn"]'::jsonb,
        'Guntakal', 'Andhra Pradesh', 'India', 15.168500, 77.375200, v_scr_zone_id, v_sc_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'RU', 'Renigunta Junction', '["Renigunta Jn", "Tirupati Outer"]'::jsonb,
        'Renigunta', 'Andhra Pradesh', 'India', 13.636000, 79.516000, v_scr_zone_id, v_sc_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_pnbe, 'PNBE', 'Patna Junction', '["Patna Jn"]'::jsonb,
        'Patna', 'Bihar', 'India', 25.602200, 85.137600, v_ecor_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'GKP', 'Gorakhpur Junction', '["Gorakhpur Jn"]'::jsonb,
        'Gorakhpur', 'Uttar Pradesh', 'India', 26.759200, 83.381400, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_jp, 'JP', 'Jaipur Junction', '["Pink City", "Jaipur Jn"]'::jsonb,
        'Jaipur', 'Rajasthan', 'India', 26.919600, 75.787800, v_nwr_zone_id, v_jp_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'AII', 'Ajmer Junction', '["Ajmer Jn", "Pushkar Gateway"]'::jsonb,
        'Ajmer', 'Rajasthan', 'India', 26.452600, 74.638700, v_nwr_zone_id, v_jp_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_bkn, 'BKN', 'Bikaner Junction', '["Bikaner Jn"]'::jsonb,
        'Bikaner', 'Rajasthan', 'India', 28.016300, 73.311900, v_nwr_zone_id, v_jp_div_id, 'NSG-2',
        TRUE, 'ACTIVE'
    ),
    (
        v_stn_ers, 'ERS', 'Ernakulam Junction South', '["Kochi Jn", "Cochin South"]'::jsonb,
        'Kochi', 'Kerala', 'India', 9.967800, 76.289700, v_sr_zone_id, v_tvc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'CLT', 'Kozhikode Main', '["Calicut", "Kozhikode Station"]'::jsonb,
        'Kozhikode', 'Kerala', 'India', 11.248300, 75.783900, v_sr_zone_id, v_tvc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'MAQ', 'Mangaluru Central', '["Mangalore Central", "MAQ"]'::jsonb,
        'Mangaluru', 'Karnataka', 'India', 12.865300, 74.842700, v_sr_zone_id, v_sbc_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'CDG', 'Chandigarh Junction', '["Chandigarh Jn"]'::jsonb,
        'Chandigarh', 'Chandigarh', 'India', 30.702000, 76.820000, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'ASR', 'Amritsar Junction', '["Golden Temple Station", "Amritsar Jn"]'::jsonb,
        'Amritsar', 'Punjab', 'India', 31.633900, 74.865600, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'SVDK', 'Shri Mata Vaishno Devi Katra', '["Katra Station", "SVDK Katra"]'::jsonb,
        'Katra', 'Jammu and Kashmir', 'India', 32.992000, 74.932000, v_nr_zone_id, v_dli_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'BBS', 'Bhubaneswar Railway Station', '["Bhubaneswar", "BBS Jn"]'::jsonb,
        'Bhubaneswar', 'Odisha', 'India', 20.266800, 85.843600, v_ecor_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'PURI', 'Puri Terminus', '["Jagannath Puri Station", "Puri"]'::jsonb,
        'Puri', 'Odisha', 'India', 19.813500, 85.831200, v_ecor_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'R', 'Raipur Junction', '["Raipur Jn"]'::jsonb,
        'Raipur', 'Chhattisgarh', 'India', 21.258000, 81.630000, v_ser_zone_id, v_cstm_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    ),
    (
        gen_random_uuid(), 'GHY', 'Guwahati Junction', '["Guwahati Station", "Gateway of Northeast"]'::jsonb,
        'Guwahati', 'Assam', 'India', 26.183000, 91.750000, v_er_zone_id, v_hwh_div_id, 'NSG-1',
        TRUE, 'ACTIVE'
    )
    ON CONFLICT (station_code) DO NOTHING;

    -- ------------------------------------------------------------------------
    -- 4. OFFICIAL TRAIN CATEGORIES & TYPES
    -- ------------------------------------------------------------------------
    INSERT INTO train_categories (category_id, category_code, category_name, description) VALUES
        (v_cat_rajdhani, 'RAJDHANI',     'Rajdhani Express',     'Fully AC Superfast Priority Connecting State Capitals to New Delhi'),
        (v_cat_vb,       'VANDE_BHARAT', 'Vande Bharat Express', 'Semi-High Speed Indigenous EMU Express'),
        (v_cat_shatabdi, 'SHATABDI',     'Shatabdi Express',     'Same-day Return High-Speed Intercity Chair Car Express'),
        (v_cat_duronto,  'DURONTO',      'Duronto Express',      'Point-to-point Non-stop Long-distance Express'),
        (v_cat_sf,       'SUPERFAST',    'Superfast Express',    'Scheduled Express running at >55 km/h avg speed'),
        (v_cat_mail_exp, 'MAIL_EXPRESS', 'Mail / Express',       'Standard Long-Distance Passenger Express')
    ON CONFLICT (category_code) DO NOTHING;

    INSERT INTO train_types (train_type_id, type_code, type_name, description) VALUES
        (v_type_rajdhani,  'RAJDHANI',     'Rajdhani Express',     'Priority Superfast All-AC Sleeper'),
        (v_type_vb,        'VANDE_BHARAT', 'Vande Bharat Express', 'Semi-High Speed Chair Car Trainset'),
        (v_type_shatabdi,  'SHATABDI',     'Shatabdi Express',     'High Speed Day Chair Car'),
        (v_type_duronto,   'DURONTO',      'Duronto Express',      'Non-stop Long-distance Express'),
        (v_type_superfast, 'SUPERFAST',    'Superfast Express',    'National Intercity Superfast Service')
    ON CONFLICT (type_code) DO NOTHING;

    -- ------------------------------------------------------------------------
    -- 5. OFFICIAL COACH CLASSES & CONFIGURATIONS
    -- ------------------------------------------------------------------------
    INSERT INTO coach_classes (coach_class_id, class_code, class_name, accommodation_type, fare_category) VALUES
        (v_class_1a, '1A', 'AC First Class (1A)',          'SLEEPING', 'PREMIUM_AC'),
        (v_class_2a, '2A', 'AC 2 Tier (2A)',               'SLEEPING', 'AC'),
        (v_class_3a, '3A', 'AC 3 Tier (3A)',               'SLEEPING', 'AC'),
        (v_class_3e, '3E', 'AC 3 Economy (3E)',            'SLEEPING', 'AC_ECONOMY'),
        (v_class_sl, 'SL', 'Sleeper Class (SL)',           'SLEEPING', 'NON_AC'),
        (v_class_cc, 'CC', 'AC Chair Car (CC)',            'SEATING',  'CHAIR_CAR'),
        (v_class_ec, 'EC', 'Executive Chair Car (EC)',     'SEATING',  'PREMIUM_CHAIR_CAR'),
        (v_class_2s, '2S', 'Second Sitting (2S)',          'SEATING',  'NON_AC')
    ON CONFLICT (class_code) DO NOTHING;

    INSERT INTO coach_types (coach_type_id, type_code, type_name, description) VALUES
        (v_ctype_lhb, 'LHB',     'Linke Hofmann Busch Coach', 'Stainless steel high-speed modern rake'),
        (v_ctype_vb,  'TRAIN18', 'Train 18 / Vande Bharat',   'Aero-dynamic distributed traction EMU rake'),
        (v_ctype_icf, 'ICF',     'Integral Coach Factory',    'Standard steel coach rake')
    ON CONFLICT (type_code) DO NOTHING;

    INSERT INTO coach_configurations (
        configuration_id, configuration_code, configuration_name, coach_class_id, coach_type_id, capacity
    ) VALUES
        (v_cfg_1a, 'LHB_1A_24', 'LHB AC First Class (24 Berths)',           v_class_1a, v_ctype_lhb, 24),
        (v_cfg_2a, 'LHB_2A_54', 'LHB AC 2-Tier (54 Berths)',                v_class_2a, v_ctype_lhb, 54),
        (v_cfg_3a, 'LHB_3A_72', 'LHB AC 3-Tier (72 Berths)',                v_class_3a, v_ctype_lhb, 72),
        (v_cfg_sl, 'LHB_SL_80', 'LHB Sleeper Class (80 Berths)',            v_class_sl, v_ctype_lhb, 80),
        (v_cfg_cc, 'VB_CC_78',  'Vande Bharat AC Chair Car (78 Seats)',      v_class_cc, v_ctype_vb,  78),
        (v_cfg_ec, 'VB_EC_52',  'Vande Bharat Executive Chair Car (52 Seats)', v_class_ec, v_ctype_vb, 52)
    ON CONFLICT (configuration_code) DO NOTHING;

    -- ========================================================================
    -- 6. ROUTE 1: NDLS -> MMCT (New Delhi - Mumbai Central Main Line)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_ndls_mmct, 'New Delhi - Mumbai Central Main Line (via Kota, Vadodara, Surat)', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_ndls_mmct, v_r_ndls_mmct, 1, v_stn_ndls, v_stn_mmct,
        1386.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_ndls_mmct, v_stn_ndls, 1, NULL,       '16:55:00', 0, 0, 0,  0.00,    'ORIGIN',   '3'),
        (gen_random_uuid(), v_rv_ndls_mmct, v_stn_kota, 2, '21:30:00', '21:40:00', 0, 0, 10, 465.00,  'REGULAR',  '1A'),
        (gen_random_uuid(), v_rv_ndls_mmct, v_stn_brc,  3, '03:15:00', '03:25:00', 1, 1, 10, 992.00,  'REGULAR',  '2'),
        (gen_random_uuid(), v_rv_ndls_mmct, v_stn_st,   4, '05:10:00', '05:15:00', 1, 1, 5,  1122.00, 'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_mmct, v_stn_mmct, 5, '08:35:00', NULL,       1, 1, 0,  1386.00, 'TERMINAL', '1')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12952, '12952', 'Mumbai Central Tejas Rajdhani Express',
        v_cat_rajdhani, v_type_rajdhani, v_stn_ndls, v_stn_mmct, v_r_ndls_mmct, 1386.00,
        'Premier Tejas Rajdhani connecting NDLS with MMCT via Kota, Vadodara, Surat',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12952, v_train_12952, v_rv_ndls_mmct, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12952, 1), (v_sch_12952, 2), (v_sch_12952, 3), (v_sch_12952, 4),
        (v_sch_12952, 5), (v_sch_12952, 6), (v_sch_12952, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 7. ROUTE 2: MMCT -> NDLS (Return Mumbai Central - New Delhi Main Line)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_mmct_ndls, 'Mumbai Central - New Delhi Main Line Return (via Surat, Vadodara, Kota)', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_mmct_ndls, v_r_mmct_ndls, 1, v_stn_mmct, v_stn_ndls,
        1386.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_mmct_ndls, v_stn_mmct, 1, NULL,       '17:00:00', 0, 0, 0,  0.00,    'ORIGIN',   '1'),
        (gen_random_uuid(), v_rv_mmct_ndls, v_stn_st,   2, '19:43:00', '19:48:00', 0, 0, 5,  263.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_mmct_ndls, v_stn_brc,  3, '21:18:00', '21:28:00', 0, 0, 10, 393.00,  'REGULAR',  '2'),
        (gen_random_uuid(), v_rv_mmct_ndls, v_stn_kota, 4, '03:15:00', '03:20:00', 1, 1, 5,  920.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_mmct_ndls, v_stn_ndls, 5, '08:32:00', NULL,       1, 1, 0,  1386.00, 'TERMINAL', '2')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12951, '12951', 'New Delhi Tejas Rajdhani Express',
        v_cat_rajdhani, v_type_rajdhani, v_stn_mmct, v_stn_ndls, v_r_mmct_ndls, 1386.00,
        'Premier return Tejas Rajdhani running overnight from Mumbai Central to New Delhi',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12951, v_train_12951, v_rv_mmct_ndls, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12951, 1), (v_sch_12951, 2), (v_sch_12951, 3), (v_sch_12951, 4),
        (v_sch_12951, 5), (v_sch_12951, 6), (v_sch_12951, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 8. ROUTE 3: MYS -> SBC -> MAS (South Rail Corridor)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_mys_mas, 'Mysuru - Bengaluru - Chennai South Rail Corridor', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_mys_mas, v_r_mys_mas, 1, v_stn_mys, v_stn_mas,
        496.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_mys_mas, v_stn_mys, 1, NULL,       '06:00:00', 0, 0, 0, 0.00,   'ORIGIN',   '1'),
        (gen_random_uuid(), v_rv_mys_mas, v_stn_sbc, 2, '07:45:00', '07:50:00', 0, 0, 5, 137.00, 'REGULAR',  '7'),
        (gen_random_uuid(), v_rv_mys_mas, v_stn_mas, 3, '12:25:00', NULL,       0, 0, 0, 496.00, 'TERMINAL', '2A')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_20608, '20608', 'MGR Chennai Central Vande Bharat Express',
        v_cat_vb, v_type_vb, v_stn_mys, v_stn_mas, v_r_mys_mas, 496.00,
        'Flagship South India semi-high speed express connecting Mysuru and Bengaluru with Chennai',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_20608, v_train_20608, v_rv_mys_mas, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_20608, 1), (v_sch_20608, 2), (v_sch_20608, 4),
        (v_sch_20608, 5), (v_sch_20608, 6), (v_sch_20608, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 9. ROUTE 4: NDLS -> CNB -> PRYJ -> BSB (Northern Holy City Trunk)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_ndls_bsb, 'New Delhi - Varanasi Holy City Trunk Route (via Kanpur, Prayagraj)', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_ndls_bsb, v_r_ndls_bsb, 1, v_stn_ndls, v_stn_bsb,
        759.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_ndls_bsb, v_stn_ndls, 1, NULL,       '06:00:00', 0, 0, 0, 0.00,   'ORIGIN',   '16'),
        (gen_random_uuid(), v_rv_ndls_bsb, v_stn_cnb,  2, '10:08:00', '10:10:00', 0, 0, 2, 440.00, 'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_bsb, v_stn_pryj, 3, '12:08:00', '12:10:00', 0, 0, 2, 635.00, 'REGULAR',  '6'),
        (gen_random_uuid(), v_rv_ndls_bsb, v_stn_bsb,  4, '14:00:00', NULL,       0, 0, 0, 759.00, 'TERMINAL', '1')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_22436, '22436', 'Varanasi Vande Bharat Express',
        v_cat_vb, v_type_vb, v_stn_ndls, v_stn_bsb, v_r_ndls_bsb, 759.00,
        'Flagship Vande Bharat Express connecting the National Capital to Varanasi',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_22436, v_train_22436, v_rv_ndls_bsb, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_22436, 1), (v_sch_22436, 2), (v_sch_22436, 3),
        (v_sch_22436, 5), (v_sch_22436, 6), (v_sch_22436, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 10. ROUTE 5: HWH -> PRYJ -> CNB -> NDLS (Eastern Grand Chord Route)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_hwh_ndls, 'Howrah - New Delhi Grand Chord Route (via Prayagraj, Kanpur)', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_hwh_ndls, v_r_hwh_ndls, 1, v_stn_hwh, v_stn_ndls,
        1451.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_hwh_ndls, v_stn_hwh,  1, NULL,       '16:50:00', 0, 0, 0,  0.00,    'ORIGIN',   '9'),
        (gen_random_uuid(), v_rv_hwh_ndls, v_stn_pryj, 2, '00:45:00', '00:50:00', 1, 1, 5,  927.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_hwh_ndls, v_stn_cnb,  3, '02:30:00', '02:35:00', 1, 1, 5,  1122.00, 'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_hwh_ndls, v_stn_ndls, 4, '10:05:00', NULL,       1, 1, 0,  1451.00, 'TERMINAL', '5')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12301, '12301', 'Howrah - New Delhi Rajdhani Express',
        v_cat_rajdhani, v_type_rajdhani, v_stn_hwh, v_stn_ndls, v_r_hwh_ndls, 1451.00,
        'The King of Eastern Railways connecting Kolkata and New Delhi overnight',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12301, v_train_12301, v_rv_hwh_ndls, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12301, 1), (v_sch_12301, 2), (v_sch_12301, 3),
        (v_sch_12301, 4), (v_sch_12301, 5), (v_sch_12301, 6)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 11. ROUTE 6: NDLS -> CNB -> LKO (Avadh Capital High Speed Line)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_ndls_lko, 'New Delhi - Lucknow Avadh Intercity Route', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_ndls_lko, v_r_ndls_lko, 1, v_stn_ndls, v_stn_lko,
        512.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_ndls_lko, v_stn_ndls, 1, NULL,       '06:10:00', 0, 0, 0, 0.00,   'ORIGIN',   '1'),
        (gen_random_uuid(), v_rv_ndls_lko, v_stn_cnb,  2, '11:20:00', '11:25:00', 0, 0, 5, 440.00, 'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_lko, v_stn_lko,  3, '12:40:00', NULL,       0, 0, 0, 512.00, 'TERMINAL', '6')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12004, '12004', 'Lucknow Swarna Shatabdi Express',
        v_cat_shatabdi, v_type_shatabdi, v_stn_ndls, v_stn_lko, v_r_ndls_lko, 512.00,
        'Premier Day Intercity connecting Capital of India with Capital of Uttar Pradesh',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12004, v_train_12004, v_rv_ndls_lko, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12004, 1), (v_sch_12004, 2), (v_sch_12004, 3), (v_sch_12004, 4),
        (v_sch_12004, 5), (v_sch_12004, 6), (v_sch_12004, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 12. ROUTE 7: NDLS -> AGC -> GWL -> BPL -> NGP -> SC -> HYB (Grand Trunk South)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_ndls_hyb, 'New Delhi - Hyderabad Grand Trunk Route', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_ndls_hyb, v_r_ndls_hyb, 1, v_stn_ndls, v_stn_hyb,
        1675.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_ndls, 1, NULL,       '16:00:00', 0, 0, 0,  0.00,    'ORIGIN',   '7'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_agc,  2, '18:05:00', '18:07:00', 0, 0, 2,  195.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_gwl,  3, '19:58:00', '20:00:00', 0, 0, 2,  313.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_bpl,  4, '01:20:00', '01:30:00', 1, 1, 10, 705.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_ngp,  5, '06:50:00', '06:55:00', 1, 1, 5,  1095.00, 'REGULAR',  '2'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_sc,   6, '17:10:00', '17:15:00', 1, 1, 5,  1666.00, 'REGULAR',  '8'),
        (gen_random_uuid(), v_rv_ndls_hyb, v_stn_hyb,  7, '18:40:00', NULL,       1, 1, 0,  1675.00, 'TERMINAL', '5')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12724, '12724', 'Telangana Superfast Express',
        v_cat_sf, v_type_superfast, v_stn_ndls, v_stn_hyb, v_r_ndls_hyb, 1675.00,
        'Connecting National Capital to Hyderabad Deccan via Agra, Gwalior, Bhopal, Nagpur, Secunderabad',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12724, v_train_12724, v_rv_ndls_hyb, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12724, 1), (v_sch_12724, 2), (v_sch_12724, 3), (v_sch_12724, 4),
        (v_sch_12724, 5), (v_sch_12724, 6), (v_sch_12724, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 13. ROUTE 8: NDLS -> AGC -> BPL -> NGP -> ERS -> TVC (Kerala National Corridor)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_ndls_tvc, 'New Delhi - Thiruvananthapuram Kerala National Corridor', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_ndls_tvc, v_r_ndls_tvc, 1, v_stn_ndls, v_stn_tvc,
        3030.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_ndls, 1, NULL,       '20:10:00', 0, 0, 0,  0.00,    'ORIGIN',   '3'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_agc,  2, '22:20:00', '22:25:00', 0, 0, 5,  195.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_gwl,  3, '23:56:00', '23:58:00', 0, 0, 2,  313.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_bpl,  4, '05:30:00', '05:35:00', 1, 1, 5,  705.00,  'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_ngp,  5, '11:45:00', '11:50:00', 1, 1, 5,  1095.00, 'REGULAR',  '2'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_ers,  6, '16:55:00', '17:00:00', 2, 2, 5,  2810.00, 'REGULAR',  '3'),
        (gen_random_uuid(), v_rv_ndls_tvc, v_stn_tvc,  7, '22:10:00', NULL,       2, 2, 0,  3030.00, 'TERMINAL', '1')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12626, '12626', 'Kerala Superfast Express',
        v_cat_sf, v_type_superfast, v_stn_ndls, v_stn_tvc, v_r_ndls_tvc, 3030.00,
        'Legendary daily Superfast connecting North India to Kerala traversing 3030 km across 7 states',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12626, v_train_12626, v_rv_ndls_tvc, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12626, 1), (v_sch_12626, 2), (v_sch_12626, 3), (v_sch_12626, 4),
        (v_sch_12626, 5), (v_sch_12626, 6), (v_sch_12626, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 14. ROUTE 9: BKN -> JP -> CNB -> PRYJ -> SDAH (Rajasthan - Bengal Duronto)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_bkn_sda, 'Bikaner - Sealdah Duronto Express Route', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_bkn_sda, v_r_bkn_sda, 1, v_stn_bkn, v_stn_sda,
        1920.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_bkn_sda, v_stn_bkn,  1, NULL,       '12:15:00', 0, 0, 0,  0.00,    'ORIGIN',   '1'),
        (gen_random_uuid(), v_rv_bkn_sda, v_stn_jp,   2, '18:40:00', '18:50:00', 0, 0, 10, 378.00,  'REGULAR',  '3'),
        (gen_random_uuid(), v_rv_bkn_sda, v_stn_cnb,  3, '02:00:00', '02:05:00', 1, 1, 5,  1010.00, 'REGULAR',  '1'),
        (gen_random_uuid(), v_rv_bkn_sda, v_stn_pryj, 4, '03:45:00', '03:50:00', 1, 1, 5,  1205.00, 'REGULAR',  '4'),
        (gen_random_uuid(), v_rv_bkn_sda, v_stn_sda,  5, '13:15:00', NULL,       1, 1, 0,  1920.00, 'TERMINAL', '9A')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_12260, '12260', 'Sealdah AC Duronto Express',
        v_cat_duronto, v_type_duronto, v_stn_bkn, v_stn_sda, v_r_bkn_sda, 1920.00,
        'All-AC Non-stop superfast service traversing Rajasthan and UP to Kolkata (Sealdah)',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_12260, v_train_12260, v_rv_bkn_sda, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_12260, 1), (v_sch_12260, 3), (v_sch_12260, 4), (v_sch_12260, 6)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 15. ROUTE 10: CSMT -> PUNE -> MAS (Deccan - Chennai South Trunk)
    -- ========================================================================
    INSERT INTO train_routes (route_id, route_name, status)
    VALUES (v_r_csmt_mas, 'Mumbai CSMT - Chennai Central Deccan-Carnatic Route', 'PUBLISHED')
    ON CONFLICT (route_id) DO NOTHING;

    INSERT INTO train_route_versions (
        route_version_id, route_id, version_number, source_station_id, destination_station_id,
        total_distance_km, effective_from, status, published_at
    ) VALUES (
        v_rv_csmt_mas, v_r_csmt_mas, 1, v_stn_cstm, v_stn_mas,
        1281.00, '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (route_id, version_number) DO NOTHING;

    INSERT INTO train_route_stops (
        route_stop_id, route_version_id, station_id, stop_sequence,
        arrival_time, departure_time, arrival_day_offset, departure_day_offset,
        halt_minutes, distance_from_source_km, stop_type, platform_label
    ) VALUES
        (gen_random_uuid(), v_rv_csmt_mas, v_stn_cstm, 1, NULL,       '13:15:00', 0, 0, 0, 0.00,    'ORIGIN',   '9'),
        (gen_random_uuid(), v_rv_csmt_mas, v_stn_pune, 2, '16:20:00', '16:25:00', 0, 0, 5, 192.00,  'REGULAR',  '2'),
        (gen_random_uuid(), v_rv_csmt_mas, v_stn_mas,  3, '11:15:00', NULL,       1, 1, 0, 1281.00, 'TERMINAL', '4')
    ON CONFLICT (route_version_id, stop_sequence) DO NOTHING;

    INSERT INTO trains (
        train_id, train_number, train_name, category_id, train_type_id,
        source_station_id, destination_station_id, route_id, total_distance_km,
        description, status, effective_from
    ) VALUES (
        v_train_22159, '22159', 'Mumbai CSMT - Chennai Central Express',
        v_cat_sf, v_type_superfast, v_stn_cstm, v_stn_mas, v_r_csmt_mas, 1281.00,
        'Direct express connecting Mumbai CSMT to MGR Chennai Central via Pune, Solapur, Guntakal',
        'ACTIVE', '2026-01-01'
    ) ON CONFLICT (train_number) DO NOTHING;

    INSERT INTO train_schedules (
        schedule_id, train_id, route_version_id, version_number, timezone,
        effective_from, status, published_at
    ) VALUES (
        v_sch_22159, v_train_22159, v_rv_csmt_mas, 1, 'Asia/Kolkata',
        '2026-01-01', 'PUBLISHED', CURRENT_TIMESTAMP
    ) ON CONFLICT (train_id, version_number) DO NOTHING;

    INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES
        (v_sch_22159, 1), (v_sch_22159, 2), (v_sch_22159, 3), (v_sch_22159, 4),
        (v_sch_22159, 5), (v_sch_22159, 6), (v_sch_22159, 7)
    ON CONFLICT DO NOTHING;

    -- ========================================================================
    -- 16. REALISTIC IRCTC FARE ENGINE RULES (Telescopic Distance Pricing)
    -- ========================================================================
    IF EXISTS (SELECT 1 FROM users LIMIT 1) THEN
        DECLARE
            v_sys_user UUID;
            v_fr_id UUID;
        BEGIN
            SELECT user_id INTO v_sys_user FROM users ORDER BY created_at ASC LIMIT 1;

            -- 12952 Tejas Rajdhani 3A (Base ₹450 + ₹1.35/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '12952 Rajdhani 3A Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_12952,
                v_class_3a, 'BASE_PLUS_DISTANCE', 450.00, 1.3500, 10,
                '2026-01-01', 'Official IRCTC Rajdhani 3A tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );

            -- 12952 Tejas Rajdhani 2A (Base ₹680 + ₹2.10/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '12952 Rajdhani 2A Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_12952,
                v_class_2a, 'BASE_PLUS_DISTANCE', 680.00, 2.1000, 10,
                '2026-01-01', 'Official IRCTC Rajdhani 2A tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );

            -- 20608 Vande Bharat CC (Base ₹220 + ₹1.55/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '20608 Vande Bharat CC Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_20608,
                v_class_cc, 'BASE_PLUS_DISTANCE', 220.00, 1.5500, 10,
                '2026-01-01', 'Official Vande Bharat CC tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );

            -- 22436 Varanasi Vande Bharat CC (Base ₹240 + ₹1.60/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '22436 Vande Bharat CC Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_22436,
                v_class_cc, 'BASE_PLUS_DISTANCE', 240.00, 1.6000, 10,
                '2026-01-01', 'Official Vande Bharat NDLS-BSB CC tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );

            -- 12724 Telangana Express Sleeper (Base ₹120 + ₹0.48/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '12724 Telangana Exp SL Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_12724,
                v_class_sl, 'BASE_PLUS_DISTANCE', 120.00, 0.4800, 10,
                '2026-01-01', 'Standard Superfast Sleeper Tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );

            -- 12626 Kerala Express 3A (Base ₹380 + ₹1.15/km)
            v_fr_id := gen_random_uuid();
            INSERT INTO fare_rules (fare_rule_id, rule_name, created_by) VALUES (v_fr_id, '12626 Kerala Exp 3A Fare', v_sys_user);
            INSERT INTO fare_rule_versions (
                fare_rule_version_id, fare_rule_id, version_number, status, train_id,
                coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                effective_from, change_reason, created_by, updated_by,
                submitted_at, approved_by, approved_at, published_by, published_at
            ) VALUES (
                gen_random_uuid(), v_fr_id, 1, 'PUBLISHED', v_train_12626,
                v_class_3a, 'BASE_PLUS_DISTANCE', 380.00, 1.1500, 10,
                '2026-01-01', 'Standard Mail/Express 3A Tariff', v_sys_user, v_sys_user,
                CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP, v_sys_user, CURRENT_TIMESTAMP
            );
        END;
    END IF;

END $$;
