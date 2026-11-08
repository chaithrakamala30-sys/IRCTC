ALTER TABLE master_data_audit_logs
    DROP CONSTRAINT master_data_audit_action_check;

ALTER TABLE master_data_audit_logs
    ADD CONSTRAINT master_data_audit_action_check
        CHECK (action IN (
            'CREATED',
            'UPDATED',
            'STATUS_CHANGED',
            'VERSION_CREATED',
            'PUBLISHED',
            'RETIRED',
            'EFFECTIVE_RANGE_CLOSED'
        ));
