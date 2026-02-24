# heluna_jvm
A Java implementation of the Heluna vm specification

## Benchmark Results

Run on Java 15.0.2, 2026-02-24. See [benchmark suite](../heluna_language/benchmark/README.md) for protocol details.

| Benchmark | Records | Iterations | Mean | Median | P99 | Min | Max |
|-----------|--------:|----------:|---------:|---------:|--------:|--------:|--------:|
| arithmetic-tiny | 1 | 50,000 | 0.003 ms | 0.003 ms | 0.01 ms | 0.002 ms | 15.01 ms |
| arithmetic-medium | 10,000 | 1,000 | 3.26 ms | 2.83 ms | 5.83 ms | 2.59 ms | 13.53 ms |
| strings-medium | 10,000 | 1,000 | 23.60 ms | 23.30 ms | 27.23 ms | 21.32 ms | 29.87 ms |
| lists-medium | 10,000 | 1,000 | 7.16 ms | 6.95 ms | 10.41 ms | 5.98 ms | 15.05 ms |
| mixed-medium | 10,000 | 1,000 | 11.30 ms | 11.07 ms | 16.08 ms | 9.12 ms | 24.05 ms |
| mixed-large | 100,000 | 100 | 197.67 ms | 196.69 ms | 210.29 ms | 192.10 ms | 229.74 ms |

### Native Java Baseline (VM Overhead)

Native baselines implement the same JSON transformations in plain Java (no HVal, no bytecode interpreter) to measure VM overhead.

| Benchmark | VM Median | Native Median | Overhead |
|-----------|----------:|--------------:|---------:|
| arithmetic-medium | 3.25 ms | 0.85 ms | 3.8x |
| strings-medium | 24.49 ms | 13.25 ms | 1.8x |
| lists-medium | 7.82 ms | 4.43 ms | 1.8x |
| mixed-medium | 11.76 ms | 4.88 ms | 2.4x |

### Running benchmarks

```bash
# Generate test data (one-time)
cd ../heluna_language/benchmark/data && python3 generate.py && cd -

# Run all benchmarks
java -cp target/classes io.heluna.vm.BenchmarkRunner \
  --spec ../heluna_language/benchmark/benchmark-spec.json \
  --benchmark-dir ../heluna_language/benchmark/

# Run a subset
java -cp target/classes io.heluna.vm.BenchmarkRunner \
  --spec ../heluna_language/benchmark/benchmark-spec.json \
  --benchmark-dir ../heluna_language/benchmark/ \
  --filter arithmetic

# Skip native baselines
java -cp target/classes io.heluna.vm.BenchmarkRunner \
  --spec ../heluna_language/benchmark/benchmark-spec.json \
  --benchmark-dir ../heluna_language/benchmark/ \
  --skip-native
```
