package io.heluna.vm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

class ExecutorTest {

    // Helper: build a minimal Packet with given constants and instructions
    private static Packet makePacket(int scratchpadSize, HVal[] constants, int[][] instructions) {
        Packet pkt = new Packet();
        pkt.scratchpadSize = scratchpadSize;
        pkt.constants.addAll(Arrays.asList(constants));
        pkt.instructions = instructions;
        return pkt;
    }

    private static int[] instr(int opcode, int flags, int dest, int op1, int op2) {
        return new int[]{opcode, flags, dest, op1, op2};
    }

    private static int[] instr(int opcode, int dest, int op1, int op2) {
        return instr(opcode, 0, dest, op1, op2);
    }

    // --- LOAD_CONST ---

    @Test void loadConstInteger() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(42)},
                new int[][]{ instr(0x01, 0, 0, 0) }); // LOAD_CONST slot0, const0
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(42), ex.getSlot(0));
    }

    @Test void loadConstString() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("hello")},
                new int[][]{ instr(0x01, 0, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("hello"), ex.getSlot(0));
    }

    // --- LOAD_NOTHING ---

    @Test void loadNothing() {
        Packet pkt = makePacket(2, new HVal[]{},
                new int[][]{ instr(0x03, 0, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(99)); // pre-fill to verify overwrite
        ex.execute();
        assertTrue(ex.getSlot(0).isNothing());
    }

    // --- COPY ---

    @Test void copy() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(7)},
                new int[][]{
                    instr(0x01, 0, 0, 0), // LOAD_CONST slot0 = 7
                    instr(0x04, 1, 0, 0)  // COPY slot1 = slot0
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(7), ex.getSlot(1));
    }

    // --- ADD ---

    @Test void addIntInt() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(10), new HVal.HInteger(20)},
                new int[][]{
                    instr(0x01, 0, 0, 0), // slot0 = 10
                    instr(0x01, 1, 1, 0), // slot1 = 20
                    instr(0x10, 2, 0, 1)  // slot2 = slot0 + slot1
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(30), ex.getSlot(2));
    }

    @Test void addIntFloat() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(5), new HVal.HFloat(2.5)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x01, 1, 1, 0),
                    instr(0x10, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HFloat(7.5), ex.getSlot(2));
    }

    @Test void addFloatFloat() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HFloat(1.5), new HVal.HFloat(2.5)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x01, 1, 1, 0),
                    instr(0x10, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HFloat(4.0), ex.getSlot(2));
    }

    // --- SUB, MUL, DIV, MOD ---

    @Test void subIntInt() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(30), new HVal.HInteger(12)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x11, 2, 0, 1) // SUB
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(18), ex.getSlot(2));
    }

    @Test void mulIntInt() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(6), new HVal.HInteger(7)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x12, 2, 0, 1) // MUL
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(42), ex.getSlot(2));
    }

    @Test void divIntInt() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(20), new HVal.HInteger(3)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x13, 2, 0, 1) // DIV
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(6), ex.getSlot(2)); // truncates toward zero
    }

    @Test void divByZeroThrows() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(10), new HVal.HInteger(0)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x13, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void modIntInt() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(17), new HVal.HInteger(5)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x14, 2, 0, 1) // MOD
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(2), ex.getSlot(2));
    }

    // --- NEGATE ---

    @Test void negateInt() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(5)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x15, 1, 0, 0) // NEGATE
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(-5), ex.getSlot(1));
    }

    @Test void negateFloat() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HFloat(3.14)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x15, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HFloat(-3.14), ex.getSlot(1));
    }

    // --- EQ, NEQ ---

    @Test void eqIntegers() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(5), new HVal.HInteger(5)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x20, 2, 0, 1) // EQ
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(2));
    }

    @Test void neqDifferent() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), new HVal.HInteger(2)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x21, 2, 0, 1) // NEQ
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(2));
    }

    @Test void eqCrossTypeReturnsFalse() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), new HVal.HString("1")},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x20, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(2));
    }

    // --- LT, GT, LTE, GTE ---

    @Test void ltIntegers() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(3), new HVal.HInteger(5)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x22, 2, 0, 1) // LT
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(2));
    }

    @Test void gteStrings() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HString("b"), new HVal.HString("a")},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x25, 2, 0, 1) // GTE
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(2));
    }

    // --- AND, OR, NOT ---

    @Test void andTrueTrue() {
        Packet pkt = makePacket(3, new HVal[]{HVal.HBoolean.TRUE, HVal.HBoolean.TRUE},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x30, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(2));
    }

    @Test void andTrueFalse() {
        Packet pkt = makePacket(3, new HVal[]{HVal.HBoolean.TRUE, HVal.HBoolean.FALSE},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x30, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(2));
    }

    @Test void orFalseFalse() {
        Packet pkt = makePacket(3, new HVal[]{HVal.HBoolean.FALSE, HVal.HBoolean.FALSE},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x31, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(2));
    }

    @Test void notTrue() {
        Packet pkt = makePacket(2, new HVal[]{HVal.HBoolean.TRUE},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x32, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void boolNonBooleanThrows() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), HVal.HBoolean.TRUE},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x30, 2, 0, 1) // AND with int
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    // --- STR_CONCAT ---

    @Test void strConcat() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HString("hello "), new HVal.HString("world")},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x40, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("hello world"), ex.getSlot(2));
    }

    @Test void strConcatAutoConvert() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HString("val="), new HVal.HInteger(42)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x40, 2, 0, 1)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("val=42"), ex.getSlot(2));
    }

    // --- Tag propagation ---

    @Test void tagPropagation() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), new HVal.HInteger(2)},
                new int[][]{
                    instr(0x01, 0, 0, 0), // slot0 = 1
                    instr(0x01, 1, 1, 0), // slot1 = 2
                    instr(0x10, 2, 0, 1)  // slot2 = slot0 + slot1
                });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(1), 0x01); // tag bit 0
        ex.setSlot(1, new HVal.HInteger(2), 0x02); // tag bit 1
        // Execute only the ADD
        ex.execute(pkt.instructions, 2, 3);
        assertEquals(0x03, ex.getTag(2)); // OR of both tags
    }

    @Test void tagClearMode() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), new HVal.HInteger(2)},
                new int[][]{
                    instr(0x10, 0x08, 2, 0, 1) // ADD with tag_mode=CLEAR (bits 3-4 = 01)
                });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(1), 0xFF);
        ex.setSlot(1, new HVal.HInteger(2), 0xFF);
        ex.execute();
        assertEquals(0, ex.getTag(2)); // cleared
    }

    // --- JUMP ---

    @Test void jumpForward() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(1), new HVal.HInteger(2)},
                new int[][]{
                    instr(0x01, 0, 0, 0),  // slot0 = 1
                    instr(0x80, 0, 3, 0, 0), // JUMP to 3
                    instr(0x01, 0, 1, 0),  // slot0 = 2 (skipped)
                    instr(0x03, 1, 0, 0)   // LOAD_NOTHING slot1 (landing)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(1), ex.getSlot(0)); // should still be 1
    }

    @Test void jumpIfTaken() {
        Packet pkt = makePacket(2, new HVal[]{HVal.HBoolean.TRUE, new HVal.HInteger(99)},
                new int[][]{
                    instr(0x01, 0, 0, 0),    // slot0 = true
                    instr(0x81, 0, 3, 0, 0), // JUMP_IF(slot0) to 3
                    instr(0x01, 1, 1, 0),    // slot1 = 99 (skipped)
                    instr(0x03, 1, 0, 0)     // LOAD_NOTHING slot1
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertTrue(ex.getSlot(1).isNothing());
    }

    @Test void jumpIfNotTaken() {
        Packet pkt = makePacket(2, new HVal[]{HVal.HBoolean.FALSE, new HVal.HInteger(99)},
                new int[][]{
                    instr(0x01, 0, 0, 0),    // slot0 = false
                    instr(0x82, 0, 3, 0, 0), // JUMP_IF_NOT(slot0) to 3
                    instr(0x01, 1, 1, 0),    // slot1 = 99 (skipped)
                    instr(0x03, 1, 0, 0)     // LOAD_NOTHING slot1
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertTrue(ex.getSlot(1).isNothing());
    }

    // --- COALESCE ---

    @Test void coalesceWithValue() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(42), new HVal.HInteger(0)},
                new int[][]{
                    instr(0x01, 0, 0, 0), // slot0 = 42
                    instr(0x01, 1, 1, 0), // slot1 = 0
                    instr(0x85, 2, 0, 1)  // COALESCE slot2 = slot0 or slot1
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(42), ex.getSlot(2));
    }

    @Test void coalesceWithNothing() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(99)},
                new int[][]{
                    instr(0x03, 0, 0, 0), // slot0 = nothing
                    instr(0x01, 1, 0, 0), // slot1 = 99
                    instr(0x85, 2, 0, 1)  // COALESCE slot2 = slot0 or slot1
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(99), ex.getSlot(2));
    }

    // --- IS_* type tests ---

    @Test void isString() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("hi")},
                new int[][]{ instr(0x01, 0, 0, 0), instr(0x50, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(1));
    }

    @Test void isIntFalseForString() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("hi")},
                new int[][]{ instr(0x01, 0, 0, 0), instr(0x51, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void isNothing() {
        Packet pkt = makePacket(2, new HVal[]{},
                new int[][]{ instr(0x03, 0, 0, 0), instr(0x54, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(1));
    }

    // --- TO_* conversions ---

    @Test void toStringFromInt() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(42)},
                new int[][]{ instr(0x01, 0, 0, 0), instr(0x58, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("42"), ex.getSlot(1));
    }

    @Test void toIntFromFloat() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HFloat(3.9)},
                new int[][]{ instr(0x01, 0, 0, 0), instr(0x59, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(3), ex.getSlot(1));
    }

    @Test void toFloatFromInt() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(5)},
                new int[][]{ instr(0x01, 0, 0, 0), instr(0x5A, 1, 0, 0) });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HFloat(5.0), ex.getSlot(1));
    }

    // --- Record ops ---

    @Test void recordNewSetGet() {
        Packet pkt = makePacket(4,
                new HVal[]{new HVal.HString("name"), new HVal.HString("Alice")},
                new int[][]{
                    instr(0x01, 0, 0, 0), // slot0 = "name"
                    instr(0x01, 1, 1, 0), // slot1 = "Alice"
                    instr(0x60, 2, 0, 0), // RECORD_NEW slot2
                    instr(0x61, 2, 0, 1), // RECORD_SET slot2["name"] = "Alice"
                    instr(0x62, 3, 2, 0)  // RECORD_GET slot3 = slot2["name"]
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("Alice"), ex.getSlot(3));
    }

    @Test void recordHas() {
        Packet pkt = makePacket(4,
                new HVal[]{new HVal.HString("key"), new HVal.HInteger(1), new HVal.HString("nope")},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0), instr(0x01, 3, 2, 0),
                    instr(0x60, 2, 0, 0),    // RECORD_NEW slot2
                    instr(0x61, 2, 0, 1),    // RECORD_SET slot2["key"] = 1
                    instr(0x63, 3, 2, 0)     // RECORD_HAS slot3 = slot2 has "key"
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(3));
    }

    // --- List ops ---

    @Test void listNewAppendGetLength() {
        Packet pkt = makePacket(5,
                new HVal[]{new HVal.HInteger(10), new HVal.HInteger(20), new HVal.HInteger(0)},
                new int[][]{
                    instr(0x01, 0, 0, 0), // slot0 = 10
                    instr(0x01, 1, 1, 0), // slot1 = 20
                    instr(0x01, 3, 2, 0), // slot3 = 0 (index)
                    instr(0x70, 2, 0, 0), // LIST_NEW slot2
                    instr(0x71, 2, 0, 0), // LIST_APPEND slot2, slot0(10)
                    instr(0x71, 2, 1, 0), // LIST_APPEND slot2, slot1(20)
                    instr(0x72, 4, 2, 3), // LIST_GET slot4 = slot2[slot3(0)]
                    instr(0x73, 3, 2, 0)  // LIST_LENGTH slot3 = len(slot2)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(10), ex.getSlot(4)); // first element
        assertEquals(new HVal.HInteger(2), ex.getSlot(3));   // length
    }

    // --- Iteration (MAP) ---

    @Test void iterMap() {
        // Build a list [1, 2, 3], map each element * 10
        Packet pkt = makePacket(10,
                new HVal[]{
                    new HVal.HInteger(1), new HVal.HInteger(2), new HVal.HInteger(3),
                    new HVal.HInteger(10)
                },
                new int[][]{
                    // Build list at slot 0
                    instr(0x70, 0, 0, 0),           // 0: LIST_NEW slot0
                    instr(0x01, 5, 0, 0),            // 1: slot5 = const[0] = 1
                    instr(0x71, 0, 5, 0),            // 2: LIST_APPEND slot0, slot5
                    instr(0x01, 5, 1, 0),            // 3: slot5 = const[1] = 2
                    instr(0x71, 0, 5, 0),            // 4: LIST_APPEND slot0, slot5
                    instr(0x01, 5, 2, 0),            // 5: slot5 = const[2] = 3
                    instr(0x71, 0, 5, 0),            // 6: LIST_APPEND slot0, slot5
                    instr(0x01, 6, 3, 0),            // 7: slot6 = const[3] = 10
                    // ITER_SETUP: element=slot1, source=slot0, body_length=1
                    instr(0x90, 0x00, 1, 0, 1),      // 8: MAP mode, element in slot1
                    // Body: slot2 = slot1 * slot6
                    instr(0x12, 2, 1, 6),            // 9: MUL slot2 = slot1 * slot6
                    // ITER_COLLECT: result=slot3, slot_a=slot2, slot_b=0
                    instr(0x91, 3, 2, 0),            // 10: collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        HVal result = ex.getSlot(3);
        assertTrue(result instanceof HVal.HList);
        HVal.HList list = (HVal.HList) result;
        assertEquals(3, list.size());
        assertEquals(new HVal.HInteger(10), list.get(0));
        assertEquals(new HVal.HInteger(20), list.get(1));
        assertEquals(new HVal.HInteger(30), list.get(2));
    }

    // --- Iteration (FILTER) ---

    @Test void iterFilter() {
        // Filter [1, 2, 3, 4, 5] keeping elements > 2
        Packet pkt = makePacket(10,
                new HVal[]{
                    new HVal.HInteger(1), new HVal.HInteger(2), new HVal.HInteger(3),
                    new HVal.HInteger(4), new HVal.HInteger(5), new HVal.HInteger(2)
                },
                new int[][]{
                    instr(0x70, 0, 0, 0),           // 0: LIST_NEW slot0
                    instr(0x01, 5, 0, 0), instr(0x71, 0, 5, 0), // 1-2: append 1
                    instr(0x01, 5, 1, 0), instr(0x71, 0, 5, 0), // 3-4: append 2
                    instr(0x01, 5, 2, 0), instr(0x71, 0, 5, 0), // 5-6: append 3
                    instr(0x01, 5, 3, 0), instr(0x71, 0, 5, 0), // 7-8: append 4
                    instr(0x01, 5, 4, 0), instr(0x71, 0, 5, 0), // 9-10: append 5
                    instr(0x01, 6, 5, 0),                         // 11: slot6 = 2
                    // ITER_SETUP: element=slot1, source=slot0, body_length=1, mode=FILTER(1)
                    instr(0x90, 0x01, 1, 0, 1),                  // 12: FILTER
                    // Body: slot2 = slot1 > slot6
                    instr(0x23, 2, 1, 6),                         // 13: GT slot2 = slot1 > slot6
                    // ITER_COLLECT: result=slot3, slot_a=slot2(predicate), slot_b=slot1(value)
                    instr(0x91, 3, 2, 1),                         // 14: collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        HVal.HList list = (HVal.HList) ex.getSlot(3);
        assertEquals(3, list.size());
        assertEquals(new HVal.HInteger(3), list.get(0));
        assertEquals(new HVal.HInteger(4), list.get(1));
        assertEquals(new HVal.HInteger(5), list.get(2));
    }

    // --- Iteration (MAP_FILTER mode 3) ---

    @Test void iterMapFilter() {
        // MAP_FILTER [1, 2, 3, 4, 5]: keep > 2, then double
        Packet pkt = makePacket(10,
                new HVal[]{
                    new HVal.HInteger(1), new HVal.HInteger(2), new HVal.HInteger(3),
                    new HVal.HInteger(4), new HVal.HInteger(5), new HVal.HInteger(2)
                },
                new int[][]{
                    instr(0x70, 0, 0, 0),           // 0: LIST_NEW slot0
                    instr(0x01, 5, 0, 0), instr(0x71, 0, 5, 0), // 1-2: append 1
                    instr(0x01, 5, 1, 0), instr(0x71, 0, 5, 0), // 3-4: append 2
                    instr(0x01, 5, 2, 0), instr(0x71, 0, 5, 0), // 5-6: append 3
                    instr(0x01, 5, 3, 0), instr(0x71, 0, 5, 0), // 7-8: append 4
                    instr(0x01, 5, 4, 0), instr(0x71, 0, 5, 0), // 9-10: append 5
                    instr(0x01, 6, 5, 0),                         // 11: slot6 = 2
                    // ITER_SETUP: element=slot1, source=slot0, body_length=2, mode=MAP_FILTER(3)
                    instr(0x90, 0x03, 1, 0, 2),                  // 12: MAP_FILTER
                    // Body: slot2 = slot1 > slot6 (predicate), slot7 = slot1 * slot6 (mapped)
                    instr(0x23, 2, 1, 6),                         // 13: GT slot2 = slot1 > 2
                    instr(0x12, 7, 1, 6),                         // 14: MUL slot7 = slot1 * 2
                    // ITER_COLLECT: result=slot3, slot_a=slot2(predicate), slot_b=slot7(value)
                    instr(0x91, 3, 2, 7),                         // 15: collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        HVal.HList list = (HVal.HList) ex.getSlot(3);
        assertEquals(3, list.size());
        assertEquals(new HVal.HInteger(6), list.get(0));  // 3*2
        assertEquals(new HVal.HInteger(8), list.get(1));  // 4*2
        assertEquals(new HVal.HInteger(10), list.get(2)); // 5*2
    }

    // --- Iteration (FOLD) ---

    @Test void iterFold() {
        // Fold [1, 2, 3, 4] with sum, starting at 0
        Packet pkt = makePacket(10,
                new HVal[]{
                    new HVal.HInteger(1), new HVal.HInteger(2), new HVal.HInteger(3),
                    new HVal.HInteger(4), new HVal.HInteger(0)
                },
                new int[][]{
                    instr(0x70, 0, 0, 0),           // 0: LIST_NEW slot0
                    instr(0x01, 5, 0, 0), instr(0x71, 0, 5, 0), // 1-2: append 1
                    instr(0x01, 5, 1, 0), instr(0x71, 0, 5, 0), // 3-4: append 2
                    instr(0x01, 5, 2, 0), instr(0x71, 0, 5, 0), // 5-6: append 3
                    instr(0x01, 5, 3, 0), instr(0x71, 0, 5, 0), // 7-8: append 4
                    instr(0x01, 2, 4, 0),                         // 9: slot2 = 0 (accumulator)
                    // ITER_SETUP: element=slot1, source=slot0, body_length=1, mode=FOLD(2)|SEQUENTIAL(4)
                    instr(0x90, 0x06, 1, 0, 1),                  // 10: FOLD + SEQUENTIAL
                    // Body: slot2 = slot2 + slot1
                    instr(0x10, 2, 2, 1),                         // 11: ADD slot2 += slot1
                    // ITER_COLLECT: result=slot3, slot_a=slot2(accumulator)
                    instr(0x91, 3, 2, 0),                         // 12: collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(10), ex.getSlot(3));
    }

    // ========== Error Path Tests ==========

    @Test void modByZeroThrows() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(10), new HVal.HInteger(0)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x14, 2, 0, 1) // MOD
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void negateNonNumericThrows() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("hello")},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x15, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void arithTypeMismatchThrows() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HString("hello"), new HVal.HInteger(1)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x10, 2, 0, 1) // ADD
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void toIntFromInvalidStringThrows() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("abc")},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x59, 1, 0, 0) // TO_INT
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void toFloatFromInvalidStringThrows() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("abc")},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5A, 1, 0, 0) // TO_FLOAT
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void compareDifferentTypesThrows() {
        Packet pkt = makePacket(3, new HVal[]{new HVal.HString("a"), new HVal.HInteger(1)},
                new int[][]{
                    instr(0x01, 0, 0, 0), instr(0x01, 1, 1, 0),
                    instr(0x22, 2, 0, 1) // LT
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    @Test void listGetNonIntIndexThrows() {
        Packet pkt = makePacket(4, new HVal[]{new HVal.HInteger(1), new HVal.HString("bad")},
                new int[][]{
                    instr(0x70, 0, 0, 0),           // LIST_NEW slot0
                    instr(0x01, 1, 0, 0),            // slot1 = 1
                    instr(0x71, 0, 1, 0),            // LIST_APPEND
                    instr(0x01, 2, 1, 0),            // slot2 = "bad"
                    instr(0x72, 3, 0, 2)             // LIST_GET with string index
                });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }

    // ========== Tag Operations ==========

    @Test void tagSetInstruction() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(0x05)},
                new int[][]{
                    instr(0xB0, 0, 0, 0, 0)  // TAG_SET dest=0, tag from const[0]
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(0x05, ex.getTag(0));
    }

    @Test void tagCheckInstruction() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(0x02)},
                new int[][]{
                    instr(0xB1, 0, 1, 0, 0)  // TAG_CHECK dest=1, check slot0 against const[0]
                });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(42), 0x03); // has bits 0 and 1
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(1)); // bit 1 is set
    }

    @Test void tagCheckFails() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(0x04)},
                new int[][]{
                    instr(0xB1, 0, 1, 0, 0)  // TAG_CHECK
                });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(42), 0x03); // bits 0,1 but checking bit 2
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void tagModeSetPreservesExisting() {
        // TAG_MODE_SET (bits 3-4 = 10 = 0x10)
        Packet pkt = makePacket(3, new HVal[]{new HVal.HInteger(1), new HVal.HInteger(2)},
                new int[][]{
                    instr(0x10, 0x10, 2, 0, 1) // ADD with TAG_MODE_SET
                });
        Executor ex = new Executor(pkt);
        ex.setSlot(0, new HVal.HInteger(1), 0x00);
        ex.setSlot(1, new HVal.HInteger(2), 0x00);
        ex.setSlot(2, new HVal.HInteger(0), 0xAB); // pre-existing tag
        ex.execute();
        assertEquals(new HVal.HInteger(3), ex.getSlot(2));
        assertEquals(0xAB, ex.getTag(2)); // tag preserved, not overwritten
    }

    // ========== Conversion Edge Cases ==========

    @Test void toBoolFromInt() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(0)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5B, 1, 0, 0) // TO_BOOL
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void toBoolFromNonZeroInt() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HInteger(1)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5B, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(1));
    }

    @Test void toBoolFromFloat() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HFloat(0.0)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5B, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void toBoolFromEmptyString() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("")},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5B, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void toBoolFromNonEmptyString() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HString("hi")},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x5B, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.TRUE, ex.getSlot(1));
    }

    @Test void toBoolFromNothing() {
        Packet pkt = makePacket(2, new HVal[]{},
                new int[][]{
                    instr(0x03, 0, 0, 0), // LOAD_NOTHING
                    instr(0x5B, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(HVal.HBoolean.FALSE, ex.getSlot(1));
    }

    @Test void toStringFromNothing() {
        Packet pkt = makePacket(2, new HVal[]{},
                new int[][]{
                    instr(0x03, 0, 0, 0),
                    instr(0x58, 1, 0, 0) // TO_STRING
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("nothing"), ex.getSlot(1));
    }

    @Test void toStringFromFloat() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HFloat(1.0)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x58, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("1.0"), ex.getSlot(1));
    }

    @Test void toStringFromFloatDecimal() {
        Packet pkt = makePacket(2, new HVal[]{new HVal.HFloat(1.5)},
                new int[][]{
                    instr(0x01, 0, 0, 0),
                    instr(0x58, 1, 0, 0)
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HString("1.5"), ex.getSlot(1));
    }

    // ========== Stdlib Call Sanitizer Passthrough ==========

    @Test void stdlibCallSanitizerPassthrough() {
        Packet pkt = makePacket(4, new HVal[]{new HVal.HString("value"), new HVal.HString("hello")},
                new int[][]{
                    // Build args record at slot 2 with key "value" -> "hello"
                    instr(0x60, 2, 0, 0),           // RECORD_NEW slot2
                    instr(0x01, 0, 0, 0),            // slot0 = "value"
                    instr(0x01, 1, 1, 0),            // slot1 = "hello"
                    instr(0x61, 2, 0, 1),            // RECORD_SET slot2["value"] = "hello"
                    // STDLIB_CALL dest=3, funcId=0 (sanitizer passthrough), args=slot2
                    // flags with TAG_CLEAR mode (0x08)
                    instr(0xA0, 0x08, 3, 0, 2),
                });
        Executor ex = new Executor(pkt);
        StdLib stdLib = new StdLib();
        ex.setStdLib(stdLib);
        ex.execute();
        assertEquals(new HVal.HString("hello"), ex.getSlot(3));
        assertEquals(0, ex.getTag(3)); // tag cleared via tag mode
    }

    // ========== Empty List Iteration ==========

    @Test void mapEmptyList() {
        Packet pkt = makePacket(10, new HVal[]{new HVal.HInteger(10)},
                new int[][]{
                    instr(0x70, 0, 0, 0),           // 0: LIST_NEW slot0 (empty)
                    instr(0x01, 6, 0, 0),            // 1: slot6 = 10
                    instr(0x90, 0x00, 1, 0, 1),      // 2: MAP mode
                    instr(0x12, 2, 1, 6),            // 3: MUL (body)
                    instr(0x91, 3, 2, 0),            // 4: collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        HVal.HList list = (HVal.HList) ex.getSlot(3);
        assertEquals(0, list.size());
    }

    @Test void filterEmptyList() {
        Packet pkt = makePacket(10, new HVal[]{new HVal.HInteger(2)},
                new int[][]{
                    instr(0x70, 0, 0, 0),           // LIST_NEW slot0 (empty)
                    instr(0x01, 6, 0, 0),            // slot6 = 2
                    instr(0x90, 0x01, 1, 0, 1),      // FILTER mode
                    instr(0x23, 2, 1, 6),            // GT body
                    instr(0x91, 3, 2, 1),            // collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        HVal.HList list = (HVal.HList) ex.getSlot(3);
        assertEquals(0, list.size());
    }

    @Test void foldEmptyList() {
        Packet pkt = makePacket(10, new HVal[]{new HVal.HInteger(99)},
                new int[][]{
                    instr(0x70, 0, 0, 0),           // LIST_NEW slot0 (empty)
                    instr(0x01, 2, 0, 0),            // slot2 = 99 (accumulator)
                    instr(0x90, 0x02, 1, 0, 1),      // FOLD mode
                    instr(0x10, 2, 2, 1),            // ADD body
                    instr(0x91, 3, 2, 0),            // collect
                });
        Executor ex = new Executor(pkt);
        ex.execute();
        assertEquals(new HVal.HInteger(99), ex.getSlot(3)); // returns initial accumulator
    }

    // ========== Unknown Opcode ==========

    @Test void unknownOpcodeThrows() {
        Packet pkt = makePacket(2, new HVal[]{},
                new int[][]{ instr(0xFF, 0, 0, 0) });
        Executor ex = new Executor(pkt);
        assertThrows(HelunaException.class, ex::execute);
    }
}
