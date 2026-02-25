package io.heluna.vm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PacketLoader {

    private static final int HEADER_SIZE = 88;
    private static final int SECTION_ENTRY_SIZE = 10;

    private static final int SECTION_CONTRACT = 0x0001;
    private static final int SECTION_CONSTANTS = 0x0002;
    private static final int SECTION_STDLIB_DEPS = 0x0003;
    private static final int SECTION_BYTECODE = 0x0004;
    private static final int SECTION_TESTS = 0x0101;

    public static Packet load(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new HelunaException("Packet too small: " + data.length + " bytes");
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        Packet pkt = new Packet();

        // --- Header ---
        pkt.magic = buf.getInt(0);
        if (pkt.magic != HelunaVM.PACKET_MAGIC) {
            throw new HelunaException(String.format("Bad magic: 0x%08X (expected 0x%08X)",
                    pkt.magic, HelunaVM.PACKET_MAGIC));
        }
        pkt.formatVersion = Short.toUnsignedInt(buf.getShort(4));
        pkt.minSpecVersion = Short.toUnsignedInt(buf.getShort(6));
        pkt.totalSize = buf.getInt(8);
        pkt.sectionCount = Short.toUnsignedInt(buf.getShort(12));

        // --- Section Directory ---
        Map<Integer, int[]> sections = new HashMap<>(); // type -> [offset, length]
        int dirStart = HEADER_SIZE;
        for (int i = 0; i < pkt.sectionCount; i++) {
            int entryOff = dirStart + i * SECTION_ENTRY_SIZE;
            int stype = Short.toUnsignedInt(buf.getShort(entryOff));
            int soff = buf.getInt(entryOff + 2);
            int slen = buf.getInt(entryOff + 6);
            sections.put(stype, new int[]{soff, slen});
        }

        // Verify required sections
        for (int req : new int[]{SECTION_CONTRACT, SECTION_CONSTANTS, SECTION_STDLIB_DEPS, SECTION_BYTECODE}) {
            if (!sections.containsKey(req)) {
                throw new HelunaException(String.format("Missing required section 0x%04X", req));
            }
        }

        // --- CONTRACT ---
        parseContract(buf, sections.get(SECTION_CONTRACT), pkt);

        // --- CONSTANTS ---
        parseConstants(buf, sections.get(SECTION_CONSTANTS), pkt);

        // --- STDLIB_DEPS ---
        parseStdlibDeps(buf, sections.get(SECTION_STDLIB_DEPS), pkt);

        // --- BYTECODE ---
        parseBytecode(buf, sections.get(SECTION_BYTECODE), pkt);

        // --- TESTS (optional) ---
        if (sections.containsKey(SECTION_TESTS)) {
            parseTests(buf, sections.get(SECTION_TESTS), pkt);
        }

        return pkt;
    }

    private static void parseContract(ByteBuffer buf, int[] section, Packet pkt) {
        int pos = section[0];
        int end = section[0] + section[1];

        // Contract header
        int nameLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.contractName = readString(buf, pos, nameLen); pos += nameLen;
        pkt.scratchpadSize = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.inputFieldCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.outputFieldCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.tagCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.sanitizerCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        pkt.ruleCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;

        // Tag definitions
        for (int i = 0; i < pkt.tagCount; i++) {
            int bitIndex = Byte.toUnsignedInt(buf.get(pos)); pos += 1;
            int tnameLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
            String tname = readString(buf, pos, tnameLen); pos += tnameLen;
            int descLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
            String desc = descLen > 0 ? readString(buf, pos, descLen) : "";
            pos += descLen;
            pkt.tagDefs.add(new Packet.TagDef(bitIndex, tname, desc));
        }

        // Input fields
        for (int i = 0; i < pkt.inputFieldCount; i++) {
            pos = parseField(buf, pos, pkt.inputFields);
        }

        // Output fields
        for (int i = 0; i < pkt.outputFieldCount; i++) {
            pos = parseField(buf, pos, pkt.outputFields);
        }

        // Sanitizer declarations
        for (int i = 0; i < pkt.sanitizerCount; i++) {
            int snameLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
            String sname = readString(buf, pos, snameLen); pos += snameLen;
            int funcId = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
            long strips = buf.getLong(pos); pos += 8;
            pkt.sanitizers.add(new Packet.SanitizerDef(sname, funcId, strips));
        }

        // Validation rules
        for (int i = 0; i < pkt.ruleCount; i++) {
            pos = parseRule(buf, pos, end, pkt);
        }
    }

    private static int parseField(ByteBuffer buf, int pos,
                                   java.util.List<Packet.FieldDef> fields) {
        int nameLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        String name = readString(buf, pos, nameLen); pos += nameLen;
        byte typeId = buf.get(pos); pos += 1;
        pos = skipTypeDetail(buf, pos, typeId);
        long tagBits = buf.getLong(pos); pos += 8;
        int scratchpadOffset = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        fields.add(new Packet.FieldDef(name, typeId, tagBits, scratchpadOffset));
        return pos;
    }

    private static int skipTypeDetail(ByteBuffer buf, int pos, byte typeId) {
        switch (typeId) {
            case HVal.TYPE_STRING:
            case HVal.TYPE_INTEGER:
            case HVal.TYPE_FLOAT:
            case HVal.TYPE_BOOLEAN:
            case HVal.TYPE_NOTHING:
                return pos; // no detail
            case HVal.TYPE_MAYBE: {
                byte inner = buf.get(pos); pos += 1;
                return skipTypeDetail(buf, pos, inner);
            }
            case HVal.TYPE_LIST: {
                byte elem = buf.get(pos); pos += 1;
                return skipTypeDetail(buf, pos, elem);
            }
            case HVal.TYPE_RECORD: {
                int fieldCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
                for (int i = 0; i < fieldCount; i++) {
                    int fnLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
                    pos += fnLen; // skip name
                    byte ft = buf.get(pos); pos += 1;
                    pos = skipTypeDetail(buf, pos, ft);
                    pos += 8; // tag_bits
                    pos += 2; // scratchpad_offset
                }
                return pos;
            }
            default:
                return pos;
        }
    }

    private static int parseRule(ByteBuffer buf, int pos, int sectionEnd, Packet pkt) {
        int ruleType = Byte.toUnsignedInt(buf.get(pos)); pos += 1;
        switch (ruleType) {
            case Packet.Rule.FORBID_TAGGED: {
                // tag_bits(8) + scope(1)
                long tagBits = buf.getLong(pos); pos += 8;
                int scope = Byte.toUnsignedInt(buf.get(pos)); pos += 1;
                pkt.rules.add(new Packet.Rule(ruleType, tagBits, null, null, null));
                return pos;
            }
            case Packet.Rule.FORBID_FIELD: {
                // scope(1) + field_index(2)
                int scope = Byte.toUnsignedInt(buf.get(pos)); pos += 1;
                int fieldIdx = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
                pkt.rules.add(new Packet.Rule(ruleType, 0, "field:" + fieldIdx, null, null));
                return pos;
            }
            case Packet.Rule.REQUIRE:
            case Packet.Rule.MATCH: {
                // field_name_length(2) + field_name(var) + msg_length(2) + msg(var)
                int fnLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
                String fieldName = readString(buf, pos, fnLen); pos += fnLen;
                int msgLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
                String msg = readString(buf, pos, msgLen); pos += msgLen;
                pkt.rules.add(new Packet.Rule(ruleType, 0, fieldName, msg, null));
                return pos;
            }
            default:
                throw new HelunaException("Unknown rule type: " + ruleType);
        }
    }

    private static void parseConstants(ByteBuffer buf, int[] section, Packet pkt) {
        int pos = section[0];
        int end = section[0] + section[1];

        while (pos < end) {
            int typeId = Byte.toUnsignedInt(buf.get(pos)); pos += 1;
            int dataLen = buf.getInt(pos); pos += 4;

            switch (typeId) {
                case HVal.TYPE_STRING: {
                    String s = readString(buf, pos, dataLen);
                    pkt.constants.add(new HVal.HString(s));
                    break;
                }
                case HVal.TYPE_INTEGER: {
                    long v = buf.getLong(pos);
                    pkt.constants.add(HVal.HInteger.of(v));
                    break;
                }
                case HVal.TYPE_FLOAT: {
                    double v = buf.getDouble(pos);
                    pkt.constants.add(new HVal.HFloat(v));
                    break;
                }
                case HVal.TYPE_BOOLEAN: {
                    boolean v = buf.get(pos) != 0;
                    pkt.constants.add(HVal.HBoolean.of(v));
                    break;
                }
                case HVal.TYPE_NOTHING: {
                    pkt.constants.add(HVal.HNothing.INSTANCE);
                    break;
                }
                default:
                    throw new HelunaException("Unknown constant type: " + typeId);
            }
            pos += dataLen;
        }
    }

    private static void parseStdlibDeps(ByteBuffer buf, int[] section, Packet pkt) {
        int pos = section[0];
        int count = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
        for (int i = 0; i < count; i++) {
            pkt.stdlibDeps.add(Short.toUnsignedInt(buf.getShort(pos)));
            pos += 2;
        }
    }

    private static void parseBytecode(ByteBuffer buf, int[] section, Packet pkt) {
        int pos = section[0];
        int instrCount = section[1] / 8;
        pkt.instructions = new int[instrCount][5];

        for (int i = 0; i < instrCount; i++) {
            pkt.instructions[i][0] = Byte.toUnsignedInt(buf.get(pos));     // opcode
            pkt.instructions[i][1] = Byte.toUnsignedInt(buf.get(pos + 1)); // flags
            pkt.instructions[i][2] = Short.toUnsignedInt(buf.getShort(pos + 2)); // dest
            pkt.instructions[i][3] = Short.toUnsignedInt(buf.getShort(pos + 4)); // operand1
            pkt.instructions[i][4] = Short.toUnsignedInt(buf.getShort(pos + 6)); // operand2
            pos += 8;
        }
    }

    private static void parseTests(ByteBuffer buf, int[] section, Packet pkt) {
        int pos = section[0];
        int testCount = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;

        for (int i = 0; i < testCount; i++) {
            int nameLen = Short.toUnsignedInt(buf.getShort(pos)); pos += 2;
            String name = readString(buf, pos, nameLen); pos += nameLen;
            int inputLen = buf.getInt(pos); pos += 4;
            String inputJson = readString(buf, pos, inputLen); pos += inputLen;
            int outputLen = buf.getInt(pos); pos += 4;
            String outputJson = readString(buf, pos, outputLen); pos += outputLen;
            pkt.testCases.add(new Packet.TestCase(name, inputJson, outputJson));
        }
    }

    private static String readString(ByteBuffer buf, int offset, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = buf.get(offset + i);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
