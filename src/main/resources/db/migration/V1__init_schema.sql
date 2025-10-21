CREATE TABLE IF NOT EXISTS businesses (
    business_id   VARCHAR(64) PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    address       VARCHAR(255),
    city          VARCHAR(120),
    state         VARCHAR(10),
    postal_code   VARCHAR(20),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    stars         DOUBLE PRECISION,
    review_count  INTEGER,
    is_open       BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS business_categories (
    business_id VARCHAR(64) NOT NULL REFERENCES businesses(business_id) ON DELETE CASCADE,
    category    VARCHAR(120) NOT NULL,
    PRIMARY KEY (business_id, category)
);

CREATE INDEX IF NOT EXISTS idx_business_categories_category ON business_categories(category);

CREATE TABLE IF NOT EXISTS users (
    user_id        VARCHAR(64) PRIMARY KEY,
    name           VARCHAR(255),
    review_count   INTEGER,
    average_stars  DOUBLE PRECISION,
    yelping_since  DATE
);

-- Note: reviews.user_id intentionally has no FK to users. The ingestion
-- pipeline loads reviews independently of the (optional) users table, so
-- review data isn't dropped if a referenced user hasn't been ingested.
CREATE TABLE IF NOT EXISTS reviews (
    review_id     VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    business_id   VARCHAR(64) NOT NULL REFERENCES businesses(business_id) ON DELETE CASCADE,
    stars         DOUBLE PRECISION,
    review_date   TIMESTAMP,
    useful_count  INTEGER
);

CREATE INDEX IF NOT EXISTS idx_review_user ON reviews(user_id);
CREATE INDEX IF NOT EXISTS idx_review_business ON reviews(business_id);
CREATE INDEX IF NOT EXISTS idx_business_city ON businesses(city);
CREATE INDEX IF NOT EXISTS idx_business_stars ON businesses(stars);
