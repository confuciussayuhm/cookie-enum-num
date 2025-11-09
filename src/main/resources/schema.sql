-- Cookie Information Database Schema
-- Version: 1.0

-- Main cookies table
CREATE TABLE IF NOT EXISTS cookies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    vendor TEXT,
    category TEXT NOT NULL,
    purpose TEXT,
    privacy_impact TEXT,
    is_third_party BOOLEAN DEFAULT 0,
    typical_expiration TEXT,
    common_domains TEXT, -- JSON array stored as text
    notes TEXT,
    confidence_score REAL DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source TEXT DEFAULT 'ai' -- 'ai', 'manual', 'imported'
);

-- Cookie patterns for matching variations (e.g., _ga_*)
CREATE TABLE IF NOT EXISTS cookie_patterns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern TEXT NOT NULL UNIQUE,
    cookie_id INTEGER NOT NULL,
    FOREIGN KEY (cookie_id) REFERENCES cookies(id) ON DELETE CASCADE
);

-- AI query cache to track what we've already asked
CREATE TABLE IF NOT EXISTS ai_query_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cookie_name TEXT NOT NULL,
    domain TEXT,
    query_hash TEXT UNIQUE NOT NULL, -- MD5 of name+domain
    raw_response TEXT, -- Full AI JSON response
    queried_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User corrections when AI gets it wrong
CREATE TABLE IF NOT EXISTS user_corrections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cookie_id INTEGER NOT NULL,
    field_name TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    corrected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cookie_id) REFERENCES cookies(id) ON DELETE CASCADE
);

-- Extension settings
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_cookie_name ON cookies(name);
CREATE INDEX IF NOT EXISTS idx_cookie_category ON cookies(category);
CREATE INDEX IF NOT EXISTS idx_cookie_vendor ON cookies(vendor);
CREATE INDEX IF NOT EXISTS idx_query_hash ON ai_query_cache(query_hash);
CREATE INDEX IF NOT EXISTS idx_pattern_cookie ON cookie_patterns(cookie_id);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS update_cookie_timestamp
AFTER UPDATE ON cookies
FOR EACH ROW
BEGIN
    UPDATE cookies SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.id;
END;

-- Insert schema version
INSERT OR IGNORE INTO settings (key, value) VALUES ('schema_version', '1.0');
INSERT OR IGNORE INTO settings (key, value) VALUES ('created_at', datetime('now'));
