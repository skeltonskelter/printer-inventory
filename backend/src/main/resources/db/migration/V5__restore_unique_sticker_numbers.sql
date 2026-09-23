CREATE UNIQUE INDEX uk_printers_sticker ON printers (upper(btrim(sticker_number)));
