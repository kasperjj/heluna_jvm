package io.heluna.vm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class PacketLoaderTest {

    private static Packet pkt;

    @BeforeAll
    static void loadPacket() throws IOException {
        try (InputStream is = PacketLoaderTest.class.getResourceAsStream("/vm-comprehensive.hlna")) {
            assertNotNull(is, "vm-comprehensive.hlna not found on classpath");
            byte[] data = is.readAllBytes();
            pkt = PacketLoader.load(data);
        }
    }

    // --- Header validation ---

    @Test void rejectTooSmall() {
        assertThrows(HelunaException.class, () -> PacketLoader.load(new byte[10]));
    }

    @Test void rejectBadMagic() {
        byte[] bad = new byte[100];
        bad[0] = 0x00; // wrong magic
        assertThrows(HelunaException.class, () -> PacketLoader.load(bad));
    }

    @Test void headerMagic() {
        assertEquals(0x484C4E41, pkt.magic);
    }

    @Test void headerVersion() {
        assertEquals(1, pkt.formatVersion);
    }

    @Test void headerMinSpecVersion() {
        assertEquals(1, pkt.minSpecVersion);
    }

    @Test void headerTotalSize() {
        assertEquals(11931, pkt.totalSize);
    }

    @Test void headerSectionCount() {
        assertEquals(4, pkt.sectionCount);
    }

    // --- Contract ---

    @Test void contractName() {
        assertEquals("vm-comprehensive", pkt.contractName);
    }

    @Test void scratchpadSize() {
        assertEquals(676, pkt.scratchpadSize);
    }

    @Test void inputFieldCount() {
        assertEquals(20, pkt.inputFieldCount);
        assertEquals(20, pkt.inputFields.size());
    }

    @Test void outputFieldCount() {
        assertEquals(89, pkt.outputFieldCount);
        assertEquals(89, pkt.outputFields.size());
    }

    @Test void tagCount() {
        assertEquals(2, pkt.tagCount);
        assertEquals(2, pkt.tagDefs.size());
    }

    @Test void sanitizerCount() {
        assertEquals(1, pkt.sanitizerCount);
        assertEquals(1, pkt.sanitizers.size());
    }

    @Test void ruleCount() {
        assertEquals(4, pkt.ruleCount);
        assertEquals(4, pkt.rules.size());
    }

    // --- Tags ---

    @Test void tagPii() {
        Packet.TagDef pii = pkt.tagDefs.get(0);
        assertEquals(0, pii.bitIndex);
        assertEquals("pii", pii.name);
        assertTrue(pii.description.contains("personally identifiable"));
    }

    @Test void tagSensitive() {
        Packet.TagDef sensitive = pkt.tagDefs.get(1);
        assertEquals(1, sensitive.bitIndex);
        assertEquals("sensitive", sensitive.name);
        assertTrue(sensitive.description.contains("sensitive"));
    }

    // --- Input fields (spot checks) ---

    @Test void inputFieldText() {
        Packet.FieldDef f = pkt.inputFields.get(0);
        assertEquals("text", f.name);
        assertEquals(HVal.TYPE_STRING, f.typeId);
        assertEquals(0, f.tagBits);
        assertEquals(0, f.scratchpadOffset);
    }

    @Test void inputFieldNumber() {
        Packet.FieldDef f = pkt.inputFields.get(2);
        assertEquals("number", f.name);
        assertEquals(HVal.TYPE_INTEGER, f.typeId);
        assertEquals(2, f.scratchpadOffset);
    }

    @Test void inputFieldDecimal() {
        Packet.FieldDef f = pkt.inputFields.get(3);
        assertEquals("decimal", f.name);
        assertEquals(HVal.TYPE_FLOAT, f.typeId);
        assertEquals(3, f.scratchpadOffset);
    }

    @Test void inputFieldFlag() {
        Packet.FieldDef f = pkt.inputFields.get(4);
        assertEquals("flag", f.name);
        assertEquals(HVal.TYPE_BOOLEAN, f.typeId);
        assertEquals(4, f.scratchpadOffset);
    }

    @Test void inputFieldOptionalText() {
        Packet.FieldDef f = pkt.inputFields.get(5);
        assertEquals("optional-text", f.name);
        assertEquals(HVal.TYPE_MAYBE, f.typeId);
        assertEquals(5, f.scratchpadOffset);
    }

    @Test void inputFieldItems() {
        Packet.FieldDef f = pkt.inputFields.get(7);
        assertEquals("items", f.name);
        assertEquals(HVal.TYPE_LIST, f.typeId);
        assertEquals(7, f.scratchpadOffset);
    }

    @Test void inputFieldPerson() {
        Packet.FieldDef f = pkt.inputFields.get(10);
        assertEquals("person", f.name);
        assertEquals(HVal.TYPE_RECORD, f.typeId);
        assertEquals(10, f.scratchpadOffset);
    }

    @Test void inputFieldSecretValueTagged() {
        Packet.FieldDef f = pkt.inputFields.get(13);
        assertEquals("secret-value", f.name);
        assertEquals(0x02, f.tagBits); // sensitive = bit 1
    }

    @Test void inputFieldPersonalNameTagged() {
        Packet.FieldDef f = pkt.inputFields.get(14);
        assertEquals("personal-name", f.name);
        assertEquals(0x03, f.tagBits); // pii + sensitive = bits 0+1
    }

    // --- Output fields (spot checks) ---

    @Test void outputFirstField() {
        Packet.FieldDef f = pkt.outputFields.get(0);
        assertEquals("add-int", f.name);
    }

    @Test void outputLastField() {
        Packet.FieldDef f = pkt.outputFields.get(88);
        assertEquals("hashed-name", f.name);
    }

    // --- Sanitizers ---

    @Test void sanitizerHash() {
        Packet.SanitizerDef s = pkt.sanitizers.get(0);
        assertEquals("hash", s.name);
        assertEquals(0x03, s.stripsTags); // strips pii + sensitive
    }

    // --- Rules ---

    @Test void forbidTaggedRules() {
        // First two rules should be forbid-tagged
        long count = pkt.rules.stream()
                .filter(r -> r.type == Packet.Rule.FORBID_TAGGED)
                .count();
        assertTrue(count >= 2, "Expected at least 2 forbid-tagged rules, got " + count);
    }

    // --- Constants ---

    @Test void constantPoolNotEmpty() {
        assertTrue(pkt.constants.size() > 0, "Constants should not be empty");
    }

    @Test void firstConstantIsInteger() {
        HVal first = pkt.constants.get(0);
        assertTrue(first instanceof HVal.HInteger);
        assertEquals(1L, ((HVal.HInteger) first).value());
    }

    @Test void secondConstantIsString() {
        HVal second = pkt.constants.get(1);
        assertTrue(second instanceof HVal.HString);
        assertEquals("hello", ((HVal.HString) second).value());
    }

    @Test void thirdConstantIsGreeting() {
        HVal third = pkt.constants.get(2);
        assertTrue(third instanceof HVal.HString);
        assertEquals("greeting", ((HVal.HString) third).value());
    }

    // --- Stdlib deps ---

    @Test void stdlibDepsNotEmpty() {
        assertTrue(pkt.stdlibDeps.size() > 0);
    }

    @Test void stdlibDepsContainsSha256() {
        assertTrue(pkt.stdlibDeps.contains(0x0070), "Should include sha256 (0x0070)");
    }

    @Test void stdlibDepsContainsUpper() {
        assertTrue(pkt.stdlibDeps.contains(0x0001), "Should include upper (0x0001)");
    }

    // --- Bytecode ---

    @Test void instructionCountPositive() {
        assertTrue(pkt.instructions.length > 0, "Should have instructions");
        assertEquals(6352 / 8, pkt.instructions.length); // section_size / 8
    }

    @Test void firstInstruction() {
        // From hex: 04 02 6f 00 02 00 00 00
        // opcode=0x04 (COPY), flags=0x02, dest=0x006F=111, op1=2, op2=0
        int[] instr = pkt.instructions[0];
        assertEquals(0x04, instr[0]); // COPY
    }

    // --- Hand-crafted header test ---

    @Test void parseMinimalHeader() {
        ByteBuffer buf = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0, 0x484C4E41); // magic
        buf.putShort(4, (short) 1); // format_version
        buf.putShort(6, (short) 1); // min_spec_version
        buf.putInt(8, 200);         // total_size
        buf.putShort(12, (short) 4); // section_count
        // section directory at offset 88
        int dir = 88;
        // CONTRACT at 128, length=10 (minimal)
        buf.putShort(dir, (short) 0x0001); buf.putInt(dir + 2, 140); buf.putInt(dir + 6, 20);
        // CONSTANTS at 160, length=0
        buf.putShort(dir + 10, (short) 0x0002); buf.putInt(dir + 12, 160); buf.putInt(dir + 16, 0);
        // STDLIB_DEPS at 160, length=2
        buf.putShort(dir + 20, (short) 0x0003); buf.putInt(dir + 22, 160); buf.putInt(dir + 26, 2);
        // BYTECODE at 162, length=0
        buf.putShort(dir + 30, (short) 0x0004); buf.putInt(dir + 32, 162); buf.putInt(dir + 36, 0);

        // Minimal CONTRACT: name_len=1, "X", scratchpad=1, 0 fields/tags/sanitizers/rules
        int c = 140;
        buf.putShort(c, (short) 1); c += 2;
        buf.put(c, (byte) 'X'); c += 1;
        buf.putShort(c, (short) 1); c += 2; // scratchpad_size
        buf.putShort(c, (short) 0); c += 2; // input_field_count
        buf.putShort(c, (short) 0); c += 2; // output_field_count
        buf.putShort(c, (short) 0); c += 2; // tag_count
        buf.putShort(c, (short) 0); c += 2; // sanitizer_count
        buf.putShort(c, (short) 0); // rule_count

        // STDLIB_DEPS: count=0
        buf.putShort(160, (short) 0);

        Packet p = PacketLoader.load(buf.array());
        assertEquals("X", p.contractName);
        assertEquals(1, p.scratchpadSize);
        assertEquals(0, p.inputFields.size());
        assertEquals(0, p.constants.size());
        assertEquals(0, p.instructions.length);
    }
}
