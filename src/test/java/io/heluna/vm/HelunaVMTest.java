package io.heluna.vm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

class HelunaVMTest {

    private static Packet pkt;

    @BeforeAll
    static void loadPacket() throws IOException {
        try (InputStream is = HelunaVMTest.class.getResourceAsStream("/vm-comprehensive.hlna")) {
            assertNotNull(is, "vm-comprehensive.hlna not found on classpath");
            pkt = HelunaVM.load(is.readAllBytes());
        }
    }

    @Test
    void packetMagicMatchesSpec() {
        assertEquals(0x484C4E41, HelunaVM.PACKET_MAGIC);
    }

    @Test
    void formatVersionIsOne() {
        assertEquals(1, HelunaVM.FORMAT_VERSION);
    }

    // =========================================================
    // Test Case 1: "comprehensive"
    // =========================================================

    @Test
    void testComprehensive() {
        String inputJson = "{"
                + "\"text\": \"  Hello World  \","
                + "\"word\": \"hello\","
                + "\"number\": 17,"
                + "\"decimal\": 3.14,"
                + "\"flag\": true,"
                + "\"optional-text\": \"present\","
                + "\"optional-number\": 42,"
                + "\"items\": [3, 1, 4, 1, 5, 9, 2, 6],"
                + "\"names\": [\"banana\", \"apple\", \"cherry\"],"
                + "\"nested-numbers\": [[1, 2], [3, 4], [5]],"
                + "\"person\": {\"name\": \"Alice\", \"age\": 30},"
                + "\"people\": ["
                + "  {\"name\": \"Charlie\", \"age\": 25},"
                + "  {\"name\": \"Alice\", \"age\": 30},"
                + "  {\"name\": \"Bob\", \"age\": 20}"
                + "],"
                + "\"encode-text\": \"Hello World!\","
                + "\"secret-value\": \"secret123\","
                + "\"personal-name\": \"John Doe\","
                + "\"negative\": -7,"
                + "\"zero\": 0,"
                + "\"shape-kind\": \"circle\","
                + "\"dimension-a\": 5.0,"
                + "\"dimension-b\": 3.0"
                + "}";

        HVal input = StdLib.parseJsonValue(inputJson, new int[]{0});
        HVal.HRecord output = HelunaVM.execute(pkt, (HVal.HRecord) input, "2024-01-15T10:30:00Z");

        // Arithmetic
        assertEq(22L, output, "add-int");
        assertEq(12L, output, "sub-int");
        assertEq(51L, output, "mul-int");
        assertEq(3L, output, "div-int");
        assertEq(2L, output, "mod-int");
        assertEq(-17L, output, "neg-int");
        assertEqFloat(5.0, output, "add-float");
        assertEqFloat(6.28, output, "mul-float");

        // Comparison
        assertEq(true, output, "cmp-eq");
        assertEq(true, output, "cmp-neq");
        assertEq(true, output, "cmp-lt");
        assertEq(true, output, "cmp-gt");
        assertEq(true, output, "cmp-lte");
        assertEq(true, output, "cmp-gte");

        // Boolean
        assertEq(true, output, "bool-and");
        assertEq(true, output, "bool-or");
        assertEq(true, output, "bool-not");
        assertEq(true, output, "bool-compound");

        // String
        assertEq("hello world", output, "str-concat");
        assertEq("hello world", output, "str-pipeline");

        // Conditionals
        assertEq("big", output, "if-simple");
        assertEq("positive", output, "if-chained");

        // Match
        assertEq("greeting", output, "match-literal");
        assertEq("medium", output, "match-range");
        assertEq("above-ten", output, "match-guard");
        assertEq("got: present", output, "match-nothing");
        assertEq("anything", output, "match-wildcard");

        // Maybe/Nothing
        assertEq("present", output, "or-else-text");
        assertEq(false, output, "is-nothing-result");

        // Type testing
        assertEq(true, output, "type-is-string");
        assertEq(true, output, "type-is-integer");
        assertEq(true, output, "type-is-float");
        assertEq(true, output, "type-is-boolean");
        assertEq(true, output, "type-is-list");
        assertEq(true, output, "type-is-record");

        // Let
        assertEq(18L, output, "let-result");

        // List ops
        assertEq(8L, output, "list-length");
        assertListInts(new long[]{4, 5, 9, 6}, output, "filtered-items");
        assertListInts(new long[]{30, 10, 40, 10, 50, 90, 20, 60}, output, "mapped-items");
        assertEq(31L, output, "fold-sum");
        assertEq(6480L, output, "fold-product");

        // Record access
        assertEq("Alice", output, "record-name");
        assertEq(30L, output, "record-age");

        // Stdlib string
        assertEq("HELLO", output, "upper-result");
        assertEq("world", output, "lower-result");
        assertEq("Hello World", output, "trim-result");
        assertEq("Hello World  ", output, "trim-start-result");
        assertEq("  Hello World", output, "trim-end-result");
        assertEq("ell", output, "substring-result");
        assertEq("herro", output, "replace-result");
        assertListStrings(new String[]{"a", "b", "c"}, output, "split-result");
        assertEq("banana, apple, cherry", output, "join-result");
        assertEq(true, output, "starts-with-result");
        assertEq(true, output, "ends-with-result");
        assertEq(true, output, "contains-result");
        assertEq(5L, output, "str-length-result");
        assertEq("00042", output, "pad-left-result");
        assertEq("hi...", output, "pad-right-result");
        assertEq("abcNUMdef", output, "regex-replace-result");

        // Stdlib numeric
        assertEq(7L, output, "abs-result");
        assertEq(4L, output, "ceil-result");
        assertEq(3L, output, "floor-result");
        assertEq(4L, output, "round-result");
        assertEq(-7L, output, "min-result");
        assertEq(17L, output, "max-result");
        assertEq(10L, output, "clamp-result");

        // Stdlib list
        assertListInts(new long[]{1, 1, 2, 3, 4, 5, 6, 9}, output, "sort-result");
        assertEq("Bob", output, "sort-by-youngest");
        assertListInts(new long[]{3, 2, 1}, output, "reverse-result");
        assertListInts(new long[]{3, 1, 4, 5, 9, 2, 6}, output, "unique-result");
        assertListInts(new long[]{1, 2, 3, 4, 5}, output, "flatten-result");
        assertListInts(new long[]{1, 2, 3, 4, 5}, output, "range-result");
        assertListInts(new long[]{3, 1, 4}, output, "slice-result");

        // Stdlib record
        assertEq(10L, output, "merge-x");
        assertEq("Alice", output, "pick-name");
        assertEq(1L, output, "omit-x");

        // Encoding
        assertEq("SGVsbG8gV29ybGQh", output, "base64-encoded");
        assertEq("Hello World!", output, "base64-decoded");
        assertEq("Hello%20World%21", output, "url-encoded");
        assertEq("Hello World!", output, "url-decoded");
        assertEq("true", output, "json-encoded");

        // Crypto
        assertEq("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                output, "sha256-result");
        assertEq("88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b",
                output, "hmac-result");

        // Conversion
        assertEq("17", output, "to-string-result");
        assertEq(3L, output, "to-int-result");

        // Date
        assertEq("2024-01-25", output, "date-add-result");

        // Shape
        assertEqFloat(78.5, output, "match-shape");

        // Security/sanitizer
        assertEq("fcf730b6d95236ecd3c9fc2d92d7b6b2bb061514961aec041d6c7a7192f592e4",
                output, "hashed-secret");
        assertEq("6cea57c2fb6cbc2a40411135005760f241fffc3e5e67ab99882726431037f908",
                output, "hashed-name");
    }

    // =========================================================
    // Test Case 2: "nothing-handling"
    // =========================================================

    @Test
    void testNothingHandling() {
        String inputJson = "{"
                + "\"text\": \"  Hello World  \","
                + "\"word\": \"hello\","
                + "\"number\": 17,"
                + "\"decimal\": 3.14,"
                + "\"flag\": true,"
                + "\"optional-text\": null,"
                + "\"optional-number\": null,"
                + "\"items\": [3, 1, 4, 1, 5, 9, 2, 6],"
                + "\"names\": [\"banana\", \"apple\", \"cherry\"],"
                + "\"nested-numbers\": [[1, 2], [3, 4], [5]],"
                + "\"person\": {\"name\": \"Alice\", \"age\": 30},"
                + "\"people\": ["
                + "  {\"name\": \"Charlie\", \"age\": 25},"
                + "  {\"name\": \"Alice\", \"age\": 30},"
                + "  {\"name\": \"Bob\", \"age\": 20}"
                + "],"
                + "\"encode-text\": \"Hello World!\","
                + "\"secret-value\": \"secret123\","
                + "\"personal-name\": \"John Doe\","
                + "\"negative\": -7,"
                + "\"zero\": 0,"
                + "\"shape-kind\": \"rectangle\","
                + "\"dimension-a\": 4.0,"
                + "\"dimension-b\": 3.0"
                + "}";

        HVal input = StdLib.parseJsonValue(inputJson, new int[]{0});
        HVal.HRecord output = HelunaVM.execute(pkt, (HVal.HRecord) input, "2024-01-15T10:30:00Z");

        // Changed outputs for nothing-handling
        assertEq("absent", output, "match-nothing");
        assertEq("fallback", output, "or-else-text");
        assertEq(true, output, "is-nothing-result");
        assertEqFloat(12.0, output, "match-shape");

        // Everything else same as comprehensive
        assertEq(22L, output, "add-int");
        assertEq("big", output, "if-simple");
        assertEq("greeting", output, "match-literal");
        assertEq(31L, output, "fold-sum");
        assertEq("Bob", output, "sort-by-youngest");
    }

    // =========================================================
    // Test Case 3: "empty-lists"
    // =========================================================

    @Test
    void testEmptyLists() {
        String inputJson = "{"
                + "\"text\": \"  Hello World  \","
                + "\"word\": \"hello\","
                + "\"number\": 17,"
                + "\"decimal\": 3.14,"
                + "\"flag\": true,"
                + "\"optional-text\": \"present\","
                + "\"optional-number\": 42,"
                + "\"items\": [],"
                + "\"names\": [],"
                + "\"nested-numbers\": [],"
                + "\"person\": {\"name\": \"Alice\", \"age\": 30},"
                + "\"people\": [],"
                + "\"encode-text\": \"Hello World!\","
                + "\"secret-value\": \"secret123\","
                + "\"personal-name\": \"John Doe\","
                + "\"negative\": -7,"
                + "\"zero\": 0,"
                + "\"shape-kind\": \"square\","
                + "\"dimension-a\": 5.0,"
                + "\"dimension-b\": 5.0"
                + "}";

        HVal input = StdLib.parseJsonValue(inputJson, new int[]{0});
        HVal.HRecord output = HelunaVM.execute(pkt, (HVal.HRecord) input, "2024-01-15T10:30:00Z");

        // Empty list outputs
        assertEq(0L, output, "list-length");
        assertListInts(new long[]{}, output, "filtered-items");
        assertListInts(new long[]{}, output, "mapped-items");
        assertEq(0L, output, "fold-sum");
        assertEq(1L, output, "fold-product");
        assertListInts(new long[]{}, output, "sort-result");
        assertEq("none", output, "sort-by-youngest");
        assertListInts(new long[]{3, 2, 1}, output, "reverse-result");
        assertListInts(new long[]{}, output, "unique-result");
        assertListInts(new long[]{}, output, "flatten-result");
        assertListInts(new long[]{1, 2, 3, 4, 5}, output, "range-result");
        assertListInts(new long[]{}, output, "slice-result");
        assertEq("", output, "join-result");
        assertEqFloat(0.0, output, "match-shape");

        // Non-list outputs same as comprehensive
        assertEq(22L, output, "add-int");
        assertEq("big", output, "if-simple");
        assertEq("got: present", output, "match-nothing");
        assertEq("present", output, "or-else-text");
        assertEq(false, output, "is-nothing-result");
    }

    // =========================================================
    // JSON round-trip test
    // =========================================================

    @Test
    void testJsonRoundTrip() {
        String inputJson = "{\"text\":\"  Hello World  \",\"word\":\"hello\",\"number\":17,"
                + "\"decimal\":3.14,\"flag\":true,\"optional-text\":\"present\","
                + "\"optional-number\":42,\"items\":[3,1,4,1,5,9,2,6],"
                + "\"names\":[\"banana\",\"apple\",\"cherry\"],"
                + "\"nested-numbers\":[[1,2],[3,4],[5]],"
                + "\"person\":{\"name\":\"Alice\",\"age\":30},"
                + "\"people\":[{\"name\":\"Charlie\",\"age\":25},"
                + "{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":20}],"
                + "\"encode-text\":\"Hello World!\",\"secret-value\":\"secret123\","
                + "\"personal-name\":\"John Doe\",\"negative\":-7,\"zero\":0,"
                + "\"shape-kind\":\"circle\",\"dimension-a\":5.0,\"dimension-b\":3.0}";

        String outputJson = HelunaVM.executeJson(pkt, inputJson, "2024-01-15T10:30:00Z");

        // Parse it back and verify key fields
        HVal parsed = StdLib.parseJsonValue(outputJson, new int[]{0});
        assertTrue(parsed instanceof HVal.HRecord, "Output JSON should parse to a record");
        HVal.HRecord output = (HVal.HRecord) parsed;

        assertEq(22L, output, "add-int");
        assertEq("hello world", output, "str-concat");
        assertEq(true, output, "cmp-eq");
        assertEq("HELLO", output, "upper-result");
    }

    // =========================================================
    // Forbid-tagged validation test
    // =========================================================

    @Test
    void testForbidTaggedValidation() {
        // Create a minimal packet with a forbid-tagged rule
        Packet miniPkt = new Packet();
        miniPkt.magic = HelunaVM.PACKET_MAGIC;
        miniPkt.formatVersion = 1;
        miniPkt.minSpecVersion = 1;
        miniPkt.scratchpadSize = 5;
        miniPkt.inputFieldCount = 1;
        miniPkt.outputFieldCount = 1;

        // Input field at slot 0, tagged with bit 0 (pii)
        miniPkt.inputFields.add(new Packet.FieldDef("name", HVal.TYPE_STRING, 0x01, 0));
        // Output field at slot 1
        miniPkt.outputFields.add(new Packet.FieldDef("result", HVal.TYPE_STRING, 0, 1));

        // Forbid tag bit 0 in output
        miniPkt.rules.add(new Packet.Rule(Packet.Rule.FORBID_TAGGED, 0x01, null, null, null));

        // Bytecode: COPY slot 0 -> slot 2 (output record slot = 1 input + 1 output = 2)
        // But for this test, we just need to verify that tagged output is rejected.
        // Build a minimal bytecode that creates output record and copies tagged value
        miniPkt.constants.add(new HVal.HString("result")); // constant 0
        miniPkt.instructions = new int[][]{
                {0x60, 0x06, 2, 0, 0},     // RECORD_NEW dest=2
                {0x01, 0x01, 3, 0, 0},     // LOAD_CONST dest=3, op1=0 ("result")
                {0x04, 0x00, 4, 0, 0},     // COPY dest=4, from slot 0 (input)
                {0x61, 0x06, 2, 3, 4},     // RECORD_SET dest=2, key=slot3, value=slot4
        };

        HVal.HRecord input = new HVal.HRecord();
        input.set("name", new HVal.HString("John Doe"));

        // This should throw because the output record carries tag bit 0
        assertThrows(HelunaException.class, () -> {
            HelunaVM.execute(miniPkt, input, "2024-01-01T00:00:00Z");
        });
    }

    // =========================================================
    // JSON Parsing Edge Cases
    // =========================================================

    @Test
    void jsonParseEmptyObject() {
        HVal result = StdLib.parseJsonValue("{}", new int[]{0});
        assertTrue(result instanceof HVal.HRecord);
        assertEquals(0, ((HVal.HRecord) result).size());
    }

    @Test
    void jsonParseEmptyArray() {
        HVal result = StdLib.parseJsonValue("[]", new int[]{0});
        assertTrue(result instanceof HVal.HList);
        assertEquals(0, ((HVal.HList) result).size());
    }

    @Test
    void jsonParseNestedStructure() {
        String json = "{\"a\":{\"b\":{\"c\":[1,2,{\"d\":true}]}}}";
        HVal result = StdLib.parseJsonValue(json, new int[]{0});
        assertTrue(result instanceof HVal.HRecord);
        HVal.HRecord root = (HVal.HRecord) result;
        HVal.HRecord a = (HVal.HRecord) root.get("a");
        HVal.HRecord b = (HVal.HRecord) a.get("b");
        HVal.HList c = (HVal.HList) b.get("c");
        assertEquals(3, c.size());
        assertEquals(new HVal.HInteger(1), c.get(0));
        HVal.HRecord inner = (HVal.HRecord) c.get(2);
        assertEquals(HVal.HBoolean.TRUE, inner.get("d"));
    }

    @Test
    void jsonParseEscapedStrings() {
        // Test \" \\ \n \t and unicode escapes
        String json = "{\"a\":\"hello\\\"world\",\"b\":\"line1\\nline2\",\"c\":\"tab\\there\","
                + "\"d\":\"" + "\\" + "u0041\"}";
        HVal result = StdLib.parseJsonValue(json, new int[]{0});
        HVal.HRecord rec = (HVal.HRecord) result;
        assertEquals("hello\"world", ((HVal.HString) rec.get("a")).value());
        assertEquals("line1\nline2", ((HVal.HString) rec.get("b")).value());
        assertEquals("tab\there", ((HVal.HString) rec.get("c")).value());
        assertEquals("A", ((HVal.HString) rec.get("d")).value());
    }

    @Test
    void jsonParseScientificNotation() {
        HVal result = StdLib.parseJsonValue("1.5e3", new int[]{0});
        assertTrue(result instanceof HVal.HFloat);
        assertEquals(1500.0, ((HVal.HFloat) result).value(), 1e-9);
    }

    @Test
    void jsonParseNegativeNumbers() {
        HVal intResult = StdLib.parseJsonValue("-42", new int[]{0});
        assertEquals(new HVal.HInteger(-42), intResult);

        HVal floatResult = StdLib.parseJsonValue("-3.14", new int[]{0});
        assertTrue(floatResult instanceof HVal.HFloat);
        assertEquals(-3.14, ((HVal.HFloat) floatResult).value(), 1e-9);
    }

    // =========================================================
    // JSON Serialization Edge Cases
    // =========================================================

    @Test
    void jsonEncodeSpecialChars() {
        String json = StdLib.toJson(new HVal.HString("hello\"world\nnewline\ttab"));
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
    }

    @Test
    void jsonEncodeAllTypesRoundTrip() {
        // Test round-trip for each type
        HVal[] values = {
                new HVal.HString("test"),
                new HVal.HInteger(42),
                new HVal.HFloat(3.14),
                HVal.HBoolean.TRUE,
                HVal.HBoolean.FALSE,
                HVal.HNothing.INSTANCE,
        };
        for (HVal v : values) {
            String json = StdLib.toJson(v);
            HVal parsed = StdLib.parseJsonValue(json, new int[]{0});
            if (v instanceof HVal.HNothing) {
                assertTrue(parsed.isNothing(), "Nothing round-trip failed");
            } else {
                assertEquals(v, parsed, "Round-trip failed for: " + v);
            }
        }
    }

    // =========================================================
    // Execution Edge Cases
    // =========================================================

    @Test
    void executeWithExtraInputFields() {
        // Extra fields in input JSON that aren't in the contract should be ignored
        String inputJson = "{"
                + "\"text\": \"  Hello World  \","
                + "\"word\": \"hello\","
                + "\"number\": 17,"
                + "\"decimal\": 3.14,"
                + "\"flag\": true,"
                + "\"optional-text\": \"present\","
                + "\"optional-number\": 42,"
                + "\"items\": [3, 1, 4, 1, 5, 9, 2, 6],"
                + "\"names\": [\"banana\", \"apple\", \"cherry\"],"
                + "\"nested-numbers\": [[1, 2], [3, 4], [5]],"
                + "\"person\": {\"name\": \"Alice\", \"age\": 30},"
                + "\"people\": ["
                + "  {\"name\": \"Charlie\", \"age\": 25},"
                + "  {\"name\": \"Alice\", \"age\": 30},"
                + "  {\"name\": \"Bob\", \"age\": 20}"
                + "],"
                + "\"encode-text\": \"Hello World!\","
                + "\"secret-value\": \"secret123\","
                + "\"personal-name\": \"John Doe\","
                + "\"negative\": -7,"
                + "\"zero\": 0,"
                + "\"shape-kind\": \"circle\","
                + "\"dimension-a\": 5.0,"
                + "\"dimension-b\": 3.0,"
                + "\"EXTRA_FIELD\": \"should be ignored\","
                + "\"another-extra\": 9999"
                + "}";

        HVal input = StdLib.parseJsonValue(inputJson, new int[]{0});
        HVal.HRecord output = HelunaVM.execute(pkt, (HVal.HRecord) input, "2024-01-15T10:30:00Z");

        // Should produce the same results as the comprehensive test
        assertEq(22L, output, "add-int");
        assertEq("hello world", output, "str-concat");
    }

    @Test
    void executeJsonRoundTripPreservesTypes() {
        String inputJson = "{\"text\":\"  Hello World  \",\"word\":\"hello\",\"number\":17,"
                + "\"decimal\":3.14,\"flag\":true,\"optional-text\":\"present\","
                + "\"optional-number\":42,\"items\":[3,1,4,1,5,9,2,6],"
                + "\"names\":[\"banana\",\"apple\",\"cherry\"],"
                + "\"nested-numbers\":[[1,2],[3,4],[5]],"
                + "\"person\":{\"name\":\"Alice\",\"age\":30},"
                + "\"people\":[{\"name\":\"Charlie\",\"age\":25},"
                + "{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":20}],"
                + "\"encode-text\":\"Hello World!\",\"secret-value\":\"secret123\","
                + "\"personal-name\":\"John Doe\",\"negative\":-7,\"zero\":0,"
                + "\"shape-kind\":\"circle\",\"dimension-a\":5.0,\"dimension-b\":3.0}";

        String outputJson = HelunaVM.executeJson(pkt, inputJson, "2024-01-15T10:30:00Z");
        HVal.HRecord output = (HVal.HRecord) StdLib.parseJsonValue(outputJson, new int[]{0});

        // Integers stay integers
        HVal addInt = output.get("add-int");
        assertTrue(addInt instanceof HVal.HInteger, "Expected integer, got " + addInt.getClass().getSimpleName());
        assertEquals(22L, ((HVal.HInteger) addInt).value());

        // Floats stay floats (or display as floats)
        HVal mulFloat = output.get("mul-float");
        assertTrue(mulFloat instanceof HVal.HFloat || mulFloat instanceof HVal.HInteger,
                "Expected numeric, got " + mulFloat.getClass().getSimpleName());
    }

    // =========================================================
    // Helper methods
    // =========================================================

    private void assertEq(long expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        assertTrue(v instanceof HVal.HInteger,
                field + ": expected integer, got " + Executor.typeName(v) + " = " + v);
        assertEquals(expected, ((HVal.HInteger) v).value(), field);
    }

    private void assertEq(String expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        assertTrue(v instanceof HVal.HString,
                field + ": expected string, got " + Executor.typeName(v) + " = " + v);
        assertEquals(expected, ((HVal.HString) v).value(), field);
    }

    private void assertEq(boolean expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        assertTrue(v instanceof HVal.HBoolean,
                field + ": expected boolean, got " + Executor.typeName(v) + " = " + v);
        assertEquals(expected, ((HVal.HBoolean) v).value(), field);
    }

    private void assertEqFloat(double expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        if (v instanceof HVal.HFloat) {
            assertEquals(expected, ((HVal.HFloat) v).value(), 1e-9, field);
        } else if (v instanceof HVal.HInteger) {
            assertEquals(expected, (double) ((HVal.HInteger) v).value(), 1e-9, field);
        } else {
            fail(field + ": expected float/integer, got " + Executor.typeName(v) + " = " + v);
        }
    }

    private void assertListInts(long[] expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        assertTrue(v instanceof HVal.HList,
                field + ": expected list, got " + Executor.typeName(v) + " = " + v);
        HVal.HList list = (HVal.HList) v;
        assertEquals(expected.length, list.size(),
                field + ": size mismatch, got " + list);
        for (int i = 0; i < expected.length; i++) {
            HVal elem = list.elements().get(i);
            assertTrue(elem instanceof HVal.HInteger,
                    field + "[" + i + "]: expected integer, got " + Executor.typeName(elem));
            assertEquals(expected[i], ((HVal.HInteger) elem).value(),
                    field + "[" + i + "]");
        }
    }

    private void assertListStrings(String[] expected, HVal.HRecord output, String field) {
        HVal v = output.get(field);
        assertTrue(v instanceof HVal.HList,
                field + ": expected list, got " + Executor.typeName(v) + " = " + v);
        HVal.HList list = (HVal.HList) v;
        assertEquals(expected.length, list.size(),
                field + ": size mismatch, got " + list);
        for (int i = 0; i < expected.length; i++) {
            HVal elem = list.elements().get(i);
            assertTrue(elem instanceof HVal.HString,
                    field + "[" + i + "]: expected string, got " + Executor.typeName(elem));
            assertEquals(expected[i], ((HVal.HString) elem).value(),
                    field + "[" + i + "]");
        }
    }
}
