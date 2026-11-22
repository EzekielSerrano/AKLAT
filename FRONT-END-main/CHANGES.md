# Change summary - Borrow/Return moved to the Librarian desk

## Student kiosk (now view-only)
- Removed "Borrow a Book" and "Return a Book" (sidebar + quick actions).
- Deleted: autolib/fx/kiosk/KioskBorrowFX.java, KioskReturnFX.java
- After tapping their ID, students land on Dashboard (loans, due dates, history, profile).
- StudentKioskFX, KioskDashboardFX, KioskProfileCardFX, PortalScreenFX: wording/nav updated.

## Librarian portal - new "Borrow & Return" page (cashier style)
- NEW autolib/fx/panels/CirculationFX.java
  1. Student taps NFC card / scans or types ID  -> profile card loads
  2. Librarian scans each book (USB scanner, or Camera button) -> added to the current transaction
     (book the student already holds = RETURN, otherwise = BORROW)
  3. "Complete Transaction" saves everything to PostgreSQL
- LibrarianPortalFX: new sidebar item; other pages refresh after a transaction.

## Database layer (DataStore.java)
- findBookByCode()  - barcode or ISBN lookup
- checkoutBook() / checkinBook() - update books.available_copies, students.active_loans and insert
  the transactions row in ONE SQL transaction (rolls back on failure)
- No schema change: db/schema.sql is untouched. The mobile app can read students + transactions as-is.

## Build
- run_autolib_fx.bat: removed the two deleted files, added CirculationFX.java
