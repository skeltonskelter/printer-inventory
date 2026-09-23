ALTER TABLE printers DROP CONSTRAINT printers_status_check;

ALTER TABLE printers
    ADD CONSTRAINT printers_status_check CHECK (status IN (
        'ACTIVE', 'UNDER_REPAIR', 'FOR_REPAIR', 'STORAGE', 'RETIRED'
    ));
