package io.heluna.vm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class Executor {

    // Opcodes
    public static final int LOAD_CONST = 0x01, LOAD_FIELD = 0x02, LOAD_NOTHING = 0x03, COPY = 0x04;
    public static final int ADD = 0x10, SUB = 0x11, MUL = 0x12, DIV = 0x13, MOD = 0x14, NEGATE = 0x15;
    public static final int EQ = 0x20, NEQ = 0x21, LT = 0x22, GT = 0x23, LTE = 0x24, GTE = 0x25;
    public static final int AND = 0x30, OR = 0x31, NOT = 0x32;
    public static final int STR_CONCAT = 0x40;
    public static final int IS_STRING = 0x50, IS_INT = 0x51, IS_FLOAT = 0x52, IS_BOOL = 0x53;
    public static final int IS_NOTHING = 0x54, IS_LIST = 0x55, IS_RECORD = 0x56;
    public static final int TO_STRING = 0x58, TO_INT = 0x59, TO_FLOAT = 0x5A, TO_BOOL = 0x5B;
    public static final int RECORD_NEW = 0x60, RECORD_SET = 0x61, RECORD_GET = 0x62, RECORD_HAS = 0x63;
    public static final int LIST_NEW = 0x70, LIST_APPEND = 0x71, LIST_GET = 0x72, LIST_LENGTH = 0x73;
    public static final int JUMP = 0x80, JUMP_IF = 0x81, JUMP_IF_NOT = 0x82;
    public static final int COALESCE = 0x85;
    public static final int ITER_SETUP = 0x90, ITER_COLLECT = 0x91;
    public static final int STDLIB_CALL = 0xA0;
    public static final int TAG_SET = 0xB0, TAG_CHECK = 0xB1;

    // Tag modes (from flags bits 3-4)
    public static final int TAG_PROPAGATE = 0, TAG_CLEAR = 1, TAG_MODE_SET = 2;

    private final Packet packet;
    private final HVal[] values;
    private final long[] tags;
    private StdLib stdLib;

    public Executor(Packet packet) {
        this.packet = packet;
        this.values = new HVal[packet.scratchpadSize];
        this.tags = new long[packet.scratchpadSize];
        // Initialize all slots to nothing
        for (int i = 0; i < values.length; i++) {
            values[i] = HVal.HNothing.INSTANCE;
        }
    }

    public void setStdLib(StdLib stdLib) {
        this.stdLib = stdLib;
    }

    // Direct access for testing and integration
    public void setSlot(int index, HVal value) {
        values[index] = value;
    }

    public void setSlot(int index, HVal value, long tagBits) {
        values[index] = value;
        tags[index] = tagBits;
    }

    public HVal getSlot(int index) {
        return values[index];
    }

    public long getTag(int index) {
        return tags[index];
    }

    public void execute() {
        execute(packet.instructions, 0, packet.instructions.length);
    }

    public void execute(int[][] instructions, int start, int end) {
        int pc = start;
        while (pc < end) {
            int[] instr = instructions[pc];
            int opcode = instr[0];
            int flags = instr[1];
            int dest = instr[2];
            int op1 = instr[3];
            int op2 = instr[4];

            int tagMode = (flags >> 3) & 0x03;

            switch (opcode) {
                // --- Scratchpad & Constants ---
                case LOAD_CONST:
                    values[dest] = packet.constants.get(op1);
                    applyTagMode(dest, tagMode, 0);
                    break;

                case LOAD_FIELD:
                    // op1 is field index — value already loaded at input field's scratchpad offset
                    // This is a no-op if input was pre-loaded at the right offset
                    // But we need to handle the case where field_idx maps to a specific slot
                    if (op1 < packet.inputFields.size()) {
                        int srcSlot = packet.inputFields.get(op1).scratchpadOffset;
                        if (dest != srcSlot) {
                            values[dest] = values[srcSlot];
                            tags[dest] = tags[srcSlot];
                        }
                    }
                    break;

                case LOAD_NOTHING:
                    values[dest] = HVal.HNothing.INSTANCE;
                    applyTagMode(dest, tagMode, 0);
                    break;

                case COPY:
                    values[dest] = values[op1];
                    tags[dest] = tags[op1];
                    break;

                // --- Arithmetic ---
                case ADD: execArith(dest, op1, op2, tagMode, '+'); break;
                case SUB: execArith(dest, op1, op2, tagMode, '-'); break;
                case MUL: execArith(dest, op1, op2, tagMode, '*'); break;
                case DIV: execArith(dest, op1, op2, tagMode, '/'); break;
                case MOD: execArith(dest, op1, op2, tagMode, '%'); break;

                case NEGATE: {
                    HVal v = values[op1];
                    if (v instanceof HVal.HInteger) {
                        values[dest] = new HVal.HInteger(-((HVal.HInteger) v).value());
                    } else if (v instanceof HVal.HFloat) {
                        values[dest] = new HVal.HFloat(-((HVal.HFloat) v).value());
                    } else {
                        throw new HelunaException("NEGATE requires numeric, got " + typeName(v));
                    }
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }

                // --- Comparison ---
                case EQ:  values[dest] = HVal.HBoolean.of(valEquals(values[op1], values[op2]));
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;
                case NEQ: values[dest] = HVal.HBoolean.of(!valEquals(values[op1], values[op2]));
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;
                case LT:  values[dest] = HVal.HBoolean.of(valCompare(values[op1], values[op2]) < 0);
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;
                case GT:  values[dest] = HVal.HBoolean.of(valCompare(values[op1], values[op2]) > 0);
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;
                case LTE: values[dest] = HVal.HBoolean.of(valCompare(values[op1], values[op2]) <= 0);
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;
                case GTE: values[dest] = HVal.HBoolean.of(valCompare(values[op1], values[op2]) >= 0);
                          applyTagMode(dest, tagMode, tags[op1] | tags[op2]); break;

                // --- Boolean ---
                case AND:
                    values[dest] = HVal.HBoolean.of(asBool(op1) && asBool(op2));
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                case OR:
                    values[dest] = HVal.HBoolean.of(asBool(op1) || asBool(op2));
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                case NOT:
                    values[dest] = HVal.HBoolean.of(!asBool(op1));
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;

                // --- String ---
                case STR_CONCAT: {
                    String left = valToString(values[op1]);
                    String right = valToString(values[op2]);
                    values[dest] = new HVal.HString(left + right);
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                }

                // --- Type Testing ---
                case IS_STRING:  values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HString);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_INT:     values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HInteger);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_FLOAT:   values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HFloat);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_BOOL:    values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HBoolean);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_NOTHING: values[dest] = HVal.HBoolean.of(values[op1].isNothing());
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_LIST:    values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HList);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_RECORD:  values[dest] = HVal.HBoolean.of(values[op1] instanceof HVal.HRecord);
                                 applyTagMode(dest, tagMode, tags[op1]); break;

                // --- Type Conversion ---
                case TO_STRING:
                    values[dest] = new HVal.HString(valToString(values[op1]));
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                case TO_INT:
                    values[dest] = toInteger(values[op1]);
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                case TO_FLOAT:
                    values[dest] = toFloat(values[op1]);
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                case TO_BOOL:
                    values[dest] = toBool(values[op1]);
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;

                // --- Record ---
                case RECORD_NEW:
                    values[dest] = new HVal.HRecord();
                    applyTagMode(dest, tagMode, 0);
                    break;
                case RECORD_SET: {
                    HVal.HRecord rec = asRecord(dest);
                    String key = asString(op1);
                    rec.set(key, values[op2]);
                    tags[dest] = tags[dest] | tags[op2];
                    break;
                }
                case RECORD_GET: {
                    HVal.HRecord rec = asRecord(op1);
                    String key = asString(op2);
                    values[dest] = rec.get(key);
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }
                case RECORD_HAS: {
                    HVal.HRecord rec = asRecord(op1);
                    String key = asString(op2);
                    values[dest] = HVal.HBoolean.of(rec.has(key));
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }

                // --- List ---
                case LIST_NEW:
                    values[dest] = new HVal.HList();
                    applyTagMode(dest, tagMode, 0);
                    break;
                case LIST_APPEND: {
                    HVal.HList list = asList(dest);
                    list.add(values[op1]);
                    tags[dest] = tags[dest] | tags[op1];
                    break;
                }
                case LIST_GET: {
                    HVal.HList list = asList(op1);
                    HVal idx = values[op2];
                    if (!(idx instanceof HVal.HInteger)) {
                        throw new HelunaException("LIST_GET index must be integer");
                    }
                    values[dest] = list.get((int) ((HVal.HInteger) idx).value());
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }
                case LIST_LENGTH: {
                    HVal.HList list = asList(op1);
                    values[dest] = new HVal.HInteger(list.size());
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }

                // --- Control Flow ---
                case JUMP:
                    pc = dest;
                    continue; // skip pc++

                case JUMP_IF:
                    if (asBool(op1)) { pc = dest; continue; }
                    break;

                case JUMP_IF_NOT:
                    if (!asBool(op1)) { pc = dest; continue; }
                    break;

                // --- Nothing Handling ---
                case COALESCE:
                    if (!values[op1].isNothing()) {
                        values[dest] = values[op1];
                        applyTagMode(dest, tagMode, tags[op1]);
                    } else {
                        values[dest] = values[op2];
                        applyTagMode(dest, tagMode, tags[op2]);
                    }
                    break;

                // --- Iteration ---
                case ITER_SETUP: {
                    int mode = flags & 0x03;
                    HVal src = values[op1];
                    if (!(src instanceof HVal.HList)) {
                        throw new HelunaException("ITER_SETUP source must be list");
                    }
                    HVal.HList srcList = (HVal.HList) src;
                    int bodyLen = op2;
                    int bodyStart = pc + 1;
                    int collectPc = bodyStart + bodyLen;

                    // Get collect instruction for result slot info
                    int[] collectInstr = instructions[collectPc];
                    int resultSlot = collectInstr[2];
                    int slotA = collectInstr[3];
                    int slotB = collectInstr[4];

                    switch (mode) {
                        case 0: { // MAP
                            HVal.HList result = new HVal.HList();
                            for (int i = 0; i < srcList.size(); i++) {
                                values[dest] = srcList.elements().get(i);
                                tags[dest] = tags[op1];
                                execute(instructions, bodyStart, bodyStart + bodyLen);
                                result.add(values[slotA]);
                            }
                            values[resultSlot] = result;
                            tags[resultSlot] = tags[op1];
                            break;
                        }
                        case 1: { // FILTER
                            HVal.HList result = new HVal.HList();
                            for (int i = 0; i < srcList.size(); i++) {
                                values[dest] = srcList.elements().get(i);
                                tags[dest] = tags[op1];
                                execute(instructions, bodyStart, bodyStart + bodyLen);
                                if (values[slotA] instanceof HVal.HBoolean &&
                                        ((HVal.HBoolean) values[slotA]).value()) {
                                    result.add(values[slotB]);
                                }
                            }
                            values[resultSlot] = result;
                            tags[resultSlot] = tags[op1];
                            break;
                        }
                        case 2: { // FOLD
                            // Accumulator is pre-initialized at slotA
                            for (int i = 0; i < srcList.size(); i++) {
                                values[dest] = srcList.elements().get(i);
                                tags[dest] = tags[op1];
                                execute(instructions, bodyStart, bodyStart + bodyLen);
                            }
                            values[resultSlot] = values[slotA];
                            tags[resultSlot] = tags[slotA];
                            break;
                        }
                        case 3: { // MAP_FILTER
                            HVal.HList result = new HVal.HList();
                            for (int i = 0; i < srcList.size(); i++) {
                                values[dest] = srcList.elements().get(i);
                                tags[dest] = tags[op1];
                                execute(instructions, bodyStart, bodyStart + bodyLen);
                                if (values[slotA] instanceof HVal.HBoolean &&
                                        ((HVal.HBoolean) values[slotA]).value()) {
                                    result.add(values[slotB]);
                                }
                            }
                            values[resultSlot] = result;
                            tags[resultSlot] = tags[op1];
                            break;
                        }
                    }
                    pc = collectPc + 1; // skip past ITER_COLLECT
                    continue;
                }

                case ITER_COLLECT:
                    // Handled by ITER_SETUP
                    break;

                // --- Standard Library ---
                case STDLIB_CALL: {
                    if (stdLib == null) {
                        throw new HelunaException("StdLib not configured");
                    }
                    HVal result;
                    if (op1 == 0) {
                        // Sanitizer passthrough — just copy the value through
                        HVal.HRecord args = asRecord(op2);
                        result = args.get("value");
                    } else {
                        result = stdLib.call(op1, asRecord(op2));
                    }
                    values[dest] = result;
                    applyTagMode(dest, tagMode, tags[op2]);
                    break;
                }

                // --- Tag Operations ---
                case TAG_SET: {
                    // op1 is constant index containing the tag value
                    HVal tagVal = packet.constants.get(op1);
                    if (tagVal instanceof HVal.HInteger) {
                        tags[dest] = ((HVal.HInteger) tagVal).value();
                    }
                    break;
                }

                case TAG_CHECK: {
                    HVal tagVal = packet.constants.get(op2);
                    long checkBits = (tagVal instanceof HVal.HInteger) ?
                            ((HVal.HInteger) tagVal).value() : 0;
                    values[dest] = HVal.HBoolean.of((tags[op1] & checkBits) == checkBits);
                    applyTagMode(dest, tagMode, 0);
                    break;
                }

                default:
                    throw new HelunaException(String.format("Unknown opcode 0x%02X at pc=%d", opcode, pc));
            }

            pc++;
        }
    }

    // --- Helper methods ---

    private void applyTagMode(int dest, int tagMode, long propagatedTags) {
        switch (tagMode) {
            case TAG_PROPAGATE: tags[dest] = propagatedTags; break;
            case TAG_CLEAR:     tags[dest] = 0; break;
            case TAG_MODE_SET:  break; // keep whatever was set explicitly
        }
    }

    private void execArith(int dest, int op1, int op2, int tagMode, char op) {
        HVal left = values[op1];
        HVal right = values[op2];

        boolean leftInt = left instanceof HVal.HInteger;
        boolean leftFloat = left instanceof HVal.HFloat;
        boolean rightInt = right instanceof HVal.HInteger;
        boolean rightFloat = right instanceof HVal.HFloat;

        if (!((leftInt || leftFloat) && (rightInt || rightFloat))) {
            throw new HelunaException("Arithmetic requires numeric operands, got "
                    + typeName(left) + " " + op + " " + typeName(right));
        }

        if (leftInt && rightInt) {
            long a = ((HVal.HInteger) left).value();
            long b = ((HVal.HInteger) right).value();
            switch (op) {
                case '+': values[dest] = new HVal.HInteger(a + b); break;
                case '-': values[dest] = new HVal.HInteger(a - b); break;
                case '*': values[dest] = new HVal.HInteger(a * b); break;
                case '/':
                    if (b == 0) throw new HelunaException("Division by zero");
                    values[dest] = new HVal.HInteger(a / b);
                    break;
                case '%':
                    if (b == 0) throw new HelunaException("Division by zero");
                    values[dest] = new HVal.HInteger(a % b);
                    break;
            }
        } else {
            double a = leftInt ? (double) ((HVal.HInteger) left).value()
                               : ((HVal.HFloat) left).value();
            double b = rightInt ? (double) ((HVal.HInteger) right).value()
                                : ((HVal.HFloat) right).value();
            switch (op) {
                case '+': values[dest] = new HVal.HFloat(a + b); break;
                case '-': values[dest] = new HVal.HFloat(a - b); break;
                case '*': values[dest] = new HVal.HFloat(a * b); break;
                case '/':
                    if (b == 0.0) throw new HelunaException("Division by zero");
                    values[dest] = new HVal.HFloat(a / b);
                    break;
                case '%':
                    if (b == 0.0) throw new HelunaException("Division by zero");
                    values[dest] = new HVal.HFloat(a % b);
                    break;
            }
        }
        applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
    }

    private boolean valEquals(HVal a, HVal b) {
        if (a instanceof HVal.HInteger && b instanceof HVal.HFloat) {
            return (double) ((HVal.HInteger) a).value() == ((HVal.HFloat) b).value();
        }
        if (a instanceof HVal.HFloat && b instanceof HVal.HInteger) {
            return ((HVal.HFloat) a).value() == (double) ((HVal.HInteger) b).value();
        }
        if (a.getClass() != b.getClass()) return false;
        return a.equals(b);
    }

    private int valCompare(HVal a, HVal b) {
        if ((a instanceof HVal.HInteger || a instanceof HVal.HFloat) &&
            (b instanceof HVal.HInteger || b instanceof HVal.HFloat)) {
            double da = a instanceof HVal.HInteger ? (double) ((HVal.HInteger) a).value()
                                                    : ((HVal.HFloat) a).value();
            double db = b instanceof HVal.HInteger ? (double) ((HVal.HInteger) b).value()
                                                    : ((HVal.HFloat) b).value();
            return Double.compare(da, db);
        }
        if (a instanceof HVal.HString && b instanceof HVal.HString) {
            return ((HVal.HString) a).value().compareTo(((HVal.HString) b).value());
        }
        throw new HelunaException("Cannot compare " + typeName(a) + " with " + typeName(b));
    }

    private boolean asBool(int slot) {
        HVal v = values[slot];
        if (v instanceof HVal.HBoolean) return ((HVal.HBoolean) v).value();
        throw new HelunaException("Expected boolean at slot " + slot + ", got " + typeName(v));
    }

    private String asString(int slot) {
        HVal v = values[slot];
        if (v instanceof HVal.HString) return ((HVal.HString) v).value();
        throw new HelunaException("Expected string at slot " + slot + ", got " + typeName(v));
    }

    private HVal.HRecord asRecord(int slot) {
        HVal v = values[slot];
        if (v instanceof HVal.HRecord) return (HVal.HRecord) v;
        throw new HelunaException("Expected record at slot " + slot + ", got " + typeName(v));
    }

    private HVal.HList asList(int slot) {
        HVal v = values[slot];
        if (v instanceof HVal.HList) return (HVal.HList) v;
        throw new HelunaException("Expected list at slot " + slot + ", got " + typeName(v));
    }

    static String valToString(HVal v) {
        if (v instanceof HVal.HString) return ((HVal.HString) v).value();
        if (v instanceof HVal.HInteger) return Long.toString(((HVal.HInteger) v).value());
        if (v instanceof HVal.HFloat) {
            double d = ((HVal.HFloat) v).value();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d) + ".0";
            }
            return Double.toString(d);
        }
        if (v instanceof HVal.HBoolean) return ((HVal.HBoolean) v).value() ? "true" : "false";
        if (v instanceof HVal.HNothing) return "nothing";
        return v.toString();
    }

    private HVal toInteger(HVal v) {
        if (v instanceof HVal.HInteger) return v;
        if (v instanceof HVal.HFloat) return new HVal.HInteger((long) ((HVal.HFloat) v).value());
        if (v instanceof HVal.HString) {
            try {
                return new HVal.HInteger(Long.parseLong(((HVal.HString) v).value()));
            } catch (NumberFormatException e) {
                try {
                    return new HVal.HInteger((long) Double.parseDouble(((HVal.HString) v).value()));
                } catch (NumberFormatException e2) {
                    throw new HelunaException("Cannot convert string to integer: " + v);
                }
            }
        }
        if (v instanceof HVal.HBoolean) return new HVal.HInteger(((HVal.HBoolean) v).value() ? 1 : 0);
        throw new HelunaException("Cannot convert " + typeName(v) + " to integer");
    }

    private HVal toFloat(HVal v) {
        if (v instanceof HVal.HFloat) return v;
        if (v instanceof HVal.HInteger) return new HVal.HFloat((double) ((HVal.HInteger) v).value());
        if (v instanceof HVal.HString) {
            try {
                return new HVal.HFloat(Double.parseDouble(((HVal.HString) v).value()));
            } catch (NumberFormatException e) {
                throw new HelunaException("Cannot convert string to float: " + v);
            }
        }
        throw new HelunaException("Cannot convert " + typeName(v) + " to float");
    }

    private HVal toBool(HVal v) {
        if (v instanceof HVal.HBoolean) return v;
        if (v instanceof HVal.HInteger) return HVal.HBoolean.of(((HVal.HInteger) v).value() != 0);
        if (v instanceof HVal.HFloat) return HVal.HBoolean.of(((HVal.HFloat) v).value() != 0.0);
        if (v instanceof HVal.HString) return HVal.HBoolean.of(!((HVal.HString) v).value().isEmpty());
        if (v instanceof HVal.HNothing) return HVal.HBoolean.FALSE;
        return HVal.HBoolean.TRUE;
    }

    static String typeName(HVal v) {
        if (v instanceof HVal.HString) return "string";
        if (v instanceof HVal.HInteger) return "integer";
        if (v instanceof HVal.HFloat) return "float";
        if (v instanceof HVal.HBoolean) return "boolean";
        if (v instanceof HVal.HNothing) return "nothing";
        if (v instanceof HVal.HList) return "list";
        if (v instanceof HVal.HRecord) return "record";
        return "unknown";
    }
}
