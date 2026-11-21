-- =====================================================================
-- A.K.L.A.T. — Automated Key Library Analytics & Tracking
-- PostgreSQL Database Schema & Initial Data
-- System: Batangas State University Library Inventory & Kiosk System
-- =====================================================================

-- 1. Create Database (Run separately if needed)
-- CREATE DATABASE autolib_db;
-- \c autolib_db;

-- ---------------------------------------------------------------------
-- 2. Extensions & Cleanup
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS books CASCADE;
DROP TABLE IF EXISTS students CASCADE;
DROP TABLE IF EXISTS staff_users CASCADE;

-- ---------------------------------------------------------------------
-- 3. Staff / Librarian Accounts
-- ---------------------------------------------------------------------
CREATE TABLE staff_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'Head Librarian',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 4. Students / Borrowers
-- ---------------------------------------------------------------------
CREATE TABLE students (
    student_id VARCHAR(30) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    department VARCHAR(50) NOT NULL,          -- e.g. CICS, COE, CAS, COED
    year_level VARCHAR(30) NOT NULL,          -- e.g. 1st Year, 2nd Year, 3rd Year, 4th Year
    nfc_registered BOOLEAN NOT NULL DEFAULT FALSE,
    nfc_uid VARCHAR(64) UNIQUE,               -- Physical NFC tag UID from hardware reader
    active_loans INT NOT NULL DEFAULT 0 CHECK (active_loans >= 0),
    overdue_count INT NOT NULL DEFAULT 0 CHECK (overdue_count >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 5. Book Catalog
-- ---------------------------------------------------------------------
CREATE TABLE books (
    barcode VARCHAR(30) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    call_number VARCHAR(50),
    section VARCHAR(100) NOT NULL,            -- e.g. Science & Tech, Engineering, Humanities
    total_copies INT NOT NULL DEFAULT 1 CHECK (total_copies >= 0),
    available_copies INT NOT NULL DEFAULT 1 CHECK (available_copies >= 0 AND available_copies <= total_copies),
    isbn VARCHAR(30),
    dewey_decimal VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 6. Transactions (Borrow, Return, Overdue)
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    student_id VARCHAR(30) NOT NULL REFERENCES students(student_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    student_name VARCHAR(150) NOT NULL,
    book_barcode VARCHAR(30) NOT NULL REFERENCES books(barcode) ON UPDATE CASCADE ON DELETE RESTRICT,
    book_title VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('BORROWED', 'RETURNED', 'OVERDUE')),
    days_overdue INT DEFAULT 0 CHECK (days_overdue >= 0),
    transaction_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 7. Performance Indexes
-- ---------------------------------------------------------------------
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_author ON books(author);
CREATE INDEX idx_books_section ON books(section);
CREATE INDEX idx_students_dept ON students(department);
CREATE INDEX idx_transactions_student ON transactions(student_id);
CREATE INDEX idx_transactions_barcode ON transactions(book_barcode);
CREATE INDEX idx_transactions_time ON transactions(transaction_time DESC);

-- =====================================================================
-- 8. Seed Data (Matches AutoLib demo data)
-- =====================================================================

-- Default Librarian account: admin / admin123
INSERT INTO staff_users (username, password_hash, full_name, role)
VALUES ('admin', 'admin123', 'Librarian Reyes', 'Head Librarian');

-- Books
INSERT INTO books (barcode, title, author, call_number, section, total_copies, available_copies) VALUES
('BK-00492', 'Data Structures & Algorithms', 'Cormen et al.', 'QA76.9', 'Science & Tech', 5, 2),
('BK-00211', 'Chemistry for Engineers', 'Zumdahl, S.', 'QD11.3', 'Engineering', 3, 0),
('BK-00387', 'Engineering Mathematics Vol. 2', 'Bird, J.', 'QA37.3', 'Engineering', 4, 1),
('BK-00501', 'Fundamentals of Physics', 'Halliday et al.', 'QC21.3', 'Science & Tech', 6, 4),
('BK-00652', 'Introduction to Psychology', 'Myers, D.', 'BF121', 'Humanities', 2, 1),
('BK-00798', 'Calculus: Early Transcendentals', 'Stewart, J.', 'QA303.2', 'Science & Tech', 5, 3);

-- Students
INSERT INTO students (student_id, name, department, year_level, nfc_registered, active_loans, overdue_count) VALUES
('2024-10042', 'Santos, Maria L.', 'CICS', '3rd Year', TRUE, 1, 0),
('2023-10087', 'Reyes, Ana C.', 'COE', '4th Year', TRUE, 1, 1),
('2025-10015', 'Cruz, Jose P.', 'CAS', '2nd Year', TRUE, 0, 0),
('2024-20093', 'Torres, Ben M.', 'COE', '3rd Year', FALSE, 2, 1),
('2023-30061', 'Lim, Carlos R.', 'COED', '4th Year', TRUE, 1, 0);

-- Registered students with NFC tags (imported from NFC tag list)
-- NOTE: department / year_level are NOT NULL, so 'Unassigned' is a placeholder until updated.
INSERT INTO students (student_id, name, department, year_level, nfc_registered, nfc_uid, active_loans, overdue_count) VALUES
('25-10834',  'Mananquil, John Rhoden M.',    'CICS', '2nd Year', TRUE, '0654151639', 0, 0),
('25-12236',  'Orense, Alwyn D.',             'CICS', '2nd Year', TRUE, '0655806295', 0, 0),
('25-10800',  'Libao, Maurine N.',            'CICS', '2nd Year', TRUE, '0659564759', 0, 0),
('25-18190',  'Relevo, Rona Mae E.',          'CICS', '2nd Year', TRUE, '0660868087', 0, 0),
('25-15243',  'Guevarra, Alhea Lorin D.',     'CICS', '2nd Year', TRUE, '0660497959', 0, 0),
('25-10314',  'Hernandez, Laurence C.',       'CICS', '2nd Year', TRUE, '0663382743', 0, 0),
('25-00301',  'Hernandez, Princess Jewell B.','CICS', '2nd Year', TRUE, '0655531159', 0, 0),
('25-19248',  'Hernandez, Leonart Andrae A.', 'CICS', '2nd Year', TRUE, '0660031911', 0, 0),
('25-12034',  'Alday, Arabella C.',           'CICS', '2nd Year', TRUE, '0655805111', 0, 0),
('25-19658',  'Torreja, Jeriel R.',           'CICS', '2nd Year', TRUE, '0655930727', 0, 0),
('25-10375',  'Rodriguez, Ariel A.',          'CICS', '2nd Year', TRUE, '0659149031', 0, 0),
('25-12553',  'Sale, Elmher Jay L.',          'CICS', '2nd Year', TRUE, '0664517847', 0, 0),
('25-14417',  'Balitustus, Kenneth C.',       'CICS', '2nd Year', TRUE, '0662748167', 0, 0),
('25-150930', 'Trinidad, Ivan E.',            'CICS', '2nd Year', TRUE, '0661596007', 0, 0),  -- ID has 6 digits, verify
('25-17128',  'Ramirez, John Patrick S.',     'CICS', '2nd Year', TRUE, '0659319335', 0, 0);

-- Transactions
INSERT INTO transactions (student_name, student_id, book_title, book_barcode, type, days_overdue, transaction_time) VALUES
('Santos, Maria L.', '2024-10042', 'Data Structures & Algorithms', 'BK-00492', 'BORROWED', 0, CURRENT_TIMESTAMP - INTERVAL '20 minutes'),
('Cruz, Jose P.',    '2025-10015', 'Engineering Mathematics Vol. 2', 'BK-00387', 'RETURNED', 0, CURRENT_TIMESTAMP - INTERVAL '38 minutes'),
('Reyes, Ana C.',    '2023-10087', 'Chemistry for Engineers',        'BK-00211', 'OVERDUE',  5, CURRENT_TIMESTAMP - INTERVAL '1 day'),
('Lim, Carlos R.',   '2023-30061', 'Introduction to Psychology',     'BK-00652', 'BORROWED', 0, CURRENT_TIMESTAMP - INTERVAL '63 minutes'),
('Santos, Maria L.', '2024-10042', 'Calculus: Early Transcendentals','BK-00798', 'RETURNED', 0, CURRENT_TIMESTAMP - INTERVAL '74 minutes');