package io.heluna.vm;

/**
 * Entry point for the Heluna Virtual Machine.
 * Loads compiled packets and executes them with JSON input.
 */
public class HelunaVM {

    public static final int PACKET_MAGIC = 0x484C4E41; // "HLNA"
    public static final int FORMAT_VERSION = 1;

    /**
     * Load a compiled Heluna packet from raw bytes.
     */
    public static Packet load(byte[] data) {
        return PacketLoader.load(data);
    }

    /**
     * Execute a loaded packet with the given input record and timestamp.
     * Returns the output record.
     */
    public static HVal.HRecord execute(Packet pkt, HVal.HRecord input, String timestamp) {
        Executor exec = new Executor(pkt);

        StdLib stdLib = new StdLib();
        stdLib.setTimestamp(timestamp);
        exec.setStdLib(stdLib);

        // Map input fields to scratchpad slots
        for (Packet.FieldDef field : pkt.inputFields) {
            HVal value = input.get(field.name);
            int slot = field.scratchpadOffset;
            exec.setSlot(slot, value, field.tagBits);
        }

        // Execute bytecode
        exec.execute();

        // Collect output from scratchpad
        // The bytecode builds the output as a single record at the slot
        // immediately after all declared field offsets (inputs + outputs).
        int outputSlot = pkt.inputFieldCount + pkt.outputFieldCount;
        HVal outputVal = exec.getSlot(outputSlot);
        HVal.HRecord output;
        if (outputVal instanceof HVal.HRecord) {
            output = (HVal.HRecord) outputVal;
        } else {
            // Fallback: build output from individual scratchpad slots
            output = new HVal.HRecord();
            for (Packet.FieldDef field : pkt.outputFields) {
                HVal value = exec.getSlot(field.scratchpadOffset);
                output.set(field.name, value);
            }
        }

        // Validate output against contract rules
        validateOutput(pkt, exec, output, outputSlot);

        return output;
    }

    private static void validateOutput(Packet pkt, Executor exec,
                                        HVal.HRecord output, int outputSlot) {
        for (Packet.Rule rule : pkt.rules) {
            switch (rule.type) {
                case Packet.Rule.FORBID_TAGGED: {
                    // Check if the output record carries forbidden tags
                    long outputTags = exec.getTag(outputSlot);
                    if ((outputTags & rule.tagBits) != 0) {
                        throw new HelunaException("Output contains forbidden tags: 0x"
                                + Long.toHexString(outputTags & rule.tagBits));
                    }
                    break;
                }
                // REQUIRE and MATCH rules are enforced by the bytecode itself
                // at runtime. The rule declarations are metadata for tooling.
                case Packet.Rule.REQUIRE:
                case Packet.Rule.MATCH:
                case Packet.Rule.FORBID_FIELD:
                    break;
            }
        }
    }

    /**
     * Execute a loaded packet with the given JSON input string and timestamp.
     * Returns the output as a JSON string.
     */
    public static String executeJson(Packet pkt, String inputJson, String timestamp) {
        HVal input = StdLib.parseJsonValue(inputJson, new int[]{0});
        if (!(input instanceof HVal.HRecord)) {
            throw new HelunaException("Input must be a JSON object, got: " + Executor.typeName(input));
        }
        HVal.HRecord output = execute(pkt, (HVal.HRecord) input, timestamp);
        return StdLib.toJson(output);
    }
}
