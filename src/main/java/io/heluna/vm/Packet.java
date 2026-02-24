package io.heluna.vm;

import java.util.ArrayList;
import java.util.List;

public class Packet {

    // Header
    public int magic;
    public int formatVersion;
    public int minSpecVersion;
    public int totalSize;
    public int sectionCount;

    // Contract
    public String contractName;
    public int scratchpadSize;
    public int inputFieldCount;
    public int outputFieldCount;
    public int tagCount;
    public int sanitizerCount;
    public int ruleCount;

    public final List<TagDef> tagDefs = new ArrayList<>();
    public final List<FieldDef> inputFields = new ArrayList<>();
    public final List<FieldDef> outputFields = new ArrayList<>();
    public final List<SanitizerDef> sanitizers = new ArrayList<>();
    public final List<Rule> rules = new ArrayList<>();

    // Constants
    public final List<HVal> constants = new ArrayList<>();

    // Stdlib deps
    public final List<Integer> stdlibDeps = new ArrayList<>();

    // Bytecode
    public int[][] instructions; // [n][4]: opcode, flags, dest, op1, op2 (but stored as int[5])

    // Tests (optional)
    public final List<TestCase> testCases = new ArrayList<>();

    // --- Nested data classes ---

    public static class TagDef {
        public final int bitIndex;
        public final String name;
        public final String description;

        public TagDef(int bitIndex, String name, String description) {
            this.bitIndex = bitIndex;
            this.name = name;
            this.description = description;
        }
    }

    public static class FieldDef {
        public final String name;
        public final byte typeId;
        public final long tagBits;
        public final int scratchpadOffset;

        public FieldDef(String name, byte typeId, long tagBits, int scratchpadOffset) {
            this.name = name;
            this.typeId = typeId;
            this.tagBits = tagBits;
            this.scratchpadOffset = scratchpadOffset;
        }
    }

    public static class SanitizerDef {
        public final String name;
        public final int stdlibFuncId;
        public final long stripsTags;

        public SanitizerDef(String name, int stdlibFuncId, long stripsTags) {
            this.name = name;
            this.stdlibFuncId = stdlibFuncId;
            this.stripsTags = stripsTags;
        }
    }

    public static class Rule {
        public static final int FORBID_FIELD = 0x01;
        public static final int FORBID_TAGGED = 0x02;
        public static final int REQUIRE = 0x03;
        public static final int MATCH = 0x04;

        public final int type;
        public final long tagBits;         // for FORBID_TAGGED
        public final String fieldRef;      // for FORBID_FIELD
        public final String rejectMessage; // for REQUIRE/MATCH
        public final byte[] rawData;       // raw rule data for REQUIRE/MATCH

        public Rule(int type, long tagBits, String fieldRef, String rejectMessage, byte[] rawData) {
            this.type = type;
            this.tagBits = tagBits;
            this.fieldRef = fieldRef;
            this.rejectMessage = rejectMessage;
            this.rawData = rawData;
        }
    }

    public static class TestCase {
        public final String name;
        public final String inputJson;
        public final String outputJson;

        public TestCase(String name, String inputJson, String outputJson) {
            this.name = name;
            this.inputJson = inputJson;
            this.outputJson = outputJson;
        }
    }
}
