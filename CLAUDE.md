# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Java VM implementation for the Heluna language — a pure functional language for safe JSON transformations. The VM executes compiled Heluna packets (`.hlna` binary files) that take JSON input and produce JSON output with zero side effects.

The language specification lives at: https://github.com/kasperjj/heluna_language

Key spec documents:
- `heluna-vm-spec.md` — VM and packet format specification (primary reference)
- `heluna-llm-context.md` — Language overview, grammar, stdlib, and examples
- `stdlib.md` — Standard library function reference

## Build Commands

```bash
mvn compile          # Compile the project
mvn test             # Run all tests
mvn test -Dtest=ClassName            # Run a single test class
mvn test -Dtest=ClassName#methodName # Run a single test method
mvn package          # Build JAR
mvn clean install    # Full clean build
```

## Heluna VM Architecture

### Packet Format

Packets are self-contained binaries with an 88-byte header (magic `0x484C4E41` / "HLNA"), Ed25519 signature, and a section directory. All multi-byte integers are little-endian. Required sections: CONTRACT (0x0001), CONSTANTS (0x0002), STDLIB_DEPS (0x0003), BYTECODE (0x0004). Optional sections provide optimization hints (type info, liveness, dependency graphs, etc.).

### Execution Model

1. Host provides input JSON record and a logical timestamp
2. VM allocates a flat **scratchpad** — array of typed slots (JSON value + 64-bit tag bitfield each), size declared in the contract section
3. Input fields are mapped to scratchpad offsets
4. Bytecode executes: all instructions are fixed 8-byte width (opcode | flags | dest | operand1 | operand2)
5. Output is constructed from designated scratchpad slots
6. Output validated against contract rules (tag constraints, forbid/require rules)
7. Result serialized as JSON

No call stack, no recursion, no heap allocation. All iteration is bounded by input list sizes. Deterministic execution — identical inputs always produce identical outputs.

### Instruction Set

Every instruction is 8 bytes. The flags byte encodes a type hint (bits 0-2) and tag mode (bits 3-4: PROPAGATE/CLEAR/SET). Major opcode groups:

- **0x01–0x04**: Scratchpad ops (LOAD_CONST, LOAD_FIELD, LOAD_NOTHING, COPY)
- **0x10–0x15**: Arithmetic (ADD, SUB, MUL, DIV, MOD, NEGATE)
- **0x20–0x25**: Comparison (EQ, NEQ, LT, GT, LTE, GTE)
- **0x30–0x32**: Boolean logic (AND, OR, NOT)
- **0x40**: String (STR_CONCAT)
- **0x50–0x56**: Type testing (IS_STRING through IS_RECORD)
- **0x58–0x5B**: Type conversion (TO_STRING, TO_INT, TO_FLOAT, TO_BOOL)
- **0x60–0x63**: Record ops (RECORD_NEW, RECORD_SET, RECORD_GET, RECORD_HAS)
- **0x70–0x73**: List ops (LIST_NEW, LIST_APPEND, LIST_GET, LIST_LENGTH)
- **0x80–0x82**: Control flow (JUMP, JUMP_IF, JUMP_IF_NOT) — targets are instruction indices
- **0x85**: COALESCE (nothing/maybe handling)
- **0x90–0x91**: Iteration (ITER_SETUP, ITER_COLLECT) — modes: MAP, FILTER, FOLD, MAP_FILTER
- **0xA0**: STDLIB_CALL (dispatch by function ID)
- **0xB0–0xB1**: Tag ops (TAG_SET, TAG_CHECK)

### Tag/Security System

Tags are 64-bit bitfields that propagate via bitwise OR through all operations (default PROPAGATE mode). Tags can only be removed through declared sanitizers. Output validation enforces forbid-tagged rules at the output boundary.

### Standard Library

55 functions across 9 categories (string 0x0001–0x0011, numeric 0x0020–0x0026, list 0x0030–0x0037, record 0x0040–0x0044, date/time 0x0050–0x0054, encoding 0x0060–0x0065, crypto 0x0070–0x0072, conversion 0x0074–0x0076, iteration 0x0078). All take a single record argument. The STDLIB_DEPS section lists which functions a packet requires — VM must support all or reject the packet.

### Type System

Values are: string (0x01), integer (0x02), float (0x03), boolean (0x04), nothing (0x05), maybe (0x06), list (0x07), record (0x08). Constants use 8-byte signed LE integers, IEEE 754 doubles, raw UTF-8 strings.

### Conformance Levels

- **Level 1 (Minimal)**: All required sections, all opcodes, all stdlib functions — correct outputs
- **Level 2 (Optimizing)**: Level 1 + uses optional sections for performance
- **Level 3 (Full)**: All optional sections supported
