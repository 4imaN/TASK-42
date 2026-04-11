-- V5: Seed catalog with 20 recycling items (no seller_id, so seller_id is NULL)
-- Compatible with MySQL 8 and H2 (MySQL mode)

-- Electronics (5)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Apple iPhone 12 64GB Space Gray',
        'apple iphone 12 64gb space gray',
        'Fully functional iPhone 12 with minor scratches on the back. Battery health at 87%. Comes with original charging cable.',
        'Electronics', 'GOOD', 229.99, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Dell XPS 13 Laptop Intel Core i7',
        'dell xps 13 laptop intel core i7',
        'Lightweight ultrabook in excellent condition. 16GB RAM, 512GB SSD. Barely used, no visible wear.',
        'Electronics', 'LIKE_NEW', 499.99, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Sony WH-1000XM4 Noise Cancelling Headphones',
        'sony wh-1000xm4 noise cancelling headphones',
        'Industry-leading noise cancellation. Includes original case and all accessories. Purchased 6 months ago.',
        'Electronics', 'LIKE_NEW', 189.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Samsung 4K Smart TV 43 Inch',
        'samsung 4k smart tv 43 inch',
        'Works perfectly. Remote included. Small scratch on the bezel not visible during use. Wall mount bracket included.',
        'Electronics', 'GOOD', 175.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Broken HP Chromebook for Parts',
        'broken hp chromebook for parts',
        'Screen is cracked and does not power on. Keyboard and bottom chassis in good shape. Sold as-is for parts or repair.',
        'Electronics', 'POOR', 15.00, 'USD', TRUE);

-- Furniture (4)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('IKEA KALLAX 4x4 Shelf Unit White',
        'ikea kallax 4x4 shelf unit white',
        'Disassembled and ready for pickup. All hardware included. Some minor scuffs on the sides from previous use.',
        'Furniture', 'GOOD', 65.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Mid-Century Modern Accent Chair Walnut',
        'mid century modern accent chair walnut',
        'Never used, still in original packaging. Walnut wood legs with light gray fabric. Retails at $450.',
        'Furniture', 'NEW', 280.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Queen Platform Bed Frame Dark Brown',
        'queen platform bed frame dark brown',
        'Solid wood slats, no box spring needed. Pet-free and smoke-free home. Buyer must disassemble and haul.',
        'Furniture', 'FAIR', 90.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Standing Desk Electric Height Adjustable',
        'standing desk electric height adjustable',
        '60x24 inch top, dual motor lift, memory presets. Lightly used for 8 months. All cables and legs included.',
        'Furniture', 'LIKE_NEW', 320.00, 'USD', TRUE);

-- Clothing (3)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Patagonia Better Sweater Fleece Jacket Medium',
        'patagonia better sweater fleece jacket medium',
        'Classic fit, navy blue. Worn twice. No pilling, no stains. Zipper works perfectly.',
        'Clothing', 'LIKE_NEW', 55.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Levi''s 501 Original Fit Jeans 32x32',
        'levis 501 original fit jeans 32x32',
        'Classic straight leg, medium wash. Light fading consistent with normal wear. No holes or stains.',
        'Clothing', 'GOOD', 22.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Nike Air Max Running Shoes Size 10',
        'nike air max running shoes size 10',
        'Used for casual wear only, not running. Soles show minimal wear. Cleaned and ready for new owner.',
        'Clothing', 'FAIR', 38.00, 'USD', TRUE);

-- Appliances (3)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('KitchenAid Artisan Stand Mixer 5 Qt Empire Red',
        'kitchenaid artisan stand mixer 5 qt empire red',
        'All attachments included: dough hook, flat beater, wire whip. Used occasionally. No dents or scratches.',
        'Appliances', 'GOOD', 185.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Dyson V11 Cordless Vacuum Cleaner',
        'dyson v11 cordless vacuum cleaner',
        'Purchased new 1 year ago. All attachments and docking station included. Suction is excellent.',
        'Appliances', 'GOOD', 220.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Instant Pot Duo 7-in-1 6 Quart',
        'instant pot duo 7 in 1 6 quart',
        'Brand new, never opened. Received as a gift but already have one. Original box sealed.',
        'Appliances', 'NEW', 75.00, 'USD', TRUE);

-- Books (3)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Clean Code by Robert C. Martin Paperback',
        'clean code by robert c martin paperback',
        'Classic software engineering text. Highlighting in first 3 chapters. Spine intact, no torn pages.',
        'Books', 'FAIR', 12.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('The Pragmatic Programmer 20th Anniversary Edition',
        'the pragmatic programmer 20th anniversary edition',
        'Read once, minimal wear. No markings or highlights. Great resource for developers at any level.',
        'Books', 'LIKE_NEW', 28.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Introduction to Algorithms CLRS 3rd Edition',
        'introduction to algorithms clrs 3rd edition',
        'Hardcover, minor shelf wear on corners. Inside pages are clean. Essential algorithms reference.',
        'Books', 'GOOD', 35.00, 'USD', TRUE);

-- Sports Equipment (2)
INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Trek FX 3 Disc Hybrid Bicycle 54cm',
        'trek fx 3 disc hybrid bicycle 54cm',
        'Ridden for one season. Hydraulic disc brakes, 24-speed drivetrain. Tires in good shape. Minor handlebar scratch.',
        'Sports Equipment', 'GOOD', 420.00, 'USD', TRUE);

INSERT INTO recycling_items (title, normalized_title, description, category, item_condition, price, currency, active)
VALUES ('Bowflex SelectTech 552 Adjustable Dumbbells Pair',
        'bowflex selecttech 552 adjustable dumbbells pair',
        'Adjustable from 5 to 52.5 lbs each. Both stands included. Light use, dials click cleanly on all settings.',
        'Sports Equipment', 'LIKE_NEW', 310.00, 'USD', TRUE);
