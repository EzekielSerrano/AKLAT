package autolib;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.*;

/**
 * ISBN Lookup Service — queries Open Library API and Google Books API
 * to auto-fill book metadata. No extra dependencies (pure JDK 17).
 *
 * Usage:
 *   IsbnLookupService.lookup("9780321560995")
 *       .thenAccept(result -> { ... }) // runs on a background thread
 *
 * Result fields (may be null/empty if not found):
 *   result.title, result.author, result.publisher,
 *   result.year, result.dewey, result.pages, result.found
 */
public class IsbnLookupService {

    // ── Result container ──────────────────────────────────────────────
    public static class BookInfo {
        public final boolean found;
        public final String  title;
        public final String  author;
        public final String  publisher;
        public final String  year;
        public final String  dewey;      // Dewey Decimal class
        public final String  pages;
        public final String  coverUrl;
        public final String  source;     // "Open Library" or "Google Books"
        public final String  isbn;       // ISBN (filled by title search results)

        BookInfo(boolean found, String title, String author,
                 String publisher, String year, String dewey,
                 String pages, String coverUrl, String source) {
            this.found     = found;
            this.title     = title;
            this.author    = author;
            this.publisher = publisher;
            this.year      = year;
            this.dewey     = dewey;
            this.pages     = pages;
            this.coverUrl  = coverUrl;
            this.source    = source;
            this.isbn      = "";
        }

        BookInfo(String title, String author, String publisher, String year,
                 String pages, String coverUrl, String isbn, String source) {
            this.found     = true;
            this.title     = title;
            this.author    = author;
            this.publisher = publisher;
            this.year      = year;
            this.dewey     = "";
            this.pages     = pages;
            this.coverUrl  = coverUrl;
            this.source    = source;
            this.isbn      = isbn;
        }

        public static BookInfo notFound() {
            return new BookInfo(false, "", "", "", "", "", "", "", "");
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Looks up a book by ISBN asynchronously.
     * Tries Open Library first, then Google Books as fallback.
     * Never throws — returns BookInfo.notFound() on any error.
     */
    public static CompletableFuture<BookInfo> lookup(String isbn) {
        String clean = isbn.replaceAll("[^0-9X]", "");
        return lookupOpenLibrary(clean)
                .thenCompose(info -> {
                    if (info.found) return CompletableFuture.completedFuture(info);
                    return lookupGoogleBooks(clean);
                })
                .exceptionally(ex -> BookInfo.notFound());
    }


    // ── Title search ──────────────────────────────────────────────────

    /**
     * Searches by book title (Open Library, then Google Books as fallback).
     * Returns up to 8 matches. Never throws — returns an empty list on error.
     */
    public static CompletableFuture<List<BookInfo>> searchByTitle(String title) {
        String q = title == null ? "" : title.trim();
        if (q.length() < 2) return CompletableFuture.completedFuture(new ArrayList<>());
        return searchOpenLibraryTitle(q)
                .thenCompose(list -> {
                    if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                    return searchGoogleTitle(q);
                })
                .exceptionally(ex -> new ArrayList<>());
    }

    private static CompletableFuture<List<BookInfo>> searchOpenLibraryTitle(String q) {
        String url = "https://openlibrary.org/search.json?title="
                   + URLEncoder.encode(q, StandardCharsets.UTF_8)
                   + "&limit=8&fields=title,author_name,first_publish_year,publisher,isbn,cover_i,number_of_pages_median";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AKLAT-Library/1.0")
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    List<BookInfo> out = new ArrayList<>();
                    if (resp.statusCode() != 200) return out;
                    for (String doc : splitObjects(resp.body(), "docs")) {
                        String t = extractString(doc, "title");
                        if (t.isEmpty()) continue;
                        String cover = extractString(doc, "cover_i");
                        out.add(new BookInfo(t,
                                extractFirstInArray(doc, "author_name", null),
                                extractFirstInArray(doc, "publisher", null),
                                extractString(doc, "first_publish_year"),
                                extractString(doc, "number_of_pages_median"),
                                cover.isEmpty() ? "" : "https://covers.openlibrary.org/b/id/" + cover + "-M.jpg",
                                pickIsbn(doc, "isbn"),
                                "Open Library"));
                    }
                    return out;
                })
                .exceptionally(ex -> new ArrayList<>());
    }

    private static CompletableFuture<List<BookInfo>> searchGoogleTitle(String q) {
        String url = "https://www.googleapis.com/books/v1/volumes?maxResults=8&q=intitle:"
                   + URLEncoder.encode(q, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AKLAT-Library/1.0")
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    List<BookInfo> out = new ArrayList<>();
                    if (resp.statusCode() != 200) return out;
                    for (String item : splitObjects(resp.body(), "items")) {
                        String t = extractString(item, "title");
                        if (t.isEmpty()) continue;
                        String year = extractString(item, "publishedDate");
                        if (year.length() > 4) year = year.substring(0, 4);
                        String cover = "";
                        if (item.contains("\"thumbnail\""))
                            cover = extractString(item, "thumbnail").replace("http://", "https://");
                        out.add(new BookInfo(t,
                                extractFirstInArray(item, "authors", null),
                                extractString(item, "publisher"),
                                year,
                                extractString(item, "pageCount"),
                                cover,
                                pickIsbn(item, "identifier"),
                                "Google Books"));
                    }
                    return out;
                })
                .exceptionally(ex -> new ArrayList<>());
    }

    /** Prefer an ISBN-13 among the values of the given key; else any ISBN-10. */
    private static String pickIsbn(String json, String key) {
        String best = "";
        if ("isbn".equals(key)) {
            // "isbn":["978...","03..."]
            int idx = json.indexOf("\"isbn\"");
            if (idx < 0) return "";
            int b = json.indexOf('[', idx), e = json.indexOf(']', idx);
            if (b < 0 || e < b) return "";
            Matcher m = Pattern.compile("\"([0-9X]{10,13})\"").matcher(json.substring(b, e));
            while (m.find()) {
                String v = m.group(1);
                if (v.length() == 13) return v;
                if (best.isEmpty()) best = v;
            }
            return best;
        }
        Matcher m = Pattern.compile("\"identifier\"\\s*:\\s*\"([0-9X]{10,13})\"").matcher(json);
        while (m.find()) {
            String v = m.group(1);
            if (v.length() == 13) return v;
            if (best.isEmpty()) best = v;
        }
        return best;
    }

    /** Splits the top-level objects of the array named arrayKey (string-aware). */
    private static List<String> splitObjects(String json, String arrayKey) {
        List<String> out = new ArrayList<>();
        int k = json.indexOf("\"" + arrayKey + "\"");
        if (k < 0) return out;
        int i = json.indexOf('[', k);
        if (i < 0) return out;
        int depth = 0, start = -1;
        boolean inStr = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (c == '\\') i++;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') { if (depth == 1) start = i; depth++; }
            else if (c == '[') depth++;
            else if (c == '}') { depth--; if (depth == 1 && start >= 0) { out.add(json.substring(start, i + 1)); start = -1; } }
            else if (c == ']') { depth--; if (depth == 0) break; }
        }
        return out;
    }

    // ── Open Library ──────────────────────────────────────────────────

    private static CompletableFuture<BookInfo> lookupOpenLibrary(String isbn) {
        String url = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn
                   + "&format=json&jscmd=data";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AKLAT-Library/1.0")
                .GET().build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return BookInfo.notFound();
                    return parseOpenLibrary(resp.body(), isbn);
                })
                .exceptionally(ex -> BookInfo.notFound());
    }

    private static BookInfo parseOpenLibrary(String json, String isbn) {
        String key = "ISBN:" + isbn;
        // Top-level key must exist
        if (!json.contains("\"" + key + "\"")) return BookInfo.notFound();

        // Extract the sub-object for this ISBN
        int start = json.indexOf("\"" + key + "\"");
        if (start < 0) return BookInfo.notFound();

        String title     = extractString(json, "title");
        String publisher = extractFirstInArray(json, "publishers", "name");
        String year      = extractString(json, "publish_date");
        // author: "authors":[{"name":"..."}]
        String author    = extractFirstInArray(json, "authors", "name");
        String dewey     = extractFirstInArray(json, "dewey_decimal_class", null);
        String pages     = extractString(json, "number_of_pages");
        String coverUrl  = "";

        // Cover image URL
        int coverIdx = json.indexOf("\"medium\":");
        if (coverIdx >= 0) {
            coverUrl = extractStringAt(json, coverIdx, "medium");
        }
        if (coverUrl.isEmpty()) {
            coverIdx = json.indexOf("\"small\":");
            if (coverIdx >= 0) coverUrl = extractStringAt(json, coverIdx, "small");
        }

        boolean found = !title.isEmpty();
        return new BookInfo(found, title, author, publisher, year, dewey, pages, coverUrl,
                            "Open Library");
    }

    // ── Google Books ──────────────────────────────────────────────────

    private static CompletableFuture<BookInfo> lookupGoogleBooks(String isbn) {
        String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + isbn;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "AKLAT-Library/1.0")
                .GET().build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return BookInfo.notFound();
                    return parseGoogleBooks(resp.body());
                })
                .exceptionally(ex -> BookInfo.notFound());
    }

    private static BookInfo parseGoogleBooks(String json) {
        if (!json.contains("\"totalItems\"") || json.contains("\"totalItems\": 0")
                || json.contains("\"totalItems\":0")) return BookInfo.notFound();

        // volumeInfo block
        int vi = json.indexOf("\"volumeInfo\"");
        if (vi < 0) return BookInfo.notFound();
        String vi_json = json.substring(vi);

        String title     = extractString(vi_json, "title");
        String publisher = extractString(vi_json, "publisher");
        String year      = extractString(vi_json, "publishedDate");
        if (year.length() > 4) year = year.substring(0, 4); // "2023-07-15" -> "2023"

        // authors array: ["Author One","Author Two"]
        String author = "";
        int authArr = vi_json.indexOf("\"authors\"");
        if (authArr >= 0) {
            int bracket = vi_json.indexOf("[", authArr);
            int endBracket = vi_json.indexOf("]", bracket);
            if (bracket >= 0 && endBracket > bracket) {
                String arr = vi_json.substring(bracket + 1, endBracket);
                // Extract first quoted string
                Pattern p = Pattern.compile("\"([^\"]+)\"");
                Matcher m = p.matcher(arr);
                if (m.find()) author = m.group(1);
            }
        }

        String pages = extractString(vi_json, "pageCount");

        // Cover
        String coverUrl = "";
        int thumbIdx = vi_json.indexOf("\"thumbnail\":");
        if (thumbIdx >= 0) {
            coverUrl = extractStringAt(vi_json, thumbIdx, "thumbnail");
            // Google uses http — upgrade to https
            coverUrl = coverUrl.replace("http://", "https://");
        }

        boolean found = !title.isEmpty();
        return new BookInfo(found, title, author, publisher, year, "", pages, coverUrl,
                            "Google Books");
    }

    // ── JSON Mini-Parser Helpers ──────────────────────────────────────

    /** Extract value of first "key":"value" (or "key": number) in json. */
    private static String extractString(String json, String key) {
        // Try quoted value: "key":"value"
        Pattern strPat = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = strPat.matcher(json);
        if (m.find()) return unescape(m.group(1));

        // Try numeric value: "key": 123
        Pattern numPat = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m2 = numPat.matcher(json);
        if (m2.find()) return m2.group(1);

        return "";
    }

    /** Extract value at a known index position (for disambiguating duplicate keys). */
    private static String extractStringAt(String json, int fromIdx, String key) {
        String sub = json.substring(fromIdx);
        return extractString(sub, key);
    }

    /**
     * Extract the first item from a JSON array by key name.
     * For arrays of objects: "key":[{"subKey":"value"},...] -> value
     * For arrays of strings: "key":["value",...] -> value  (subKey == null)
     */
    private static String extractFirstInArray(String json, String arrayKey, String subKey) {
        int arrIdx = json.indexOf("\"" + arrayKey + "\"");
        if (arrIdx < 0) return "";
        int bracket = json.indexOf("[", arrIdx);
        if (bracket < 0) return "";
        // Find matching ]
        int depth = 0, end = -1;
        for (int i = bracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        if (end < 0) return "";
        String arr = json.substring(bracket, end + 1);

        if (subKey == null) {
            // Array of strings — grab first quoted value
            Pattern p = Pattern.compile("\"([^\"]+)\"");
            Matcher m = p.matcher(arr.substring(1)); // skip opening [
            if (m.find()) return unescape(m.group(1));
            return "";
        }
        // Array of objects
        return extractString(arr, subKey);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\t", " ")
                .replace("\\\\", "\\")
                .replace("\\/", "/");
    }
}
