-- V7: Seed seller users, seller metrics, and assign sellers to catalog items
-- Provides the data foundation needed for admin ranking-strategy functionality
-- Compatible with MySQL 8 and H2 (MySQL mode)

-- Create two seller users (force_password_reset=TRUE so credentials must be changed on first login)
-- BCrypt hash is a placeholder — password reset is required before any access is possible
INSERT INTO users (username, password_hash, email, full_name, seller_credit_score, force_password_reset, enabled, locked, failed_attempts, created_at, updated_at)
VALUES ('seller_alpha',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'alpha@reclaim-sellers.example', 'Alpha Recycling Co.', 85.00,
        TRUE, TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (username, password_hash, email, full_name, seller_credit_score, force_password_reset, enabled, locked, failed_attempts, created_at, updated_at)
VALUES ('seller_beta',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'beta@reclaim-sellers.example', 'Beta Resale LLC', 55.00,
        TRUE, TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Assign ROLE_USER to both sellers
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'seller_alpha' AND r.name = 'ROLE_USER';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'seller_beta' AND r.name = 'ROLE_USER';

-- Seller metrics: alpha is high-quality, beta is moderate
INSERT INTO seller_metrics (seller_id, credit_score, positive_rate, total_transactions,
                            positive_transactions, average_rating, recent_review_quality, updated_at)
SELECT id, 85.00, 0.9200, 50, 46, 4.50, 4.30, CURRENT_TIMESTAMP
FROM users WHERE username = 'seller_alpha';

INSERT INTO seller_metrics (seller_id, credit_score, positive_rate, total_transactions,
                            positive_transactions, average_rating, recent_review_quality, updated_at)
SELECT id, 55.00, 0.7000, 20, 14, 3.20, 2.80, CURRENT_TIMESTAMP
FROM users WHERE username = 'seller_beta';

-- Assign seller_alpha to higher-value items (Electronics + Furniture)
UPDATE recycling_items SET seller_id = (SELECT id FROM users WHERE username = 'seller_alpha')
WHERE title IN (
    'Apple iPhone 12 64GB Space Gray',
    'Dell XPS 13 Laptop Intel Core i7',
    'Sony WH-1000XM4 Noise Cancelling Headphones',
    'Standing Desk Electric Height Adjustable',
    'Mid-Century Modern Accent Chair Walnut'
);

-- Assign seller_beta to remaining Electronics, Appliances, and Sports Equipment
UPDATE recycling_items SET seller_id = (SELECT id FROM users WHERE username = 'seller_beta')
WHERE title IN (
    'Samsung 4K Smart TV 43 Inch',
    'Broken HP Chromebook for Parts',
    'KitchenAid Artisan Stand Mixer 5 Qt Empire Red',
    'Dyson V11 Cordless Vacuum Cleaner',
    'Trek FX 3 Disc Hybrid Bicycle 54cm',
    'Bowflex SelectTech 552 Adjustable Dumbbells Pair'
);

-- Remaining items (Clothing, Books, Instant Pot, some Furniture) keep NULL seller_id
-- to preserve mixed-ranking behavior

-- Seed a default ranking strategy (inactive — admin must explicitly activate)
INSERT INTO ranking_strategy_versions (version_label, credit_score_weight, positive_rate_weight,
                                       review_quality_weight, min_credit_score_threshold,
                                       min_positive_rate_threshold, active, created_at)
VALUES ('default-v1', 0.4000, 0.3500, 0.2500, 0.00, 0.0000, FALSE, CURRENT_TIMESTAMP);
