package io.heluna.vm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Native Java baseline implementations of each benchmark.
 * Uses plain Java types (Map, List, String, Long, Double, Boolean) — no HVal.
 * For measuring VM bytecode interpreter overhead vs raw Java.
 */
@SuppressWarnings("unchecked")
public class NativeBenchmarks {

    static boolean hasNative(String packetPath) {
        return extractBenchName(packetPath) != null;
    }

    static Map<String, Object> execute(String packetPath, Map<String, Object> input) {
        String benchName = extractBenchName(packetPath);
        if (benchName == null) {
            throw new RuntimeException("No native impl for packet: " + packetPath);
        }
        switch (benchName) {
            case "bench-arithmetic": return benchArithmetic(input);
            case "bench-strings":   return benchStrings(input);
            case "bench-lists":     return benchLists(input);
            case "bench-mixed":     return benchMixed(input);
            default: throw new RuntimeException("No native impl for: " + benchName);
        }
    }

    private static String extractBenchName(String packetPath) {
        int lastSlash = packetPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? packetPath.substring(lastSlash + 1) : packetPath;
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        switch (baseName) {
            case "bench-arithmetic":
            case "bench-strings":
            case "bench-lists":
            case "bench-mixed":
                return baseName;
            default:
                return null;
        }
    }

    // --- Benchmark: Arithmetic ---

    private static Map<String, Object> benchArithmetic(Map<String, Object> input) {
        List<Object> records = (List<Object>) input.get("records");

        List<Map<String, Object>> activeRecs = new ArrayList<>();
        for (Object obj : records) {
            Map<String, Object> rec = (Map<String, Object>) obj;
            if (Boolean.TRUE.equals(rec.get("active"))) {
                activeRecs.add(rec);
            }
        }

        long activeCount = activeRecs.size();
        double totalSalary = 0.0;
        double scoreSum = 0.0;
        double totalAdjusted = 0.0;
        for (Map<String, Object> rec : activeRecs) {
            double salary = toDouble(rec.get("salary"));
            double score = toDouble(rec.get("score"));
            totalSalary += salary;
            scoreSum += score;
            totalAdjusted += salary * 0.7;
        }

        double avgScore = scoreSum / (double) activeCount;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active-count", activeCount);
        result.put("total-salary", totalSalary);
        result.put("avg-score", avgScore);
        result.put("total-adjusted", totalAdjusted);
        result.put("record-count", (long) records.size());
        return result;
    }

    // --- Benchmark: Strings ---

    private static Map<String, Object> benchStrings(Map<String, Object> input) {
        List<Object> records = (List<Object>) input.get("records");

        List<Object> normalizedEmails = new ArrayList<>();
        List<Object> formattedNames = new ArrayList<>();
        List<Object> maskedEmails = new ArrayList<>();
        StringBuilder allNames = new StringBuilder();

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> rec = (Map<String, Object>) records.get(i);
            String email = (String) rec.get("email");
            String name = (String) rec.get("name");

            String trimmedEmail = email.trim();
            String trimmedName = name.trim();

            normalizedEmails.add(trimmedEmail.toLowerCase());
            formattedNames.add(trimmedName.toUpperCase());
            maskedEmails.add(trimmedEmail.replace("@", "[at]"));

            if (i > 0) allNames.append("; ");
            allNames.append(trimmedName);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("normalized-emails", normalizedEmails);
        result.put("formatted-names", formattedNames);
        result.put("masked-emails", maskedEmails);
        result.put("all-names", allNames.toString());
        result.put("record-count", (long) records.size());
        return result;
    }

    // --- Benchmark: Lists ---

    private static Map<String, Object> benchLists(Map<String, Object> input) {
        List<Object> records = (List<Object>) input.get("records");

        // sort-by score (ascending)
        List<Object> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            double sa = toDouble(((Map<String, Object>) a).get("score"));
            double sb = toDouble(((Map<String, Object>) b).get("score"));
            return Double.compare(sa, sb);
        });

        // unique departments
        LinkedHashSet<String> uniqueDepts = new LinkedHashSet<>();
        for (Object obj : records) {
            uniqueDepts.add((String) ((Map<String, Object>) obj).get("department"));
        }

        // top 10 (slice 0..10)
        int topTenCount = Math.min(10, sorted.size());

        // filter active
        int activeCount = 0;
        for (Object obj : records) {
            if (Boolean.TRUE.equals(((Map<String, Object>) obj).get("active"))) {
                activeCount++;
            }
        }

        // range sum 1..100 (inclusive)
        long rangeSum = 0;
        for (int i = 1; i <= 100; i++) {
            rangeSum += i;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sorted-count", (long) sorted.size());
        result.put("unique-dept-count", (long) uniqueDepts.size());
        result.put("top-ten-count", (long) topTenCount);
        result.put("active-count", (long) activeCount);
        result.put("reversed-count", (long) sorted.size());
        result.put("range-sum", rangeSum);
        return result;
    }

    // --- Benchmark: Mixed ---

    private static Map<String, Object> benchMixed(Map<String, Object> input) {
        List<Object> records = (List<Object>) input.get("records");

        // Filter active, accumulate salaries and scores
        List<Map<String, Object>> activeRecs = new ArrayList<>();
        double totalSalary = 0.0;
        double scoreSum = 0.0;
        for (Object obj : records) {
            Map<String, Object> rec = (Map<String, Object>) obj;
            if (Boolean.TRUE.equals(rec.get("active"))) {
                activeRecs.add(rec);
                totalSalary += toDouble(rec.get("salary"));
                scoreSum += toDouble(rec.get("score"));
            }
        }
        long activeCount = activeRecs.size();
        double avgScore = scoreSum / (double) activeCount;

        // Join active names (trimmed)
        StringBuilder allNames = new StringBuilder();
        for (int i = 0; i < activeRecs.size(); i++) {
            if (i > 0) allNames.append("; ");
            allNames.append(((String) activeRecs.get(i).get("name")).trim());
        }

        // Sort active by score (ascending)
        List<Map<String, Object>> sortedActive = new ArrayList<>(activeRecs);
        sortedActive.sort((a, b) -> Double.compare(toDouble(a.get("score")), toDouble(b.get("score"))));

        // Unique departments from ALL records
        LinkedHashSet<String> uniqueDepts = new LinkedHashSet<>();
        for (Object obj : records) {
            uniqueDepts.add((String) ((Map<String, Object>) obj).get("department"));
        }

        // Seniors: active and age >= 50
        long seniorCount = 0;
        for (Map<String, Object> rec : activeRecs) {
            if (toLong(rec.get("age")) >= 50) seniorCount++;
        }

        // Top scorer (first from sorted = lowest score)
        String topScorer;
        if (!sortedActive.isEmpty()) {
            topScorer = ((String) sortedActive.get(0).get("name")).trim();
        } else {
            topScorer = "none";
        }

        // Checksum: sha256(toString(activeCount) + ":" + toString(totalSalary))
        // The VM's compiled bytecode produces record-wrapped toString values:
        // to-string({value: x}) compiles to TO_STRING on x, then builds record,
        // which STR_CONCAT converts via record.toString() → {value: "..."}
        String activeStr = Long.toString(activeCount);
        String salaryStr = doubleToString(totalSalary);
        String checksumInput = "{value: \"" + activeStr + "\"}:{value: \"" + salaryStr + "\"}";
        String checksum = sha256Hex(checksumInput);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active-count", activeCount);
        result.put("total-salary", totalSalary);
        result.put("avg-score", avgScore);
        result.put("all-names", allNames.toString());
        result.put("sorted-count", (long) sortedActive.size());
        result.put("unique-dept-count", (long) uniqueDepts.size());
        result.put("senior-count", seniorCount);
        result.put("top-scorer", topScorer);
        result.put("checksum", checksum);
        return result;
    }

    // --- Utilities ---

    private static double toDouble(Object v) {
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return (double) (Long) v;
        throw new RuntimeException("Expected number, got: " + v);
    }

    private static long toLong(Object v) {
        if (v instanceof Long) return (Long) v;
        if (v instanceof Double) return (long) (double) (Double) v;
        throw new RuntimeException("Expected number, got: " + v);
    }

    /** Format double matching Executor.valToString for HFloat */
    private static String doubleToString(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d) + ".0";
        }
        return Double.toString(d);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // --- JSON Parser (plain Java types) ---

    static Object parseJson(String json) {
        return parseValue(json, new int[]{0});
    }

    private static Object parseValue(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) return null;
        char c = s.charAt(pos[0]);
        if (c == '"') return parseString(s, pos);
        if (c == '{') return parseObject(s, pos);
        if (c == '[') return parseArray(s, pos);
        if (c == 't') { pos[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return null; }
        return parseNumber(s, pos);
    }

    private static String parseString(String s, int[] pos) {
        pos[0]++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '"') { pos[0]++; return sb.toString(); }
            if (c == '\\') {
                pos[0]++;
                char esc = s.charAt(pos[0]);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(pos[0] + 1, pos[0] + 5), 16));
                        pos[0] += 4;
                        break;
                }
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        throw new RuntimeException("Unterminated string");
    }

    private static Object parseNumber(String s, int[] pos) {
        int start = pos[0];
        boolean isFloat = false;
        if (s.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        if (pos[0] < s.length() && s.charAt(pos[0]) == '.') {
            isFloat = true; pos[0]++;
            while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        }
        if (pos[0] < s.length() && (s.charAt(pos[0]) == 'e' || s.charAt(pos[0]) == 'E')) {
            isFloat = true; pos[0]++;
            if (pos[0] < s.length() && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) pos[0]++;
            while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        }
        String num = s.substring(start, pos[0]);
        if (isFloat) return Double.parseDouble(num);
        return Long.parseLong(num);
    }

    private static Map<String, Object> parseObject(String s, int[] pos) {
        pos[0]++; // skip {
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs(s, pos);
        if (s.charAt(pos[0]) == '}') { pos[0]++; return map; }
        while (true) {
            skipWs(s, pos);
            String key = parseString(s, pos);
            skipWs(s, pos);
            pos[0]++; // skip :
            Object val = parseValue(s, pos);
            map.put(key, val);
            skipWs(s, pos);
            if (s.charAt(pos[0]) == '}') { pos[0]++; return map; }
            pos[0]++; // skip ,
        }
    }

    private static List<Object> parseArray(String s, int[] pos) {
        pos[0]++; // skip [
        List<Object> list = new ArrayList<>();
        skipWs(s, pos);
        if (s.charAt(pos[0]) == ']') { pos[0]++; return list; }
        while (true) {
            list.add(parseValue(s, pos));
            skipWs(s, pos);
            if (s.charAt(pos[0]) == ']') { pos[0]++; return list; }
            pos[0]++; // skip ,
        }
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) pos[0]++;
    }

    // --- JSON Serializer (matches StdLib.toJson output) ---

    static String toJson(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + escapeJsonStr((String) v) + "\"";
        if (v instanceof Long) return Long.toString((Long) v);
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d) + ".0";
            }
            return Double.toString(d);
        }
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<Object> list = (List<Object>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<String, Object> map = (Map<String, Object>) v;
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJsonStr(e.getKey())).append("\":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "null";
    }

    private static String escapeJsonStr(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
