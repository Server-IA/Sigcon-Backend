CREATE TABLE IF NOT EXISTS third_contact (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL,
    position VARCHAR(255),
    phone VARCHAR(12),
    email VARCHAR(255),
    contact_person VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_third_contact_third_party_id
        FOREIGN KEY (third_party_id) REFERENCES third_parties(id)
);