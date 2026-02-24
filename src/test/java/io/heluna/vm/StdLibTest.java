package io.heluna.vm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StdLibTest {

    private StdLib stdlib;

    @BeforeEach
    void setUp() {
        stdlib = new StdLib();
        stdlib.setTimestamp("2024-06-15T12:30:00Z");
    }

    // Helper: build a record from key-value pairs
    private static HVal.HRecord rec(Object... kvs) {
        HVal.HRecord r = new HVal.HRecord();
        for (int i = 0; i < kvs.length; i += 2) {
            String key = (String) kvs[i];
            Object val = kvs[i + 1];
            if (val instanceof HVal) r.set(key, (HVal) val);
            else if (val instanceof String) r.set(key, new HVal.HString((String) val));
            else if (val instanceof Long) r.set(key, new HVal.HInteger((Long) val));
            else if (val instanceof Integer) r.set(key, new HVal.HInteger(((Integer) val).longValue()));
            else if (val instanceof Double) r.set(key, new HVal.HFloat((Double) val));
            else if (val instanceof Boolean) r.set(key, HVal.HBoolean.of((Boolean) val));
            else throw new IllegalArgumentException("Unsupported type: " + val.getClass());
        }
        return r;
    }

    private static HVal.HList listOf(HVal... items) {
        HVal.HList list = new HVal.HList();
        for (HVal v : items) list.add(v);
        return list;
    }

    private static HVal.HInteger i(long v) { return new HVal.HInteger(v); }
    private static HVal.HFloat f(double v) { return new HVal.HFloat(v); }
    private static HVal.HString s(String v) { return new HVal.HString(v); }

    // ========== String Functions ==========

    @Test void upperBasic() {
        assertEquals(s("HELLO"), stdlib.call(0x0001, rec("value", "hello")));
    }

    @Test void upperEmpty() {
        assertEquals(s(""), stdlib.call(0x0001, rec("value", "")));
    }

    @Test void upperUnicode() {
        assertEquals(s("STRASSE"), stdlib.call(0x0001, rec("value", "strasse")));
    }

    @Test void lowerBasic() {
        assertEquals(s("hello"), stdlib.call(0x0002, rec("value", "HELLO")));
    }

    @Test void lowerEmpty() {
        assertEquals(s(""), stdlib.call(0x0002, rec("value", "")));
    }

    @Test void trimWhitespace() {
        assertEquals(s("hello"), stdlib.call(0x0003, rec("value", "  hello  ")));
    }

    @Test void trimAlreadyTrimmed() {
        assertEquals(s("hello"), stdlib.call(0x0003, rec("value", "hello")));
    }

    @Test void trimStartWhitespace() {
        assertEquals(s("hello  "), stdlib.call(0x0004, rec("value", "  hello  ")));
    }

    @Test void trimEndWhitespace() {
        assertEquals(s("  hello"), stdlib.call(0x0005, rec("value", "  hello  ")));
    }

    @Test void substringNormal() {
        assertEquals(s("ell"), stdlib.call(0x0006, rec("value", "hello", "start", 1, "end", 4)));
    }

    @Test void substringClamped() {
        assertEquals(s("hello"), stdlib.call(0x0006, rec("value", "hello", "start", -5, "end", 100)));
    }

    @Test void substringStartGteEnd() {
        assertEquals(s(""), stdlib.call(0x0006, rec("value", "hello", "start", 3, "end", 2)));
    }

    @Test void substringUnicode() {
        // Test with multi-byte characters - uses codepoint-based indexing
        assertEquals(s("bc"), stdlib.call(0x0006, rec("value", "abcd", "start", 1, "end", 3)));
    }

    @Test void replaceBasic() {
        assertEquals(s("herro"), stdlib.call(0x0007, rec("value", "hello", "find", "ll", "replacement", "rr")));
    }

    @Test void replaceEmptyFind() {
        assertEquals(s("hello"), stdlib.call(0x0007, rec("value", "hello", "find", "", "replacement", "x")));
    }

    @Test void replaceNoMatch() {
        assertEquals(s("hello"), stdlib.call(0x0007, rec("value", "hello", "find", "xyz", "replacement", "!")));
    }

    @Test void splitWithDelimiter() {
        HVal result = stdlib.call(0x0008, rec("value", "a,b,c", "delimiter", ","));
        assertTrue(result instanceof HVal.HList);
        HVal.HList list = (HVal.HList) result;
        assertEquals(3, list.size());
        assertEquals(s("a"), list.get(0));
        assertEquals(s("b"), list.get(1));
        assertEquals(s("c"), list.get(2));
    }

    @Test void splitEmptyDelimiter() {
        HVal result = stdlib.call(0x0008, rec("value", "hi", "delimiter", ""));
        HVal.HList list = (HVal.HList) result;
        assertEquals(2, list.size());
        assertEquals(s("h"), list.get(0));
        assertEquals(s("i"), list.get(1));
    }

    @Test void splitEmptyInput() {
        HVal result = stdlib.call(0x0008, rec("value", "", "delimiter", ","));
        HVal.HList list = (HVal.HList) result;
        assertEquals(1, list.size());
        assertEquals(s(""), list.get(0));
    }

    @Test void joinBasic() {
        HVal result = stdlib.call(0x0009, rec("list", listOf(s("a"), s("b"), s("c")), "delimiter", ", "));
        assertEquals(s("a, b, c"), result);
    }

    @Test void joinEmptyList() {
        assertEquals(s(""), stdlib.call(0x0009, rec("list", listOf(), "delimiter", ",")));
    }

    @Test void joinEmptyDelimiter() {
        assertEquals(s("abc"), stdlib.call(0x0009, rec("list", listOf(s("a"), s("b"), s("c")), "delimiter", "")));
    }

    @Test void startsWithTrue() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x000A, rec("value", "hello", "prefix", "hel")));
    }

    @Test void startsWithFalse() {
        assertEquals(HVal.HBoolean.FALSE, stdlib.call(0x000A, rec("value", "hello", "prefix", "xyz")));
    }

    @Test void startsWithEmptyPrefix() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x000A, rec("value", "hello", "prefix", "")));
    }

    @Test void endsWithTrue() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x000B, rec("value", "hello", "suffix", "llo")));
    }

    @Test void endsWithFalse() {
        assertEquals(HVal.HBoolean.FALSE, stdlib.call(0x000B, rec("value", "hello", "suffix", "xyz")));
    }

    @Test void containsWithSubstring() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x000C, rec("value", "hello world", "substring", "lo wo")));
    }

    @Test void containsFalse() {
        assertEquals(HVal.HBoolean.FALSE, stdlib.call(0x000C, rec("value", "hello", "substring", "xyz")));
    }

    @Test void containsSearchAlias() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x000C, rec("value", "hello", "search", "ell")));
    }

    @Test void lengthString() {
        assertEquals(i(5), stdlib.call(0x000D, rec("value", "hello")));
    }

    @Test void lengthList() {
        assertEquals(i(3), stdlib.call(0x000D, rec("list", listOf(i(1), i(2), i(3)))));
    }

    @Test void padLeftBasic() {
        assertEquals(s("00042"), stdlib.call(0x000E, rec("value", "42", "width", 5, "fill", "0")));
    }

    @Test void padLeftAlreadyWide() {
        assertEquals(s("hello"), stdlib.call(0x000E, rec("value", "hello", "width", 3, "fill", " ")));
    }

    @Test void padRightBasic() {
        assertEquals(s("hi..."), stdlib.call(0x000F, rec("value", "hi", "width", 5, "fill", ".")));
    }

    @Test void padRightAlreadyWide() {
        assertEquals(s("hello"), stdlib.call(0x000F, rec("value", "hello", "width", 3, "fill", " ")));
    }

    @Test void regexMatchTrue() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x0010, rec("value", "abc123", "pattern", ".*\\d+")));
    }

    @Test void regexMatchFalse() {
        assertEquals(HVal.HBoolean.FALSE, stdlib.call(0x0010, rec("value", "abc", "pattern", "^\\d+$")));
    }

    @Test void regexMatchInvalidThrows() {
        assertThrows(HelunaException.class,
                () -> stdlib.call(0x0010, rec("value", "test", "pattern", "[invalid")));
    }

    @Test void regexReplaceBasic() {
        assertEquals(s("abcNUMdef"), stdlib.call(0x0011,
                rec("value", "abc123def", "pattern", "\\d+", "replacement", "NUM")));
    }

    @Test void regexReplaceNoMatch() {
        assertEquals(s("hello"), stdlib.call(0x0011,
                rec("value", "hello", "pattern", "\\d+", "replacement", "X")));
    }

    // ========== Numeric Functions ==========

    @Test void absPositive() {
        assertEquals(i(7), stdlib.call(0x0020, rec("value", i(7))));
    }

    @Test void absNegative() {
        assertEquals(i(7), stdlib.call(0x0020, rec("value", i(-7))));
    }

    @Test void absZero() {
        assertEquals(i(0), stdlib.call(0x0020, rec("value", i(0))));
    }

    @Test void absFloat() {
        assertEquals(f(3.14), stdlib.call(0x0020, rec("value", f(-3.14))));
    }

    @Test void ceilPositive() {
        assertEquals(i(4), stdlib.call(0x0021, rec("value", f(3.2))));
    }

    @Test void ceilNegative() {
        assertEquals(i(-3), stdlib.call(0x0021, rec("value", f(-3.8))));
    }

    @Test void ceilAlreadyWhole() {
        assertEquals(i(5), stdlib.call(0x0021, rec("value", f(5.0))));
    }

    @Test void floorPositive() {
        assertEquals(i(3), stdlib.call(0x0022, rec("value", f(3.8))));
    }

    @Test void floorNegative() {
        assertEquals(i(-4), stdlib.call(0x0022, rec("value", f(-3.2))));
    }

    @Test void floorAlreadyWhole() {
        assertEquals(i(5), stdlib.call(0x0022, rec("value", f(5.0))));
    }

    @Test void roundHalf() {
        assertEquals(i(4), stdlib.call(0x0023, rec("value", f(3.5))));
    }

    @Test void roundNegative() {
        assertEquals(i(-3), stdlib.call(0x0023, rec("value", f(-3.4))));
    }

    @Test void minInts() {
        assertEquals(i(3), stdlib.call(0x0024, rec("a", i(3), "b", i(7))));
    }

    @Test void minFloats() {
        assertEquals(f(1.5), stdlib.call(0x0024, rec("a", f(1.5), "b", f(2.5))));
    }

    @Test void minCrossType() {
        assertEquals(i(2), stdlib.call(0x0024, rec("a", i(2), "b", f(3.5))));
    }

    @Test void maxInts() {
        assertEquals(i(7), stdlib.call(0x0025, rec("a", i(3), "b", i(7))));
    }

    @Test void maxFloats() {
        assertEquals(f(2.5), stdlib.call(0x0025, rec("a", f(1.5), "b", f(2.5))));
    }

    @Test void maxCrossType() {
        assertEquals(f(3.5), stdlib.call(0x0025, rec("a", i(2), "b", f(3.5))));
    }

    @Test void clampInRange() {
        assertEquals(i(5), stdlib.call(0x0026, rec("value", i(5), "low", i(1), "high", i(10))));
    }

    @Test void clampBelowLow() {
        assertEquals(i(1), stdlib.call(0x0026, rec("value", i(-5), "low", i(1), "high", i(10))));
    }

    @Test void clampAboveHigh() {
        assertEquals(i(10), stdlib.call(0x0026, rec("value", i(20), "low", i(1), "high", i(10))));
    }

    @Test void clampMinMaxAliases() {
        assertEquals(i(5), stdlib.call(0x0026, rec("value", i(5), "min", i(1), "max", i(10))));
    }

    // ========== List Functions ==========

    @Test void sortIntegers() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0030, rec("list", listOf(i(3), i(1), i(2))));
        assertEquals(i(1), result.get(0));
        assertEquals(i(2), result.get(1));
        assertEquals(i(3), result.get(2));
    }

    @Test void sortStrings() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0030, rec("list", listOf(s("banana"), s("apple"), s("cherry"))));
        assertEquals(s("apple"), result.get(0));
        assertEquals(s("banana"), result.get(1));
        assertEquals(s("cherry"), result.get(2));
    }

    @Test void sortEmpty() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0030, rec("list", listOf()));
        assertEquals(0, result.size());
    }

    @Test void sortMixedIntFloat() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0030, rec("list", listOf(f(2.5), i(1), f(1.5))));
        assertEquals(i(1), result.get(0));
        assertEquals(f(1.5), result.get(1));
        assertEquals(f(2.5), result.get(2));
    }

    @Test void sortByField() {
        HVal.HRecord r1 = rec("name", "Charlie", "age", 25);
        HVal.HRecord r2 = rec("name", "Alice", "age", 30);
        HVal.HRecord r3 = rec("name", "Bob", "age", 20);
        HVal.HList result = (HVal.HList) stdlib.call(0x0031, rec("list", listOf(r1, r2, r3), "field", "age"));
        assertEquals(s("Bob"), ((HVal.HRecord) result.get(0)).get("name"));
        assertEquals(s("Charlie"), ((HVal.HRecord) result.get(1)).get("name"));
        assertEquals(s("Alice"), ((HVal.HRecord) result.get(2)).get("name"));
    }

    @Test void sortByEmptyList() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0031, rec("list", listOf(), "field", "x"));
        assertEquals(0, result.size());
    }

    @Test void reverseNormal() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0032, rec("list", listOf(i(1), i(2), i(3))));
        assertEquals(i(3), result.get(0));
        assertEquals(i(2), result.get(1));
        assertEquals(i(1), result.get(2));
    }

    @Test void reverseEmpty() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0032, rec("list", listOf()));
        assertEquals(0, result.size());
    }

    @Test void reverseSingle() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0032, rec("list", listOf(i(42))));
        assertEquals(1, result.size());
        assertEquals(i(42), result.get(0));
    }

    @Test void uniqueWithDuplicates() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0033, rec("list", listOf(i(1), i(2), i(1), i(3), i(2))));
        assertEquals(3, result.size());
        assertEquals(i(1), result.get(0));
        assertEquals(i(2), result.get(1));
        assertEquals(i(3), result.get(2));
    }

    @Test void uniqueAlreadyUnique() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0033, rec("list", listOf(i(1), i(2), i(3))));
        assertEquals(3, result.size());
    }

    @Test void flattenNestedLists() {
        HVal.HList inner1 = listOf(i(1), i(2));
        HVal.HList inner2 = listOf(i(3), i(4));
        HVal.HList result = (HVal.HList) stdlib.call(0x0034, rec("list", listOf(inner1, inner2, i(5))));
        assertEquals(5, result.size());
        assertEquals(i(1), result.get(0));
        assertEquals(i(2), result.get(1));
        assertEquals(i(3), result.get(2));
        assertEquals(i(4), result.get(3));
        assertEquals(i(5), result.get(4));
    }

    @Test void zipEqualLength() {
        HVal.HList a = listOf(i(1), i(2));
        HVal.HList b = listOf(s("a"), s("b"));
        HVal.HList result = (HVal.HList) stdlib.call(0x0035, rec("a", a, "b", b));
        assertEquals(2, result.size());
        HVal.HRecord first = (HVal.HRecord) result.get(0);
        assertEquals(i(1), first.get("a"));
        assertEquals(s("a"), first.get("b"));
    }

    @Test void zipMismatchedLength() {
        HVal.HList a = listOf(i(1), i(2), i(3));
        HVal.HList b = listOf(s("a"), s("b"));
        HVal.HList result = (HVal.HList) stdlib.call(0x0035, rec("a", a, "b", b));
        assertEquals(2, result.size()); // truncates to shorter
    }

    @Test void rangeAscending() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0036, rec("start", 1, "end", 5));
        assertEquals(5, result.size());
        assertEquals(i(1), result.get(0));
        assertEquals(i(5), result.get(4));
    }

    @Test void rangeDescending() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0036, rec("start", 5, "end", 3));
        assertEquals(3, result.size());
        assertEquals(i(5), result.get(0));
        assertEquals(i(4), result.get(1));
        assertEquals(i(3), result.get(2));
    }

    @Test void rangeSingleElement() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0036, rec("start", 3, "end", 3));
        assertEquals(1, result.size());
        assertEquals(i(3), result.get(0));
    }

    @Test void sliceNormal() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0037,
                rec("list", listOf(i(10), i(20), i(30), i(40), i(50)), "start", 1, "end", 3));
        assertEquals(2, result.size());
        assertEquals(i(20), result.get(0));
        assertEquals(i(30), result.get(1));
    }

    @Test void sliceClampedBounds() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0037,
                rec("list", listOf(i(10), i(20), i(30)), "start", -1, "end", 100));
        assertEquals(3, result.size());
    }

    @Test void sliceStartGteEnd() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0037,
                rec("list", listOf(i(10), i(20), i(30)), "start", 2, "end", 1));
        assertEquals(0, result.size());
    }

    // ========== Record Functions ==========

    @Test void keysNonEmpty() {
        HVal.HRecord input = rec("record", rec("x", 1, "y", 2, "z", 3));
        HVal.HList result = (HVal.HList) stdlib.call(0x0040, input);
        assertEquals(3, result.size());
        assertEquals(s("x"), result.get(0));
        assertEquals(s("y"), result.get(1));
        assertEquals(s("z"), result.get(2));
    }

    @Test void keysEmpty() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0040, rec("record", new HVal.HRecord()));
        assertEquals(0, result.size());
    }

    @Test void valuesNonEmpty() {
        HVal.HRecord input = rec("record", rec("a", 10, "b", 20));
        HVal.HList result = (HVal.HList) stdlib.call(0x0041, input);
        assertEquals(2, result.size());
        assertEquals(i(10), result.get(0));
        assertEquals(i(20), result.get(1));
    }

    @Test void valuesEmpty() {
        HVal.HList result = (HVal.HList) stdlib.call(0x0041, rec("record", new HVal.HRecord()));
        assertEquals(0, result.size());
    }

    @Test void mergeOverlappingKeys() {
        HVal.HRecord a = rec("x", 1, "y", 2);
        HVal.HRecord b = rec("y", 99, "z", 3);
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0042, rec("a", a, "b", b));
        assertEquals(i(1), result.get("x"));
        assertEquals(i(99), result.get("y")); // b overwrites a
        assertEquals(i(3), result.get("z"));
    }

    @Test void pickSubset() {
        HVal.HRecord r = rec("a", 1, "b", 2, "c", 3);
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0043,
                rec("record", r, "fields", listOf(s("a"), s("c"))));
        assertEquals(2, result.size());
        assertEquals(i(1), result.get("a"));
        assertEquals(i(3), result.get("c"));
    }

    @Test void pickNonExistentField() {
        HVal.HRecord r = rec("a", 1);
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0043,
                rec("record", r, "fields", listOf(s("a"), s("missing"))));
        assertEquals(1, result.size());
        assertEquals(i(1), result.get("a"));
    }

    @Test void omitFields() {
        HVal.HRecord r = rec("a", 1, "b", 2, "c", 3);
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0044,
                rec("record", r, "fields", listOf(s("b"))));
        assertEquals(2, result.size());
        assertEquals(i(1), result.get("a"));
        assertEquals(i(3), result.get("c"));
    }

    @Test void omitNonExistentField() {
        HVal.HRecord r = rec("a", 1, "b", 2);
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0044,
                rec("record", r, "fields", listOf(s("missing"))));
        assertEquals(2, result.size());
    }

    // ========== Date/Time Functions ==========

    @Test void parseDateYmdHms() {
        HVal.HRecord result = (HVal.HRecord) stdlib.call(0x0050,
                rec("value", "2024-03-15 10:30:00", "format", "%Y-%m-%d %H:%M:%S"));
        assertEquals(i(2024), result.get("year"));
        assertEquals(i(3), result.get("month"));
        assertEquals(i(15), result.get("day"));
        assertEquals(i(10), result.get("hour"));
        assertEquals(i(30), result.get("minute"));
        assertEquals(i(0), result.get("second"));
    }

    @Test void formatDateBasic() {
        HVal.HRecord date = new HVal.HRecord();
        date.set("year", i(2024));
        date.set("month", i(3));
        date.set("day", i(15));
        date.set("hour", i(10));
        date.set("minute", i(30));
        date.set("second", i(0));
        assertEquals(s("2024-03-15"), stdlib.call(0x0051, rec("date", date, "format", "%Y-%m-%d")));
    }

    @Test void dateDiffDays() {
        assertEquals(i(10), stdlib.call(0x0052,
                rec("from", "2024-01-01", "to", "2024-01-11", "unit", "days")));
    }

    @Test void dateDiffNegative() {
        assertEquals(i(-5), stdlib.call(0x0052,
                rec("from", "2024-01-10", "to", "2024-01-05", "unit", "days")));
    }

    @Test void dateDiffWithABaliases() {
        assertEquals(i(10), stdlib.call(0x0052,
                rec("a", "2024-01-01", "to", "2024-01-11", "from", "", "unit", "days")));
    }

    @Test void dateAddDays() {
        assertEquals(s("2024-01-11"), stdlib.call(0x0053,
                rec("date", "2024-01-01", "amount", 10, "unit", "days")));
    }

    @Test void dateAddDaysDatetime() {
        HVal result = stdlib.call(0x0053,
                rec("date", "2024-01-01T10:00:00Z", "amount", 2, "unit", "days"));
        assertEquals(s("2024-01-03T10:00:00Z"), result);
    }

    @Test void nowDate() {
        assertEquals(s("2024-06-15T12:30:00Z"), stdlib.call(0x0054, rec()));
    }

    // ========== Encoding Functions ==========

    @Test void base64EncodeDecodeRoundTrip() {
        HVal encoded = stdlib.call(0x0060, rec("value", "Hello World!"));
        assertEquals(s("SGVsbG8gV29ybGQh"), encoded);
        HVal decoded = stdlib.call(0x0061, rec("value", "SGVsbG8gV29ybGQh"));
        assertEquals(s("Hello World!"), decoded);
    }

    @Test void base64EmptyString() {
        assertEquals(s(""), stdlib.call(0x0060, rec("value", "")));
        assertEquals(s(""), stdlib.call(0x0061, rec("value", "")));
    }

    @Test void urlEncodeDecodeRoundTrip() {
        HVal encoded = stdlib.call(0x0062, rec("value", "Hello World!"));
        assertEquals(s("Hello%20World%21"), encoded);
        HVal decoded = stdlib.call(0x0063, rec("value", "Hello%20World%21"));
        assertEquals(s("Hello World!"), decoded);
    }

    @Test void urlEncodeSpecialChars() {
        HVal result = stdlib.call(0x0062, rec("value", "a=1&b=2"));
        String str = ((HVal.HString) result).value();
        assertTrue(str.contains("%3D") || str.contains("%26"),
                "Expected URL-encoded special chars, got: " + str);
    }

    @Test void jsonEncodeString() {
        assertEquals(s("\"hello\""), stdlib.call(0x0064, rec("value", s("hello"))));
    }

    @Test void jsonEncodeInt() {
        assertEquals(s("42"), stdlib.call(0x0064, rec("value", i(42))));
    }

    @Test void jsonEncodeFloat() {
        assertEquals(s("3.14"), stdlib.call(0x0064, rec("value", f(3.14))));
    }

    @Test void jsonEncodeBool() {
        assertEquals(s("true"), stdlib.call(0x0064, rec("value", HVal.HBoolean.TRUE)));
    }

    @Test void jsonEncodeNull() {
        assertEquals(s("null"), stdlib.call(0x0064, rec("value", HVal.HNothing.INSTANCE)));
    }

    @Test void jsonEncodeList() {
        assertEquals(s("[1,2,3]"), stdlib.call(0x0064, rec("value", listOf(i(1), i(2), i(3)))));
    }

    @Test void jsonEncodeRecord() {
        HVal.HRecord r = rec("a", 1);
        HVal result = stdlib.call(0x0064, rec("value", r));
        assertEquals(s("{\"a\":1}"), result);
    }

    @Test void jsonParseString() {
        HVal result = stdlib.call(0x0065, rec("value", "\"hello\""));
        assertEquals(s("hello"), result);
    }

    @Test void jsonParseNumber() {
        assertEquals(i(42), stdlib.call(0x0065, rec("value", "42")));
    }

    @Test void jsonParseFloat() {
        assertEquals(f(3.14), stdlib.call(0x0065, rec("value", "3.14")));
    }

    @Test void jsonParseBool() {
        assertEquals(HVal.HBoolean.TRUE, stdlib.call(0x0065, rec("value", "true")));
        assertEquals(HVal.HBoolean.FALSE, stdlib.call(0x0065, rec("value", "false")));
    }

    @Test void jsonParseNull() {
        assertTrue(stdlib.call(0x0065, rec("value", "null")).isNothing());
    }

    @Test void jsonParseNested() {
        HVal result = stdlib.call(0x0065, rec("value", "{\"items\":[1,2,3]}"));
        assertTrue(result instanceof HVal.HRecord);
        HVal.HRecord r = (HVal.HRecord) result;
        HVal items = r.get("items");
        assertTrue(items instanceof HVal.HList);
        assertEquals(3, ((HVal.HList) items).size());
    }

    // ========== Crypto Functions ==========

    @Test void sha256KnownVector() {
        HVal result = stdlib.call(0x0070, rec("value", "hello"));
        assertEquals(s("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"), result);
    }

    @Test void sha256EmptyString() {
        HVal result = stdlib.call(0x0070, rec("value", ""));
        assertEquals(s("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), result);
    }

    @Test void hmacSha256KnownVector() {
        HVal result = stdlib.call(0x0071, rec("value", "hello", "key", "secret"));
        String hash = ((HVal.HString) result).value();
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test void hmacSha256DifferentKey() {
        HVal r1 = stdlib.call(0x0071, rec("value", "hello", "key", "key1"));
        HVal r2 = stdlib.call(0x0071, rec("value", "hello", "key", "key2"));
        assertNotEquals(r1, r2);
    }

    @Test void uuidGenFormat() {
        HVal result = stdlib.call(0x0072, rec());
        String uuid = ((HVal.HString) result).value();
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "UUID format mismatch: " + uuid);
    }

    // ========== Conversion Functions ==========

    @Test void toStringFnInt() {
        assertEquals(s("42"), stdlib.call(0x0074, rec("value", i(42))));
    }

    @Test void toFloatFnFromInt() {
        assertEquals(f(5.0), stdlib.call(0x0075, rec("value", i(5))));
    }

    @Test void toFloatFnFromString() {
        assertEquals(f(3.14), stdlib.call(0x0075, rec("value", "3.14")));
    }

    @Test void toIntegerFnFromFloat() {
        assertEquals(i(3), stdlib.call(0x0076, rec("value", f(3.9))));
    }

    @Test void toIntegerFnFromString() {
        assertEquals(i(42), stdlib.call(0x0076, rec("value", "42")));
    }

    // ========== Fold Function ==========

    @Test void foldAdd() {
        HVal result = stdlib.call(0x0078,
                rec("list", listOf(i(1), i(2), i(3)), "initial", i(0), "fn", "add"));
        assertEquals(i(6), result);
    }

    @Test void foldMultiply() {
        HVal result = stdlib.call(0x0078,
                rec("list", listOf(i(2), i(3), i(4)), "initial", i(1), "fn", "multiply"));
        assertEquals(i(24), result);
    }

    @Test void foldUnknownFnThrows() {
        assertThrows(HelunaException.class,
                () -> stdlib.call(0x0078,
                        rec("list", listOf(i(1)), "initial", i(0), "fn", "unknown")));
    }

    // ========== Unknown function throws ==========

    @Test void unknownFunctionThrows() {
        assertThrows(HelunaException.class, () -> stdlib.call(0xFFFF, rec()));
    }
}
