-- P0.1 only establishes migration ownership. Product tables belong to P0.2.
CREATE TABLE foundation_metadata (
    id INT PRIMARY KEY,
    foundation_version VARCHAR(20) NOT NULL,
    initialized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO foundation_metadata (id, foundation_version) VALUES (1, 'P0.1');
