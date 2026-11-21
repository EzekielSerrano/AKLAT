package autolib;

import autolib.db.DBConnection;
import autolib.models.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Data access layer — queries PostgreSQL via DBConnection.
 * Falls back to in-memory seed data if the database is unreachable.
 */
public class DataStore {

    private static DataStore instance;
    private boolean dbAvailable;

    // In-memory cache / fallback
    private List<Book> cachedBooks = new ArrayList<>();
    private List<Student> cachedStudents = new ArrayList<>();
    private List<Transaction> cachedTransactions = new ArrayList<>();
    private int nextBookSeq = 1249;

    private DataStore() {
        dbAvailable = DBConnection.testConnection();
        if (dbAvailable) {
            System.out.println("[AutoLib] ✓ PostgreSQL connected — using live database.");
            refreshCache();
        } else {
            System.out.println("[AutoLib] ✗ PostgreSQL unavailable — using in-memory seed data.");
            seedFallback();
        }
    }

    public static DataStore get() {
        if (instance == null)
            instance = new DataStore();
        return instance;
    }

    public boolean isDbAvailable() {
        return dbAvailable;
    }

    /** Reload all caches from the database. */
    public void refreshCache() {
        if (!dbAvailable) return;
        try {
            cachedBooks = dbLoadBooks();
            cachedStudents = dbLoadStudents();
            cachedTransactions = dbLoadTransactions();
        } catch (SQLException e) {
            System.err.println("[AutoLib DB] Cache refresh failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BOOKS
    // ══════════════════════════════════════════════════════════════════════
    public List<Book> getBooks() {
        if (dbAvailable) {
            try { return dbLoadBooks(); } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(cachedBooks);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public boolean addBook(Book b) {
        boolean ok = true;
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO books (barcode,title,author,call_number,section,total_copies,available_copies,isbn,dewey_decimal) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, truncate(b.getBarcode(), 30));
                ps.setString(2, truncate(b.getTitle(), 250));
                ps.setString(3, truncate(b.getAuthor(), 250));
                ps.setString(4, truncate(b.getCallNumber(), 45));
                ps.setString(5, truncate(b.getSection(), 95));
                ps.setInt(6, b.getTotalCopies());
                ps.setInt(7, b.getAvailableCopies());
                ps.setString(8, truncate(b.getIsbn(), 28));
                ps.setString(9, truncate(b.getDeweyDecimal(), 45));
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[AutoLib DB] addBook failed: " + e.getMessage());
                e.printStackTrace();
                ok = false;
            }
        }
        cachedBooks.add(b);
        return ok;
    }

    public void removeBook(int idx) {
        if (idx >= 0 && idx < cachedBooks.size()) {
            Book b = cachedBooks.get(idx);
            if (dbAvailable) {
                try (Connection c = DBConnection.getConnection();
                     PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE barcode = ?")) {
                    ps.setString(1, b.getBarcode());
                    ps.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            cachedBooks.remove(idx);
        }
    }

    /** Persist a book's available_copies to the database. */
    public void updateBookCopies(Book b) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE books SET available_copies = ? WHERE barcode = ?")) {
                ps.setInt(1, b.getAvailableCopies());
                ps.setString(2, b.getBarcode());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /** Persist a student's active_loans and overdue_count to the database. */
    public void updateStudentLoans(Student s) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE students SET active_loans = ?, overdue_count = ? WHERE student_id = ?")) {
                ps.setInt(1, s.getActiveLoans());
                ps.setInt(2, s.getOverdueCount());
                ps.setString(3, s.getStudentId());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /** Persist a student's NFC registration (nfc_registered and nfc_uid) to the database. */
    public void updateStudentNfc(Student s) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE students SET nfc_registered = ?, nfc_uid = ? WHERE student_id = ?")) {
                ps.setBoolean(1, s.isNfcRegistered());
                ps.setString(2, s.getNfcUid());
                ps.setString(3, s.getStudentId());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /** Persist updated student info (name, department, year level, NFC) to the database. */
    public boolean updateStudent(Student s) {
        boolean ok = true;
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE students SET name = ?, department = ?, year_level = ?, nfc_registered = ?, nfc_uid = ? WHERE student_id = ?")) {
                ps.setString(1, s.getName());
                ps.setString(2, s.getDepartment());
                ps.setString(3, s.getYear());
                ps.setBoolean(4, s.isNfcRegistered());
                ps.setString(5, s.getNfcUid());
                ps.setString(6, s.getStudentId());
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[AutoLib DB] updateStudent failed: " + e.getMessage());
                e.printStackTrace();
                ok = false;
            }
        }
        return ok;
    }

    public boolean deleteStudent(String studentId) {
        boolean ok = true;
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM students WHERE student_id = ?")) {
                ps.setString(1, studentId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[AutoLib DB] deleteStudent failed: " + e.getMessage());
                e.printStackTrace();
                ok = false;
            }
        }
        cachedStudents.removeIf(st -> st.getStudentId().equalsIgnoreCase(studentId));
        return ok;
    }

    public String nextBarcode() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT barcode FROM books WHERE barcode LIKE 'BK-%' ORDER BY barcode DESC LIMIT 1")) {
                if (rs.next()) {
                    String last = rs.getString(1);
                    String digits = last.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        int num = Integer.parseInt(digits);
                        return String.format("BK-%05d", num + 1);
                    }
                }
            } catch (Exception e) { /* fall through */ }
        }
        return String.format("BK-%05d", nextBookSeq++);
    }

    public List<Book> searchBooks(String q, String section, String status) {
        List<Book> all = getBooks();
        List<Book> r = new ArrayList<>();
        String lq = q == null ? "" : q.toLowerCase().trim();
        for (Book b : all) {
            boolean matchQ = lq.isEmpty()
                    || (b.getTitle() != null && b.getTitle().toLowerCase().contains(lq))
                    || (b.getAuthor() != null && b.getAuthor().toLowerCase().contains(lq))
                    || (b.getBarcode() != null && b.getBarcode().toLowerCase().contains(lq))
                    || (b.getCallNumber() != null && b.getCallNumber().toLowerCase().contains(lq))
                    || (b.getIsbn() != null && b.getIsbn().toLowerCase().contains(lq));
            boolean matchS = section == null || section.equals("All Sections")
                    || (b.getSection() != null && b.getSection().equalsIgnoreCase(section.trim()));
            boolean matchSt = status == null || status.equals("All Status")
                    || (b.getStatusText() != null && b.getStatusText().equalsIgnoreCase(status.trim()));
            if (matchQ && matchS && matchSt)
                r.add(b);
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STUDENTS
    // ══════════════════════════════════════════════════════════════════════
    public List<Student> getStudents() {
        if (dbAvailable) {
            try { return dbLoadStudents(); } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(cachedStudents);
    }

    public void addStudent(Student s) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO students (student_id,name,department,year_level,nfc_registered,nfc_uid,active_loans,overdue_count) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, s.getStudentId());
                ps.setString(2, s.getName());
                ps.setString(3, s.getDepartment());
                ps.setString(4, s.getYear());
                ps.setBoolean(5, s.isNfcRegistered());
                ps.setString(6, s.getNfcUid());
                ps.setInt(7, s.getActiveLoans());
                ps.setInt(8, s.getOverdueCount());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
        cachedStudents.add(s);
    }

    public void removeStudent(int idx) {
        if (idx >= 0 && idx < cachedStudents.size()) {
            Student s = cachedStudents.get(idx);
            if (dbAvailable) {
                try (Connection c = DBConnection.getConnection();
                     PreparedStatement ps = c.prepareStatement("DELETE FROM students WHERE student_id = ?")) {
                    ps.setString(1, s.getStudentId());
                    ps.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            cachedStudents.remove(idx);
        }
    }

    public Student findStudentById(String id) {
        if (id == null) return null;
        String q = id.trim();
        if (q.isEmpty()) return null;
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM students WHERE LOWER(student_id) = LOWER(?)")) {
                ps.setString(1, q);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapStudent(rs);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        for (Student s : cachedStudents) {
            if (s.getStudentId().equalsIgnoreCase(q)) return s;
        }
        return null;
    }

    public Student findStudentByNfc(String nfcUid) {
        if (nfcUid == null) return null;
        String q = nfcUid.trim();
        if (q.isEmpty()) return null;
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM students WHERE LOWER(nfc_uid) = LOWER(?)")) {
                ps.setString(1, q);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapStudent(rs);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        for (Student s : cachedStudents) {
            if (s.getNfcUid() != null && s.getNfcUid().equalsIgnoreCase(q)) return s;
        }
        return null;
    }

    public Student findStudentByIdOrNfc(String query) {
        if (query == null) return null;
        String q = query.trim();
        if (q.isEmpty()) return null;

        // Try exact student_id first
        Student s = findStudentById(q);
        if (s != null) return s;

        // Try without hyphens (e.g. "2510834" -> "25-10834")
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM students WHERE REPLACE(student_id, '-', '') = LOWER(?)")) {
                ps.setString(1, q.replace("-", ""));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapStudent(rs);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        } else {
            for (Student st : cachedStudents) {
                String cleanId = st.getStudentId().replace("-", "");
                if (cleanId.equalsIgnoreCase(q)) return st;
            }
        }

        // Try NFC UID
        return findStudentByNfc(q);
    }

    public List<Student> searchStudents(String q, String dept) {
        List<Student> all = getStudents();
        List<Student> r = new ArrayList<>();
        String lq = q == null ? "" : q.toLowerCase().trim();
        for (Student s : all) {
            boolean matchQ = lq.isEmpty()
                    || s.getName().toLowerCase().contains(lq)
                    || s.getStudentId().toLowerCase().contains(lq)
                    || s.getDepartment().toLowerCase().contains(lq);
            boolean matchD = dept == null || dept.equals("All Departments") || s.getDepartment().equals(dept);
            if (matchQ && matchD)
                r.add(s);
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TRANSACTIONS
    // ══════════════════════════════════════════════════════════════════════
    public List<Transaction> getTransactions() {
        if (dbAvailable) {
            try { return dbLoadTransactions(); } catch (SQLException e) { e.printStackTrace(); }
        }
        return new ArrayList<>(cachedTransactions);
    }

    public void addTransaction(Transaction t) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO transactions (student_name,student_id,book_title,book_barcode,type,days_overdue,transaction_time) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, t.getStudentName());
                ps.setString(2, t.getStudentId());
                ps.setString(3, t.getBookTitle());
                ps.setString(4, t.getBookBarcode());
                ps.setString(5, t.getType().name());
                ps.setInt(6, t.getDaysOverdue());
                ps.setTimestamp(7, Timestamp.valueOf(t.getTimestamp()));
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
        cachedTransactions.add(0, t);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DASHBOARD STATS
    // ══════════════════════════════════════════════════════════════════════
    public int totalBooks() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_copies),0) FROM books")) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return cachedBooks.size();
    }

    public int currentlyBorrowed() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_copies - available_copies),0) FROM books")) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        int count = 0;
        for (Book b : cachedBooks) count += b.getBorrowedCount();
        return count;
    }

    public int overdueBooks() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM transactions WHERE type = 'OVERDUE'")) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        int count = 0;
        for (Transaction t : cachedTransactions)
            if (t.getType() == Transaction.Type.OVERDUE) count++;
        return count;
    }

    public int registeredStudents() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students")) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return cachedStudents.size();
    }

    public int[] weeklyBorrowData() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT EXTRACT(DOW FROM transaction_time)::int AS d, COUNT(*) " +
                     "FROM transactions WHERE type = 'BORROWED' " +
                     "AND transaction_time >= CURRENT_DATE - INTERVAL '7 days' " +
                     "GROUP BY d ORDER BY d")) {
                int[] raw = new int[7]; // 0=Sun..6=Sat (PostgreSQL DOW)
                while (rs.next()) {
                    int dow = rs.getInt(1);
                    raw[dow] = rs.getInt(2);
                }
                // Remap to Mon=0..Sun=6 for chart labels
                return new int[]{raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[0]};
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return new int[] { 0, 0, 0, 0, 0, 0, 0 };
    }

    public int[] monthlyBorrowData() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT EXTRACT(MONTH FROM transaction_time)::int AS m, COUNT(*) " +
                     "FROM transactions WHERE type = 'BORROWED' " +
                     "AND EXTRACT(YEAR FROM transaction_time) = EXTRACT(YEAR FROM CURRENT_DATE) " +
                     "GROUP BY m ORDER BY m")) {
                int[] data = new int[12];
                while (rs.next()) {
                    int month = rs.getInt(1); // 1-based
                    data[month - 1] = rs.getInt(2);
                }
                return data;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    }

    public List<Book> mostBorrowed() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT * FROM books ORDER BY (total_copies - available_copies) DESC LIMIT 5")) {
                List<Book> list = new ArrayList<>();
                while (rs.next()) list.add(mapBook(rs));
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        List<Book> r = new ArrayList<>(cachedBooks);
        r.sort((a, b) -> b.getBorrowedCount() - a.getBorrowedCount());
        return r.subList(0, Math.min(5, r.size()));
    }

    public List<Transaction> overdueAlerts() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT * FROM transactions WHERE type = 'OVERDUE' ORDER BY transaction_time DESC")) {
                List<Transaction> list = new ArrayList<>();
                while (rs.next()) list.add(mapTransaction(rs));
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        List<Transaction> r = new ArrayList<>();
        for (Transaction t : cachedTransactions)
            if (t.getType() == Transaction.Type.OVERDUE)
                r.add(t);
        return r;
    }

    public List<String> getSections() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT DISTINCT section FROM books ORDER BY section")) {
                List<String> list = new ArrayList<>();
                while (rs.next()) list.add(rs.getString(1));
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        Set<String> set = new LinkedHashSet<>();
        for (Book b : cachedBooks) set.add(b.getSection());
        return new ArrayList<>(set);
    }

    public List<String> getDepartments() {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT DISTINCT department FROM students ORDER BY department")) {
                List<String> list = new ArrayList<>();
                while (rs.next()) list.add(rs.getString(1));
                return list;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        Set<String> set = new LinkedHashSet<>();
        for (Student s : cachedStudents) set.add(s.getDepartment());
        return new ArrayList<>(set);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STAFF / LOGIN
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Validates librarian login credentials against the staff_users table.
     * Returns the full_name on success, null on failure.
     */
    public String authenticateStaff(String username, String password) {
        if (dbAvailable) {
            try (Connection c = DBConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT full_name FROM staff_users WHERE username = ? AND password_hash = ?")) {
                ps.setString(1, username);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        // Fallback: hardcoded admin/admin123
        if ("admin".equals(username) && "admin123".equals(password))
            return "Librarian Reyes";
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATABASE LOADERS (private)
    // ══════════════════════════════════════════════════════════════════════
    private List<Book> dbLoadBooks() throws SQLException {
        List<Book> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM books ORDER BY barcode")) {
            while (rs.next()) list.add(mapBook(rs));
        }
        return list;
    }

    private List<Student> dbLoadStudents() throws SQLException {
        List<Student> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM students ORDER BY student_id")) {
            while (rs.next()) list.add(mapStudent(rs));
        }
        return list;
    }

    private List<Transaction> dbLoadTransactions() throws SQLException {
        List<Transaction> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM transactions ORDER BY transaction_time DESC")) {
            while (rs.next()) list.add(mapTransaction(rs));
        }
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROW MAPPERS (private)
    // ══════════════════════════════════════════════════════════════════════
    private Book mapBook(ResultSet rs) throws SQLException {
        Book b = new Book(
            rs.getString("barcode"),
            rs.getString("title"),
            rs.getString("author"),
            rs.getString("call_number"),
            rs.getString("section"),
            rs.getInt("total_copies"),
            rs.getInt("available_copies")
        );
        b.setIsbn(rs.getString("isbn"));
        b.setDeweyDecimal(rs.getString("dewey_decimal"));
        return b;
    }

    private Student mapStudent(ResultSet rs) throws SQLException {
        return new Student(
            rs.getString("student_id"),
            rs.getString("name"),
            rs.getString("department"),
            rs.getString("year_level"),
            rs.getString("nfc_uid"),
            rs.getBoolean("nfc_registered"),
            rs.getInt("active_loans"),
            rs.getInt("overdue_count")
        );
    }

    private Transaction mapTransaction(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("transaction_time");
        LocalDateTime ldt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        String typeStr = rs.getString("type");
        Transaction.Type type = Transaction.Type.valueOf(typeStr);
        return new Transaction(
            rs.getString("student_name"),
            rs.getString("student_id"),
            rs.getString("book_title"),
            rs.getString("book_barcode"),
            type,
            ldt,
            rs.getInt("days_overdue")
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FALLBACK SEED (when DB is unreachable)
    // ══════════════════════════════════════════════════════════════════════
    private void seedFallback() {
        cachedBooks.add(new Book("BK-00492", "Data Structures & Algorithms", "Cormen et al.", "QA76.9", "Science & Tech", 5, 2));
        cachedBooks.add(new Book("BK-00211", "Chemistry for Engineers", "Zumdahl, S.", "QD11.3", "Engineering", 3, 0));
        cachedBooks.add(new Book("BK-00387", "Engineering Mathematics Vol. 2", "Bird, J.", "QA37.3", "Engineering", 4, 1));
        cachedBooks.add(new Book("BK-00501", "Fundamentals of Physics", "Halliday et al.", "QC21.3", "Science & Tech", 6, 4));
        cachedBooks.add(new Book("BK-00652", "Introduction to Psychology", "Myers, D.", "BF121", "Humanities", 2, 1));
        cachedBooks.add(new Book("BK-00798", "Calculus: Early Transcendentals", "Stewart, J.", "QA303.2", "Science & Tech", 5, 3));

        cachedStudents.add(new Student("25-10834",  "Mananquil, John Rhoden M.",     "CICS", "2nd Year", "0654151639", true, 0, 0));
        cachedStudents.add(new Student("25-12236",  "Orense, Alwyn D.",              "CICS", "2nd Year", "0655806295", true, 0, 0));
        cachedStudents.add(new Student("25-10800",  "Libao, Maurine N.",             "CICS", "2nd Year", "0659564759", true, 0, 0));
        cachedStudents.add(new Student("25-18190",  "Relevo, Rona Mae E.",           "CICS", "2nd Year", "0660868087", true, 0, 0));
        cachedStudents.add(new Student("25-15243",  "Guevarra, Alhea Lorin D.",      "CICS", "2nd Year", "0660497959", true, 0, 0));
        cachedStudents.add(new Student("25-10314",  "Hernandez, Laurence C.",        "CICS", "2nd Year", "0663382743", true, 0, 0));
        cachedStudents.add(new Student("25-00301",  "Hernandez, Princess Jewell B.", "CICS", "2nd Year", "0655531159", true, 0, 0));
        cachedStudents.add(new Student("25-19248",  "Hernandez, Leonart Andrae A.",  "CICS", "2nd Year", "0660031911", true, 0, 0));
        cachedStudents.add(new Student("25-12034",  "Alday, Arabella C.",            "CICS", "2nd Year", "0655805111", true, 0, 0));
        cachedStudents.add(new Student("25-19658",  "Torreja, Jeriel R.",            "CICS", "2nd Year", "0655930727", true, 0, 0));
        cachedStudents.add(new Student("25-10375",  "Rodriguez, Ariel A.",           "CICS", "2nd Year", "0659149031", true, 0, 0));
        cachedStudents.add(new Student("25-12553",  "Sale, Elmher Jay L.",           "CICS", "2nd Year", "0664517847", true, 0, 0));
        cachedStudents.add(new Student("25-14417",  "Balitustus, Kenneth C.",        "CICS", "2nd Year", "0662748167", true, 0, 0));
        cachedStudents.add(new Student("25-150930", "Trinidad, Ivan E.",             "CICS", "2nd Year", "0661596007", true, 0, 0));
        cachedStudents.add(new Student("25-17128",  "Ramirez, John Patrick S.",      "CICS", "2nd Year", "0659319335", true, 0, 0));

        // No seed transactions — real usage will generate real transaction history.
    }
}
