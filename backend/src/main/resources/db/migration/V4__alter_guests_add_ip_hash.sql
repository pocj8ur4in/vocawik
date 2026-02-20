ALTER TABLE guests
    ADD COLUMN ip_hash VARCHAR(64);

UPDATE guests
SET ip_hash = md5(uuid) || md5(uuid)
WHERE ip_hash IS NULL;

ALTER TABLE guests
    ALTER COLUMN ip_hash SET NOT NULL;

CREATE UNIQUE INDEX uk_guests_ip_hash
    ON guests (ip_hash);
