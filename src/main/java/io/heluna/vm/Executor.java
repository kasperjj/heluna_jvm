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

    // Superinstructions
    public static final int RECORD_GET_C = 0xC1, RECORD_SET_C = 0xC2;
    public static final int RECORD_NEW_SET_C = 0xC3;
    public static final int STDLIB_CALL_1 = 0xC4;
    public static final int CMP_JUMP_EQ = 0xC5, CMP_JUMP_NEQ = 0xC6;
    public static final int CMP_JUMP_LT = 0xC7, CMP_JUMP_GT = 0xC8;
    public static final int CMP_JUMP_LTE = 0xC9, CMP_JUMP_GTE = 0xCA;
    public static final int IS_NOTHING_JUMP = 0xCB;

    // Tag modes (from flags bits 3-4)
    public static final int TAG_PROPAGATE = 0, TAG_CLEAR = 1, TAG_MODE_SET = 2;

    private static class IterState {
        final int mode, dest, bodyStart, bodyEnd, resultSlot, slotA, slotB;
        final long srcTags;
        final ArrayList<HVal> elements;
        final int size;
        HVal.HList result; // null for FOLD
        int idx;

        IterState(int mode, int dest, int bodyStart, int bodyEnd,
                  int resultSlot, int slotA, int slotB,
                  long srcTags, ArrayList<HVal> elements) {
            this.mode = mode; this.dest = dest;
            this.bodyStart = bodyStart; this.bodyEnd = bodyEnd;
            this.resultSlot = resultSlot; this.slotA = slotA; this.slotB = slotB;
            this.srcTags = srcTags;
            this.elements = elements; this.size = elements.size();
            this.result = (mode == 2) ? null : new HVal.HList(this.size);
            this.idx = 0;
        }
    }

    private final Packet packet;
    private final HVal[] values;
    private final long[] tags;
    private final Deque<IterState> iterStack = new ArrayDeque<>();
    private StdLib stdLib;
    private final HVal.HRecord stdlibArg1 = new HVal.HRecord();

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
                case ADD: {
                    HVal left = values[op1], right = values[op2];
                    byte lt = left.typeCode(), rt = right.typeCode();
                    if (lt == HVal.TYPE_INTEGER && rt == HVal.TYPE_INTEGER) {
                        values[dest] = HVal.HInteger.of(((HVal.HInteger) left).value() + ((HVal.HInteger) right).value());
                    } else if (lt == HVal.TYPE_FLOAT && rt == HVal.TYPE_FLOAT) {
                        values[dest] = new HVal.HFloat(((HVal.HFloat) left).value() + ((HVal.HFloat) right).value());
                    } else {
                        execArith(dest, op1, op2, tagMode, '+');
                        break;
                    }
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                }
                case SUB: {
                    HVal left = values[op1], right = values[op2];
                    byte lt = left.typeCode(), rt = right.typeCode();
                    if (lt == HVal.TYPE_INTEGER && rt == HVal.TYPE_INTEGER) {
                        values[dest] = HVal.HInteger.of(((HVal.HInteger) left).value() - ((HVal.HInteger) right).value());
                    } else if (lt == HVal.TYPE_FLOAT && rt == HVal.TYPE_FLOAT) {
                        values[dest] = new HVal.HFloat(((HVal.HFloat) left).value() - ((HVal.HFloat) right).value());
                    } else {
                        execArith(dest, op1, op2, tagMode, '-');
                        break;
                    }
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                }
                case MUL: {
                    HVal left = values[op1], right = values[op2];
                    byte lt = left.typeCode(), rt = right.typeCode();
                    if (lt == HVal.TYPE_INTEGER && rt == HVal.TYPE_INTEGER) {
                        values[dest] = HVal.HInteger.of(((HVal.HInteger) left).value() * ((HVal.HInteger) right).value());
                    } else if (lt == HVal.TYPE_FLOAT && rt == HVal.TYPE_FLOAT) {
                        values[dest] = new HVal.HFloat(((HVal.HFloat) left).value() * ((HVal.HFloat) right).value());
                    } else {
                        execArith(dest, op1, op2, tagMode, '*');
                        break;
                    }
                    applyTagMode(dest, tagMode, tags[op1] | tags[op2]);
                    break;
                }
                case DIV: execArith(dest, op1, op2, tagMode, '/'); break;
                case MOD: execArith(dest, op1, op2, tagMode, '%'); break;

                case NEGATE: {
                    HVal v = values[op1];
                    switch (v.typeCode()) {
                        case HVal.TYPE_INTEGER:
                            values[dest] = HVal.HInteger.of(-((HVal.HInteger) v).value());
                            break;
                        case HVal.TYPE_FLOAT:
                            values[dest] = new HVal.HFloat(-((HVal.HFloat) v).value());
                            break;
                        default:
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
                case IS_STRING:  values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_STRING);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_INT:     values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_INTEGER);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_FLOAT:   values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_FLOAT);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_BOOL:    values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_BOOLEAN);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_NOTHING: values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_NOTHING);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_LIST:    values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_LIST);
                                 applyTagMode(dest, tagMode, tags[op1]); break;
                case IS_RECORD:  values[dest] = HVal.HBoolean.of(values[op1].typeCode() == HVal.TYPE_RECORD);
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
                    if (idx.typeCode() != HVal.TYPE_INTEGER) {
                        throw new HelunaException("LIST_GET index must be integer");
                    }
                    values[dest] = list.get((int) ((HVal.HInteger) idx).value());
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }
                case LIST_LENGTH: {
                    HVal.HList list = asList(op1);
                    values[dest] = HVal.HInteger.of(list.size());
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
                    if (src.typeCode() != HVal.TYPE_LIST) {
                        throw new HelunaException("ITER_SETUP source must be list");
                    }
                    HVal.HList srcList = (HVal.HList) src;
                    int bodyLen = op2;
                    int bodyStart = pc + 1;
                    int collectPc = bodyStart + bodyLen;
                    int[] collectInstr = instructions[collectPc];
                    int resultSlot = collectInstr[2];
                    int slotA = collectInstr[3];
                    int slotB = collectInstr[4];

                    ArrayList<HVal> elems = srcList.elements();
                    if (elems.isEmpty()) {
                        // Empty list — produce empty result or unchanged accumulator
                        if (mode == 2) { // FOLD
                            values[resultSlot] = values[slotA];
                            tags[resultSlot] = tags[slotA];
                        } else {
                            values[resultSlot] = new HVal.HList();
                            tags[resultSlot] = tags[op1];
                        }
                        pc = collectPc + 1;
                        continue;
                    }

                    IterState state = new IterState(mode, dest, bodyStart, bodyStart + bodyLen,
                                                     resultSlot, slotA, slotB, tags[op1], elems);
                    iterStack.push(state);

                    // Set up first element and jump into body
                    values[dest] = elems.get(0);
                    tags[dest] = tags[op1];
                    pc = bodyStart;
                    continue;
                }

                case ITER_COLLECT: {
                    IterState state = iterStack.peek();

                    // Process current element result
                    switch (state.mode) {
                        case 0: // MAP
                            state.result.add(values[state.slotA]);
                            break;
                        case 1: // FILTER
                        case 3: // MAP_FILTER
                            if (values[state.slotA].typeCode() == HVal.TYPE_BOOLEAN &&
                                    ((HVal.HBoolean) values[state.slotA]).value()) {
                                state.result.add(values[state.slotB]);
                            }
                            break;
                        case 2: // FOLD — accumulator already updated in slotA
                            break;
                    }

                    state.idx++;
                    if (state.idx < state.size) {
                        // More elements — set up next and loop back
                        values[state.dest] = state.elements.get(state.idx);
                        tags[state.dest] = state.srcTags;
                        pc = state.bodyStart;
                        continue;
                    }

                    // Done — collect and pop
                    iterStack.pop();
                    if (state.mode == 2) { // FOLD
                        values[state.resultSlot] = values[state.slotA];
                        tags[state.resultSlot] = tags[state.slotA];
                    } else {
                        values[state.resultSlot] = state.result;
                        tags[state.resultSlot] = state.srcTags;
                    }
                    break; // pc++ moves past ITER_COLLECT
                }

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
                    if (tagVal.typeCode() == HVal.TYPE_INTEGER) {
                        tags[dest] = ((HVal.HInteger) tagVal).value();
                    }
                    break;
                }

                case TAG_CHECK: {
                    HVal tagVal = packet.constants.get(op2);
                    long checkBits = (tagVal.typeCode() == HVal.TYPE_INTEGER) ?
                            ((HVal.HInteger) tagVal).value() : 0;
                    values[dest] = HVal.HBoolean.of((tags[op1] & checkBits) == checkBits);
                    applyTagMode(dest, tagMode, 0);
                    break;
                }

                // --- Superinstructions ---
                case RECORD_GET_C: {
                    HVal.HRecord rec = asRecord(op1);
                    String key = ((HVal.HString) packet.constants.get(op2)).value();
                    values[dest] = rec.get(key);
                    applyTagMode(dest, tagMode, tags[op1]);
                    break;
                }
                case RECORD_SET_C: {
                    HVal.HRecord rec = asRecord(dest);
                    String key = ((HVal.HString) packet.constants.get(op1)).value();
                    rec.set(key, values[op2]);
                    tags[dest] = tags[dest] | tags[op2];
                    break;
                }
                case RECORD_NEW_SET_C: {
                    HVal.HRecord rec = new HVal.HRecord();
                    String key = ((HVal.HString) packet.constants.get(op1)).value();
                    rec.set(key, values[op2]);
                    values[dest] = rec;
                    applyTagMode(dest, tagMode, tags[op2]);
                    break;
                }
                case STDLIB_CALL_1: {
                    if (stdLib == null) {
                        throw new HelunaException("StdLib not configured");
                    }
                    HVal result;
                    if (op1 == 0) {
                        result = values[op2];
                    } else {
                        stdlibArg1.clear();
                        stdlibArg1.set("value", values[op2]);
                        result = stdLib.call(op1, stdlibArg1);
                    }
                    values[dest] = result;
                    applyTagMode(dest, tagMode, tags[op2]);
                    break;
                }
                case CMP_JUMP_EQ:
                    if (!valEquals(values[op1], values[op2])) { pc = dest; continue; }
                    break;
                case CMP_JUMP_NEQ:
                    if (valEquals(values[op1], values[op2])) { pc = dest; continue; }
                    break;
                case CMP_JUMP_LT:
                    if (!(valCompare(values[op1], values[op2]) < 0)) { pc = dest; continue; }
                    break;
                case CMP_JUMP_GT:
                    if (!(valCompare(values[op1], values[op2]) > 0)) { pc = dest; continue; }
                    break;
                case CMP_JUMP_LTE:
                    if (!(valCompare(values[op1], values[op2]) <= 0)) { pc = dest; continue; }
                    break;
                case CMP_JUMP_GTE:
                    if (!(valCompare(values[op1], values[op2]) >= 0)) { pc = dest; continue; }
                    break;
                case IS_NOTHING_JUMP:
                    if (values[op1].isNothing()) { pc = dest; continue; }
                    break;

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

        byte leftType = left.typeCode();
        byte rightType = right.typeCode();
        boolean leftInt = leftType == HVal.TYPE_INTEGER;
        boolean leftFloat = leftType == HVal.TYPE_FLOAT;
        boolean rightInt = rightType == HVal.TYPE_INTEGER;
        boolean rightFloat = rightType == HVal.TYPE_FLOAT;

        if (!((leftInt || leftFloat) && (rightInt || rightFloat))) {
            throw new HelunaException("Arithmetic requires numeric operands, got "
                    + typeName(left) + " " + op + " " + typeName(right));
        }

        if (leftInt && rightInt) {
            long a = ((HVal.HInteger) left).value();
            long b = ((HVal.HInteger) right).value();
            switch (op) {
                case '+': values[dest] = HVal.HInteger.of(a + b); break;
                case '-': values[dest] = HVal.HInteger.of(a - b); break;
                case '*': values[dest] = HVal.HInteger.of(a * b); break;
                case '/':
                    if (b == 0) throw new HelunaException("Division by zero");
                    values[dest] = HVal.HInteger.of(a / b);
                    break;
                case '%':
                    if (b == 0) throw new HelunaException("Division by zero");
                    values[dest] = HVal.HInteger.of(a % b);
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
        byte ta = a.typeCode(), tb = b.typeCode();
        if (ta == HVal.TYPE_INTEGER && tb == HVal.TYPE_FLOAT) {
            return (double) ((HVal.HInteger) a).value() == ((HVal.HFloat) b).value();
        }
        if (ta == HVal.TYPE_FLOAT && tb == HVal.TYPE_INTEGER) {
            return ((HVal.HFloat) a).value() == (double) ((HVal.HInteger) b).value();
        }
        if (ta != tb) return false;
        return a.equals(b);
    }

    private int valCompare(HVal a, HVal b) {
        byte ta = a.typeCode(), tb = b.typeCode();
        boolean aNum = (ta == HVal.TYPE_INTEGER || ta == HVal.TYPE_FLOAT);
        boolean bNum = (tb == HVal.TYPE_INTEGER || tb == HVal.TYPE_FLOAT);
        if (aNum && bNum) {
            double da = ta == HVal.TYPE_INTEGER ? (double) ((HVal.HInteger) a).value()
                                                : ((HVal.HFloat) a).value();
            double db = tb == HVal.TYPE_INTEGER ? (double) ((HVal.HInteger) b).value()
                                                : ((HVal.HFloat) b).value();
            return Double.compare(da, db);
        }
        if (ta == HVal.TYPE_STRING && tb == HVal.TYPE_STRING) {
            return ((HVal.HString) a).value().compareTo(((HVal.HString) b).value());
        }
        throw new HelunaException("Cannot compare " + typeName(a) + " with " + typeName(b));
    }

    private boolean asBool(int slot) {
        HVal v = values[slot];
        if (v.typeCode() == HVal.TYPE_BOOLEAN) return ((HVal.HBoolean) v).value();
        throw new HelunaException("Expected boolean at slot " + slot + ", got " + typeName(v));
    }

    private String asString(int slot) {
        HVal v = values[slot];
        if (v.typeCode() == HVal.TYPE_STRING) return ((HVal.HString) v).value();
        throw new HelunaException("Expected string at slot " + slot + ", got " + typeName(v));
    }

    private HVal.HRecord asRecord(int slot) {
        HVal v = values[slot];
        if (v.typeCode() == HVal.TYPE_RECORD) return (HVal.HRecord) v;
        throw new HelunaException("Expected record at slot " + slot + ", got " + typeName(v));
    }

    private HVal.HList asList(int slot) {
        HVal v = values[slot];
        if (v.typeCode() == HVal.TYPE_LIST) return (HVal.HList) v;
        throw new HelunaException("Expected list at slot " + slot + ", got " + typeName(v));
    }

    static String valToString(HVal v) {
        switch (v.typeCode()) {
            case HVal.TYPE_STRING:  return ((HVal.HString) v).value();
            case HVal.TYPE_INTEGER: return Long.toString(((HVal.HInteger) v).value());
            case HVal.TYPE_FLOAT: {
                double d = ((HVal.HFloat) v).value();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return Long.toString((long) d) + ".0";
                }
                return Double.toString(d);
            }
            case HVal.TYPE_BOOLEAN: return ((HVal.HBoolean) v).value() ? "true" : "false";
            case HVal.TYPE_NOTHING: return "nothing";
            default: return v.toString();
        }
    }

    private HVal toInteger(HVal v) {
        switch (v.typeCode()) {
            case HVal.TYPE_INTEGER: return v;
            case HVal.TYPE_FLOAT:   return HVal.HInteger.of((long) ((HVal.HFloat) v).value());
            case HVal.TYPE_STRING: {
                try {
                    return HVal.HInteger.of(Long.parseLong(((HVal.HString) v).value()));
                } catch (NumberFormatException e) {
                    try {
                        return HVal.HInteger.of((long) Double.parseDouble(((HVal.HString) v).value()));
                    } catch (NumberFormatException e2) {
                        throw new HelunaException("Cannot convert string to integer: " + v);
                    }
                }
            }
            case HVal.TYPE_BOOLEAN: return HVal.HInteger.of(((HVal.HBoolean) v).value() ? 1 : 0);
            default: throw new HelunaException("Cannot convert " + typeName(v) + " to integer");
        }
    }

    private HVal toFloat(HVal v) {
        switch (v.typeCode()) {
            case HVal.TYPE_FLOAT:   return v;
            case HVal.TYPE_INTEGER: return new HVal.HFloat((double) ((HVal.HInteger) v).value());
            case HVal.TYPE_STRING: {
                try {
                    return new HVal.HFloat(Double.parseDouble(((HVal.HString) v).value()));
                } catch (NumberFormatException e) {
                    throw new HelunaException("Cannot convert string to float: " + v);
                }
            }
            default: throw new HelunaException("Cannot convert " + typeName(v) + " to float");
        }
    }

    private HVal toBool(HVal v) {
        switch (v.typeCode()) {
            case HVal.TYPE_BOOLEAN: return v;
            case HVal.TYPE_INTEGER: return HVal.HBoolean.of(((HVal.HInteger) v).value() != 0);
            case HVal.TYPE_FLOAT:   return HVal.HBoolean.of(((HVal.HFloat) v).value() != 0.0);
            case HVal.TYPE_STRING:  return HVal.HBoolean.of(!((HVal.HString) v).value().isEmpty());
            case HVal.TYPE_NOTHING: return HVal.HBoolean.FALSE;
            default: return HVal.HBoolean.TRUE;
        }
    }

    static String typeName(HVal v) {
        switch (v.typeCode()) {
            case HVal.TYPE_STRING:  return "string";
            case HVal.TYPE_INTEGER: return "integer";
            case HVal.TYPE_FLOAT:   return "float";
            case HVal.TYPE_BOOLEAN: return "boolean";
            case HVal.TYPE_NOTHING: return "nothing";
            case HVal.TYPE_LIST:    return "list";
            case HVal.TYPE_RECORD:  return "record";
            default: return "unknown";
        }
    }
}
