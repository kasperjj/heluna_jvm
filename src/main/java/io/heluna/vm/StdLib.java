package io.heluna.vm;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StdLib {

    private String timestamp = "2024-01-01T00:00:00Z";

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public HVal call(int funcId, HVal.HRecord args) {
        switch (funcId) {
            // String functions
            case 0x0001: return upper(args);
            case 0x0002: return lower(args);
            case 0x0003: return trim(args);
            case 0x0004: return trimStart(args);
            case 0x0005: return trimEnd(args);
            case 0x0006: return substring(args);
            case 0x0007: return replace(args);
            case 0x0008: return split(args);
            case 0x0009: return join(args);
            case 0x000A: return startsWith(args);
            case 0x000B: return endsWith(args);
            case 0x000C: return contains(args);
            case 0x000D: return length(args);
            case 0x000E: return padLeft(args);
            case 0x000F: return padRight(args);
            case 0x0010: return regexMatch(args);
            case 0x0011: return regexReplace(args);

            // Numeric functions
            case 0x0020: return abs(args);
            case 0x0021: return ceil(args);
            case 0x0022: return floor(args);
            case 0x0023: return round(args);
            case 0x0024: return min(args);
            case 0x0025: return max(args);
            case 0x0026: return clamp(args);

            // List functions
            case 0x0030: return sort(args);
            case 0x0031: return sortBy(args);
            case 0x0032: return reverse(args);
            case 0x0033: return unique(args);
            case 0x0034: return flatten(args);
            case 0x0035: return zip(args);
            case 0x0036: return range(args);
            case 0x0037: return slice(args);

            // Record functions
            case 0x0040: return keys(args);
            case 0x0041: return values(args);
            case 0x0042: return merge(args);
            case 0x0043: return pick(args);
            case 0x0044: return omit(args);

            // Date/Time functions
            case 0x0050: return parseDate(args);
            case 0x0051: return formatDate(args);
            case 0x0052: return dateDiff(args);
            case 0x0053: return dateAdd(args);
            case 0x0054: return nowDate(args);

            // Encoding functions
            case 0x0060: return base64Encode(args);
            case 0x0061: return base64Decode(args);
            case 0x0062: return urlEncode(args);
            case 0x0063: return urlDecode(args);
            case 0x0064: return jsonEncode(args);
            case 0x0065: return jsonParse(args);

            // Crypto functions
            case 0x0070: return sha256(args);
            case 0x0071: return hmacSha256(args);
            case 0x0072: return uuidGen(args);

            // Conversion functions
            case 0x0074: return toStringFn(args);
            case 0x0075: return toFloatFn(args);
            case 0x0076: return toIntegerFn(args);

            // Iteration
            case 0x0078: return fold(args);

            default:
                throw new HelunaException(String.format("Unknown stdlib function 0x%04X", funcId));
        }
    }

    // ========== String Functions ==========

    private HVal upper(HVal.HRecord args) {
        return new HVal.HString(getStr(args, "value").toUpperCase());
    }

    private HVal lower(HVal.HRecord args) {
        return new HVal.HString(getStr(args, "value").toLowerCase());
    }

    private HVal trim(HVal.HRecord args) {
        return new HVal.HString(getStr(args, "value").trim());
    }

    private HVal trimStart(HVal.HRecord args) {
        String s = getStr(args, "value");
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return new HVal.HString(s.substring(i));
    }

    private HVal trimEnd(HVal.HRecord args) {
        String s = getStr(args, "value");
        int i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
        return new HVal.HString(s.substring(0, i));
    }

    private HVal substring(HVal.HRecord args) {
        String s = getStr(args, "value");
        int start = (int) getInt(args, "start");
        int end = (int) getInt(args, "end");
        int len = s.codePointCount(0, s.length());
        if (start < 0) start = 0;
        if (end > len) end = len;
        if (start >= end) return new HVal.HString("");
        int startOff = s.offsetByCodePoints(0, start);
        int endOff = s.offsetByCodePoints(0, end);
        return new HVal.HString(s.substring(startOff, endOff));
    }

    private HVal replace(HVal.HRecord args) {
        String s = getStr(args, "value");
        String find = getStr(args, "find");
        String repl = getStr(args, "replacement");
        if (find.isEmpty()) return new HVal.HString(s);
        return new HVal.HString(s.replace(find, repl));
    }

    private HVal split(HVal.HRecord args) {
        String s = getStr(args, "value");
        String delim = getStr(args, "delimiter");
        HVal.HList result = new HVal.HList();
        if (delim.isEmpty()) {
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                result.add(new HVal.HString(new String(Character.toChars(cp))));
                i += Character.charCount(cp);
            }
        } else {
            String[] parts = s.split(Pattern.quote(delim), -1);
            for (String part : parts) {
                result.add(new HVal.HString(part));
            }
        }
        return result;
    }

    private HVal join(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        String delim = getStr(args, "delimiter");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(delim);
            sb.append(Executor.valToString(list.elements().get(i)));
        }
        return new HVal.HString(sb.toString());
    }

    private HVal startsWith(HVal.HRecord args) {
        return HVal.HBoolean.of(getStr(args, "value").startsWith(getStr(args, "prefix")));
    }

    private HVal endsWith(HVal.HRecord args) {
        return HVal.HBoolean.of(getStr(args, "value").endsWith(getStr(args, "suffix")));
    }

    private HVal contains(HVal.HRecord args) {
        String s = getStr(args, "value");
        // The spec uses "search" for the contains function parameter name
        HVal sub = args.get("substring");
        if (sub.isNothing()) sub = args.get("search");
        if (sub instanceof HVal.HString) {
            return HVal.HBoolean.of(s.contains(((HVal.HString) sub).value()));
        }
        throw new HelunaException("contains: missing substring/search argument");
    }

    private HVal length(HVal.HRecord args) {
        HVal listVal = args.get("list");
        if (listVal instanceof HVal.HList) {
            return new HVal.HInteger(((HVal.HList) listVal).size());
        }
        String s = getStr(args, "value");
        return new HVal.HInteger(s.codePointCount(0, s.length()));
    }

    private HVal padLeft(HVal.HRecord args) {
        String s = getStr(args, "value");
        int width = (int) getInt(args, "width");
        String fill = getStr(args, "fill");
        if (fill.isEmpty()) fill = " ";
        while (s.length() < width) {
            s = fill + s;
        }
        if (s.length() > width && s.length() > getStr(args, "value").length()) {
            s = s.substring(s.length() - Math.max(width, getStr(args, "value").length()));
        }
        return new HVal.HString(s);
    }

    private HVal padRight(HVal.HRecord args) {
        String s = getStr(args, "value");
        int width = (int) getInt(args, "width");
        String fill = getStr(args, "fill");
        if (fill.isEmpty()) fill = " ";
        while (s.length() < width) {
            s = s + fill;
        }
        if (s.length() > width && s.length() > getStr(args, "value").length()) {
            s = s.substring(0, Math.max(width, getStr(args, "value").length()));
        }
        return new HVal.HString(s);
    }

    private HVal regexMatch(HVal.HRecord args) {
        String s = getStr(args, "value");
        String pattern = getStr(args, "pattern");
        try {
            return HVal.HBoolean.of(s.matches("(?s)" + pattern));
        } catch (Exception e) {
            throw new HelunaException("Invalid regex: " + pattern);
        }
    }

    private HVal regexReplace(HVal.HRecord args) {
        String s = getStr(args, "value");
        String pattern = getStr(args, "pattern");
        String replacement = getStr(args, "replacement");
        try {
            return new HVal.HString(s.replaceAll(pattern, Matcher.quoteReplacement(replacement)));
        } catch (Exception e) {
            throw new HelunaException("Invalid regex: " + pattern);
        }
    }

    // ========== Numeric Functions ==========

    private HVal abs(HVal.HRecord args) {
        HVal v = args.get("value");
        if (v instanceof HVal.HInteger) return new HVal.HInteger(Math.abs(((HVal.HInteger) v).value()));
        if (v instanceof HVal.HFloat) return new HVal.HFloat(Math.abs(((HVal.HFloat) v).value()));
        throw new HelunaException("abs: expected number");
    }

    private HVal ceil(HVal.HRecord args) {
        return new HVal.HInteger((long) Math.ceil(getNum(args, "value")));
    }

    private HVal floor(HVal.HRecord args) {
        return new HVal.HInteger((long) Math.floor(getNum(args, "value")));
    }

    private HVal round(HVal.HRecord args) {
        double v = getNum(args, "value");
        return new HVal.HInteger((long) Math.floor(v + 0.5));
    }

    private HVal min(HVal.HRecord args) {
        HVal a = args.get("a"), b = args.get("b");
        double da = toDouble(a), db = toDouble(b);
        if (da <= db) return a;
        return b;
    }

    private HVal max(HVal.HRecord args) {
        HVal a = args.get("a"), b = args.get("b");
        double da = toDouble(a), db = toDouble(b);
        if (da >= db) return a;
        return b;
    }

    private HVal clamp(HVal.HRecord args) {
        HVal v = args.get("value");
        double dv = toDouble(v);
        double lo = getNum(args, "low");
        if (lo == 0 && args.get("low").isNothing()) lo = getNum(args, "min");
        double hi = getNum(args, "high");
        if (hi == 0 && args.get("high").isNothing()) hi = getNum(args, "max");
        if (dv < lo) return numFromDouble(lo, v);
        if (dv > hi) return numFromDouble(hi, v);
        return v;
    }

    // ========== List Functions ==========

    private HVal sort(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        ArrayList<HVal> sorted = new ArrayList<>(list.elements());
        sorted.sort((a, b) -> {
            if ((a instanceof HVal.HInteger || a instanceof HVal.HFloat) &&
                (b instanceof HVal.HInteger || b instanceof HVal.HFloat)) {
                return Double.compare(toDouble(a), toDouble(b));
            }
            if (a instanceof HVal.HString && b instanceof HVal.HString) {
                return ((HVal.HString) a).value().compareTo(((HVal.HString) b).value());
            }
            return 0;
        });
        return new HVal.HList(sorted);
    }

    private HVal sortBy(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        String field = getStr(args, "field");
        ArrayList<HVal> sorted = new ArrayList<>(list.elements());
        sorted.sort((a, b) -> {
            HVal fa = (a instanceof HVal.HRecord) ? ((HVal.HRecord) a).get(field) : HVal.HNothing.INSTANCE;
            HVal fb = (b instanceof HVal.HRecord) ? ((HVal.HRecord) b).get(field) : HVal.HNothing.INSTANCE;
            if ((fa instanceof HVal.HInteger || fa instanceof HVal.HFloat) &&
                (fb instanceof HVal.HInteger || fb instanceof HVal.HFloat)) {
                return Double.compare(toDouble(fa), toDouble(fb));
            }
            if (fa instanceof HVal.HString && fb instanceof HVal.HString) {
                return ((HVal.HString) fa).value().compareTo(((HVal.HString) fb).value());
            }
            return 0;
        });
        return new HVal.HList(sorted);
    }

    private HVal reverse(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        ArrayList<HVal> reversed = new ArrayList<>(list.elements());
        Collections.reverse(reversed);
        return new HVal.HList(reversed);
    }

    private HVal unique(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        HVal.HList result = new HVal.HList();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (HVal v : list.elements()) {
            String key = v.toString();
            if (seen.add(key)) result.add(v);
        }
        return result;
    }

    private HVal flatten(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        HVal.HList result = new HVal.HList();
        for (HVal v : list.elements()) {
            if (v instanceof HVal.HList) {
                for (HVal inner : ((HVal.HList) v).elements()) {
                    result.add(inner);
                }
            } else {
                result.add(v);
            }
        }
        return result;
    }

    private HVal zip(HVal.HRecord args) {
        HVal.HList a = getList(args, "a");
        HVal.HList b = getList(args, "b");
        HVal.HList result = new HVal.HList();
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            HVal.HRecord rec = new HVal.HRecord();
            rec.set("a", a.elements().get(i));
            rec.set("b", b.elements().get(i));
            result.add(rec);
        }
        return result;
    }

    private HVal range(HVal.HRecord args) {
        long start = getInt(args, "start");
        long end = getInt(args, "end");
        HVal.HList result = new HVal.HList();
        if (start <= end) {
            for (long i = start; i <= end; i++) result.add(new HVal.HInteger(i));
        } else {
            for (long i = start; i >= end; i--) result.add(new HVal.HInteger(i));
        }
        return result;
    }

    private HVal slice(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        int start = (int) getInt(args, "start");
        int end = (int) getInt(args, "end");
        if (start < 0) start = 0;
        if (end > list.size()) end = list.size();
        HVal.HList result = new HVal.HList();
        for (int i = start; i < end; i++) {
            result.add(list.elements().get(i));
        }
        return result;
    }

    // ========== Record Functions ==========

    private HVal keys(HVal.HRecord args) {
        HVal.HRecord rec = getRecord(args, "record");
        HVal.HList result = new HVal.HList();
        for (String key : rec.fields().keySet()) {
            result.add(new HVal.HString(key));
        }
        return result;
    }

    private HVal values(HVal.HRecord args) {
        HVal.HRecord rec = getRecord(args, "record");
        HVal.HList result = new HVal.HList();
        for (HVal val : rec.fields().values()) {
            result.add(val);
        }
        return result;
    }

    private HVal merge(HVal.HRecord args) {
        HVal.HRecord a = getRecord(args, "a");
        HVal.HRecord b = getRecord(args, "b");
        HVal.HRecord result = new HVal.HRecord();
        for (var e : a.fields().entrySet()) result.set(e.getKey(), e.getValue());
        for (var e : b.fields().entrySet()) result.set(e.getKey(), e.getValue());
        return result;
    }

    private HVal pick(HVal.HRecord args) {
        HVal.HRecord rec = getRecord(args, "record");
        HVal.HList fields = getList(args, "fields");
        HVal.HRecord result = new HVal.HRecord();
        for (HVal f : fields.elements()) {
            String name = ((HVal.HString) f).value();
            if (rec.has(name)) result.set(name, rec.get(name));
        }
        return result;
    }

    private HVal omit(HVal.HRecord args) {
        HVal.HRecord rec = getRecord(args, "record");
        HVal.HList fields = getList(args, "fields");
        LinkedHashSet<String> omitSet = new LinkedHashSet<>();
        for (HVal f : fields.elements()) omitSet.add(((HVal.HString) f).value());
        HVal.HRecord result = new HVal.HRecord();
        for (var e : rec.fields().entrySet()) {
            if (!omitSet.contains(e.getKey())) result.set(e.getKey(), e.getValue());
        }
        return result;
    }

    // ========== Date/Time Functions ==========

    private HVal parseDate(HVal.HRecord args) {
        String value = getStr(args, "value");
        String format = getStr(args, "format");
        DateTimeFormatter fmt = convertFormat(format);
        LocalDateTime dt = LocalDateTime.parse(value, fmt);
        HVal.HRecord result = new HVal.HRecord();
        result.set("year", new HVal.HInteger(dt.getYear()));
        result.set("month", new HVal.HInteger(dt.getMonthValue()));
        result.set("day", new HVal.HInteger(dt.getDayOfMonth()));
        result.set("hour", new HVal.HInteger(dt.getHour()));
        result.set("minute", new HVal.HInteger(dt.getMinute()));
        result.set("second", new HVal.HInteger(dt.getSecond()));
        return result;
    }

    private HVal formatDate(HVal.HRecord args) {
        HVal.HRecord date = getRecord(args, "date");
        String format = getStr(args, "format");
        int year = (int) getIntFromRecord(date, "year");
        int month = (int) getIntFromRecord(date, "month");
        int day = (int) getIntFromRecord(date, "day");
        int hour = (int) getIntFromRecord(date, "hour");
        int minute = (int) getIntFromRecord(date, "minute");
        int second = (int) getIntFromRecord(date, "second");
        LocalDateTime dt = LocalDateTime.of(year, month, day, hour, minute, second);
        return new HVal.HString(dt.format(convertFormat(format)));
    }

    private HVal dateDiff(HVal.HRecord args) {
        String from = getStr(args, "from");
        if (from.isEmpty()) from = getStr(args, "a");
        String to = getStr(args, "to");
        if (to.isEmpty()) to = getStr(args, "b");
        String unit = getStr(args, "unit");
        LocalDateTime dtFrom = parseISO(from);
        LocalDateTime dtTo = parseISO(to);
        long diff;
        switch (unit) {
            case "seconds": diff = ChronoUnit.SECONDS.between(dtFrom, dtTo); break;
            case "minutes": diff = ChronoUnit.MINUTES.between(dtFrom, dtTo); break;
            case "hours":   diff = ChronoUnit.HOURS.between(dtFrom, dtTo); break;
            case "days":    diff = ChronoUnit.DAYS.between(dtFrom, dtTo); break;
            default: throw new HelunaException("dateDiff: unknown unit " + unit);
        }
        return new HVal.HInteger(diff);
    }

    private HVal dateAdd(HVal.HRecord args) {
        String date = getStr(args, "date");
        long amount = getInt(args, "amount");
        String unit = getStr(args, "unit");
        LocalDateTime dt = parseISO(date);
        switch (unit) {
            case "seconds": dt = dt.plusSeconds(amount); break;
            case "minutes": dt = dt.plusMinutes(amount); break;
            case "hours":   dt = dt.plusHours(amount); break;
            case "days":    dt = dt.plusDays(amount); break;
            case "months":  dt = dt.plusMonths(amount); break;
            case "years":   dt = dt.plusYears(amount); break;
            default: throw new HelunaException("dateAdd: unknown unit " + unit);
        }
        // Preserve input format: date-only input gets date-only output
        if (!date.contains("T")) {
            return new HVal.HString(dt.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return new HVal.HString(dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
    }

    private HVal nowDate(HVal.HRecord args) {
        return new HVal.HString(timestamp);
    }

    // ========== Encoding Functions ==========

    private HVal base64Encode(HVal.HRecord args) {
        String s = getStr(args, "value");
        return new HVal.HString(Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private HVal base64Decode(HVal.HRecord args) {
        String s = getStr(args, "value");
        return new HVal.HString(new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8));
    }

    private HVal urlEncode(HVal.HRecord args) {
        String s = getStr(args, "value");
        return new HVal.HString(URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private HVal urlDecode(HVal.HRecord args) {
        String s = getStr(args, "value");
        return new HVal.HString(URLDecoder.decode(s, StandardCharsets.UTF_8));
    }

    private HVal jsonEncode(HVal.HRecord args) {
        HVal v = args.get("value");
        return new HVal.HString(toJson(v));
    }

    private HVal jsonParse(HVal.HRecord args) {
        String s = getStr(args, "value");
        return parseJsonValue(s.trim(), new int[]{0});
    }

    // ========== Crypto Functions ==========

    private HVal sha256(HVal.HRecord args) {
        String s = getStr(args, "value");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return new HVal.HString(bytesToHex(hash));
        } catch (Exception e) {
            throw new HelunaException("sha256 failed: " + e.getMessage());
        }
    }

    private HVal hmacSha256(HVal.HRecord args) {
        String value = getStr(args, "value");
        String key = getStr(args, "key");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return new HVal.HString(bytesToHex(hash));
        } catch (Exception e) {
            throw new HelunaException("hmac-sha256 failed: " + e.getMessage());
        }
    }

    private HVal uuidGen(HVal.HRecord args) {
        return new HVal.HString(UUID.randomUUID().toString());
    }

    // ========== Conversion Functions ==========

    private HVal toStringFn(HVal.HRecord args) {
        return new HVal.HString(Executor.valToString(args.get("value")));
    }

    private HVal toFloatFn(HVal.HRecord args) {
        HVal v = args.get("value");
        if (v instanceof HVal.HFloat) return v;
        if (v instanceof HVal.HInteger) return new HVal.HFloat((double) ((HVal.HInteger) v).value());
        if (v instanceof HVal.HString) return new HVal.HFloat(Double.parseDouble(((HVal.HString) v).value()));
        throw new HelunaException("to-float: cannot convert " + Executor.typeName(v));
    }

    private HVal toIntegerFn(HVal.HRecord args) {
        HVal v = args.get("value");
        if (v instanceof HVal.HInteger) return v;
        if (v instanceof HVal.HFloat) return new HVal.HInteger((long) ((HVal.HFloat) v).value());
        if (v instanceof HVal.HString) {
            String s = ((HVal.HString) v).value();
            try { return new HVal.HInteger(Long.parseLong(s)); }
            catch (NumberFormatException e) {
                return new HVal.HInteger((long) Double.parseDouble(s));
            }
        }
        throw new HelunaException("to-integer: cannot convert " + Executor.typeName(v));
    }

    // ========== Iteration ==========

    private HVal fold(HVal.HRecord args) {
        HVal.HList list = getList(args, "list");
        HVal acc = args.get("initial");
        String fn = getStr(args, "fn");
        for (HVal elem : list.elements()) {
            switch (fn) {
                case "add":
                    acc = addValues(acc, elem);
                    break;
                case "multiply":
                    acc = mulValues(acc, elem);
                    break;
                default:
                    throw new HelunaException("fold: unknown fn " + fn);
            }
        }
        return acc;
    }

    // ========== Helpers ==========

    private String getStr(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        if (v instanceof HVal.HString) return ((HVal.HString) v).value();
        if (v.isNothing()) return "";
        return Executor.valToString(v);
    }

    private long getInt(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        if (v instanceof HVal.HInteger) return ((HVal.HInteger) v).value();
        if (v instanceof HVal.HFloat) return (long) ((HVal.HFloat) v).value();
        return 0;
    }

    private long getIntFromRecord(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        if (v instanceof HVal.HInteger) return ((HVal.HInteger) v).value();
        return 0;
    }

    private double getNum(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        return toDouble(v);
    }

    private HVal.HList getList(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        if (v instanceof HVal.HList) return (HVal.HList) v;
        return new HVal.HList();
    }

    private HVal.HRecord getRecord(HVal.HRecord rec, String field) {
        HVal v = rec.get(field);
        if (v instanceof HVal.HRecord) return (HVal.HRecord) v;
        return new HVal.HRecord();
    }

    private double toDouble(HVal v) {
        if (v instanceof HVal.HInteger) return (double) ((HVal.HInteger) v).value();
        if (v instanceof HVal.HFloat) return ((HVal.HFloat) v).value();
        return 0;
    }

    private HVal numFromDouble(double d, HVal reference) {
        if (reference instanceof HVal.HInteger) return new HVal.HInteger((long) d);
        return new HVal.HFloat(d);
    }

    private HVal addValues(HVal a, HVal b) {
        if (a instanceof HVal.HInteger && b instanceof HVal.HInteger) {
            return new HVal.HInteger(((HVal.HInteger) a).value() + ((HVal.HInteger) b).value());
        }
        return new HVal.HFloat(toDouble(a) + toDouble(b));
    }

    private HVal mulValues(HVal a, HVal b) {
        if (a instanceof HVal.HInteger && b instanceof HVal.HInteger) {
            return new HVal.HInteger(((HVal.HInteger) a).value() * ((HVal.HInteger) b).value());
        }
        return new HVal.HFloat(toDouble(a) * toDouble(b));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private LocalDateTime parseISO(String s) {
        s = s.replace("Z", "");
        if (s.contains("T")) {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return LocalDateTime.parse(s + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private DateTimeFormatter convertFormat(String format) {
        // Convert strftime-style format to Java DateTimeFormatter
        String java = format
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%M", "mm")
                .replace("%S", "ss");
        return DateTimeFormatter.ofPattern(java);
    }

    // ========== JSON serialization/parsing ==========

    static String toJson(HVal v) {
        if (v instanceof HVal.HString) {
            return "\"" + escapeJson(((HVal.HString) v).value()) + "\"";
        }
        if (v instanceof HVal.HInteger) return Long.toString(((HVal.HInteger) v).value());
        if (v instanceof HVal.HFloat) {
            double d = ((HVal.HFloat) v).value();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d) + ".0";
            }
            return Double.toString(d);
        }
        if (v instanceof HVal.HBoolean) return ((HVal.HBoolean) v).value() ? "true" : "false";
        if (v instanceof HVal.HNothing) return "null";
        if (v instanceof HVal.HList) {
            StringBuilder sb = new StringBuilder("[");
            HVal.HList list = (HVal.HList) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.elements().get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof HVal.HRecord) {
            StringBuilder sb = new StringBuilder("{");
            HVal.HRecord rec = (HVal.HRecord) v;
            boolean first = true;
            for (var e : rec.fields().entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(e.getKey())).append("\":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "null";
    }

    private static String escapeJson(String s) {
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

    static HVal parseJsonValue(String s, int[] pos) {
        skipWhitespace(s, pos);
        if (pos[0] >= s.length()) return HVal.HNothing.INSTANCE;
        char c = s.charAt(pos[0]);
        if (c == '"') return parseJsonString(s, pos);
        if (c == '{') return parseJsonObject(s, pos);
        if (c == '[') return parseJsonArray(s, pos);
        if (c == 't') { pos[0] += 4; return HVal.HBoolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return HVal.HBoolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return HVal.HNothing.INSTANCE; }
        return parseJsonNumber(s, pos);
    }

    private static HVal parseJsonString(String s, int[] pos) {
        pos[0]++; // skip opening "
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '"') { pos[0]++; return new HVal.HString(sb.toString()); }
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
                        String hex = s.substring(pos[0] + 1, pos[0] + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos[0] += 4;
                        break;
                }
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        throw new HelunaException("Unterminated JSON string");
    }

    private static HVal parseJsonNumber(String s, int[] pos) {
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
        if (isFloat) return new HVal.HFloat(Double.parseDouble(num));
        return new HVal.HInteger(Long.parseLong(num));
    }

    private static HVal parseJsonObject(String s, int[] pos) {
        pos[0]++; // skip {
        HVal.HRecord rec = new HVal.HRecord();
        skipWhitespace(s, pos);
        if (s.charAt(pos[0]) == '}') { pos[0]++; return rec; }
        while (true) {
            skipWhitespace(s, pos);
            HVal key = parseJsonString(s, pos);
            skipWhitespace(s, pos);
            pos[0]++; // skip :
            HVal val = parseJsonValue(s, pos);
            rec.set(((HVal.HString) key).value(), val);
            skipWhitespace(s, pos);
            if (s.charAt(pos[0]) == '}') { pos[0]++; return rec; }
            pos[0]++; // skip ,
        }
    }

    private static HVal parseJsonArray(String s, int[] pos) {
        pos[0]++; // skip [
        HVal.HList list = new HVal.HList();
        skipWhitespace(s, pos);
        if (s.charAt(pos[0]) == ']') { pos[0]++; return list; }
        while (true) {
            list.add(parseJsonValue(s, pos));
            skipWhitespace(s, pos);
            if (s.charAt(pos[0]) == ']') { pos[0]++; return list; }
            pos[0]++; // skip ,
        }
    }

    private static void skipWhitespace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) pos[0]++;
    }
}
