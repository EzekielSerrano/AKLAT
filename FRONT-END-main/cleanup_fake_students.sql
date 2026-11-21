-- ============================================================
-- A.K.L.A.T. — Remove fake seed students & their transactions
-- Run once to clean the live PostgreSQL database
-- ============================================================

-- Step 1: Delete transactions belonging to fake students
DELETE FROM transactions
WHERE student_id IN (
    '2024-10042',   -- Santos, Maria L.
    '2023-10087',   -- Reyes, Ana C.
    '2025-10015',   -- Cruz, Jose P.
    '2024-20093',   -- Torres, Ben M.
    '2023-30061'    -- Lim, Carlos R.
);

-- Step 2: Delete the fake students themselves
DELETE FROM students
WHERE student_id IN (
    '2024-10042',
    '2023-10087',
    '2025-10015',
    '2024-20093',
    '2023-30061'
);

-- Step 3: Verify — should return 0 rows for each
SELECT 'Remaining fake students:' AS check_label, COUNT(*) AS count
FROM students
WHERE student_id IN ('2024-10042','2023-10087','2025-10015','2024-20093','2023-30061');

SELECT 'Remaining fake transactions:' AS check_label, COUNT(*) AS count
FROM transactions
WHERE student_id IN ('2024-10042','2023-10087','2025-10015','2024-20093','2023-30061');

-- Step 4: Show what remains
SELECT student_id, name, department, year_level FROM students ORDER BY student_id;
