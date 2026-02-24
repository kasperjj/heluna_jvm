package io.heluna.vm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Cross-VM benchmark runner for the Heluna VM.
 *
 * Reads a benchmark-spec.json, loads packets and data, runs warmup + measured
 * iterations, and outputs JSON results to stdout.
 *
 * Usage:
 *   java -cp target/classes io.heluna.vm.BenchmarkRunner \
 *     --spec ../heluna_language/benchmark/benchmark-spec.json \
 *     --benchmark-dir ../heluna_language/benchmark/
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        String specPath = null;
        String benchmarkDir = null;
        String filter = null;
        boolean skipNative = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--spec":
                    specPath = args[++i];
                    break;
                case "--benchmark-dir":
                    benchmarkDir = args[++i];
                    break;
                case "--filter":
                    filter = args[++i];
                    break;
                case "--skip-native":
                    skipNative = true;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (specPath == null || benchmarkDir == null) {
            printUsage();
            System.exit(1);
        }

        Path specFile = Paths.get(specPath);
        Path baseDir = Paths.get(benchmarkDir);

        String specJson = new String(Files.readAllBytes(specFile));
        HVal specVal = StdLib.parseJsonValue(specJson, new int[]{0});
        if (!(specVal instanceof HVal.HRecord)) {
            System.err.println("benchmark-spec.json must be a JSON object");
            System.exit(1);
        }
        HVal.HRecord spec = (HVal.HRecord) specVal;

        HVal benchmarksVal = spec.get("benchmarks");
        if (!(benchmarksVal instanceof HVal.HList)) {
            System.err.println("benchmark-spec.json must contain a 'benchmarks' array");
            System.exit(1);
        }
        HVal.HList benchmarks = (HVal.HList) benchmarksVal;

        String timestamp = "2024-01-15T10:30:00Z";
        List<String> resultEntries = new ArrayList<>();

        for (int i = 0; i < benchmarks.size(); i++) {
            HVal.HRecord bench = (HVal.HRecord) benchmarks.elements().get(i);
            String name = ((HVal.HString) bench.get("name")).value();

            if (filter != null && !name.contains(filter)) {
                continue;
            }

            String packetPath = ((HVal.HString) bench.get("packet")).value();
            String dataPath = ((HVal.HString) bench.get("data")).value();
            int warmupCount = (int) ((HVal.HInteger) bench.get("warmup")).value();
            int iterations = (int) ((HVal.HInteger) bench.get("iterations")).value();

            System.err.println("Running: " + name + " (" + warmupCount + " warmup, " + iterations + " iterations)");

            // Load packet once
            byte[] packetBytes = Files.readAllBytes(baseDir.resolve(packetPath));
            Packet pkt = HelunaVM.load(packetBytes);

            // Load and parse data once
            String dataJson = new String(Files.readAllBytes(baseDir.resolve(dataPath)));
            HVal dataVal = StdLib.parseJsonValue(dataJson, new int[]{0});
            HVal.HRecord inputRecord = (HVal.HRecord) dataVal;

            // --- VM Benchmark ---
            Runnable vmTask = () -> HelunaVM.execute(pkt, inputRecord, timestamp);
            doWarmup(vmTask, warmupCount);

            HVal.HRecord firstOutput = HelunaVM.execute(pkt, inputRecord, timestamp);
            String firstOutputJson = StdLib.toJson(firstOutput);
            String outputSha256 = sha256(firstOutputJson);

            long[] times = doMeasure(vmTask, iterations);
            double[] stats = computeStats(times);

            System.err.printf("  %s: mean=%.2fms median=%.2fms p99=%.2fms min=%.2fms max=%.2fms%n",
                    name, stats[1], stats[2], stats[3], stats[4], stats[5]);

            resultEntries.add(formatResult(name, iterations, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5], outputSha256));

            // --- Native Baseline ---
            if (!skipNative && NativeBenchmarks.hasNative(packetPath)) {
                System.err.println("Running native: " + name);

                @SuppressWarnings("unchecked")
                Map<String, Object> nativeInput = (Map<String, Object>) NativeBenchmarks.parseJson(dataJson);
                Runnable nativeTask = () -> NativeBenchmarks.execute(packetPath, nativeInput);
                doWarmup(nativeTask, warmupCount);

                Map<String, Object> nativeOutput = NativeBenchmarks.execute(packetPath, nativeInput);
                String nativeOutputJson = NativeBenchmarks.toJson(nativeOutput);
                String nativeSha256 = sha256(nativeOutputJson);

                if (!nativeSha256.equals(outputSha256)) {
                    System.err.println("  WARNING: native SHA-256 mismatch!");
                    System.err.println("    VM:     " + outputSha256);
                    System.err.println("    Native: " + nativeSha256);
                }

                long[] nativeTimes = doMeasure(nativeTask, iterations);
                double[] nativeStats = computeStats(nativeTimes);

                System.err.printf("  %s-native: mean=%.2fms median=%.2fms p99=%.2fms min=%.2fms max=%.2fms%n",
                        name, nativeStats[1], nativeStats[2], nativeStats[3], nativeStats[4], nativeStats[5]);

                double overhead = stats[2] / nativeStats[2];
                System.err.printf("  overhead: %.1fx%n", overhead);

                String nativeName = name + "-native";
                resultEntries.add(formatResult(nativeName, iterations, nativeStats[0], nativeStats[1], nativeStats[2], nativeStats[3], nativeStats[4], nativeStats[5], nativeSha256));
            }
        }

        // Output JSON results to stdout
        String javaVersion = System.getProperty("java.version");
        String timestampNow = Instant.now().toString();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"vm\":\"java\",\"java_version\":\"").append(escapeJson(javaVersion)).append("\"");
        sb.append(",\"timestamp\":\"").append(escapeJson(timestampNow)).append("\"");
        sb.append(",\"results\":[");
        for (int i = 0; i < resultEntries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(resultEntries.get(i));
        }
        sb.append("]}");

        System.out.println(sb.toString());
    }

    private static void doWarmup(Runnable task, int count) {
        for (int i = 0; i < count; i++) {
            task.run();
        }
    }

    private static long[] doMeasure(Runnable task, int iterations) {
        long[] times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            task.run();
            times[i] = System.nanoTime() - start;
        }
        return times;
    }

    private static double[] computeStats(long[] times) {
        Arrays.sort(times);
        int n = times.length;
        long totalNs = 0;
        for (long t : times) totalNs += t;
        double totalMs = totalNs / 1_000_000.0;
        double meanMs = totalMs / n;
        double medianMs = medianNanos(times) / 1_000_000.0;
        double p99Ms = percentileNanos(times, 99) / 1_000_000.0;
        double minMs = times[0] / 1_000_000.0;
        double maxMs = times[n - 1] / 1_000_000.0;
        return new double[]{totalMs, meanMs, medianMs, p99Ms, minMs, maxMs};
    }

    private static double medianNanos(long[] sorted) {
        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            return sorted[n / 2];
        }
    }

    private static double percentileNanos(long[] sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.length) idx = sorted.length - 1;
        return sorted[idx];
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String formatResult(String name, int iterations, double totalMs,
                                        double meanMs, double medianMs, double p99Ms,
                                        double minMs, double maxMs, String outputSha256) {
        return String.format(
                "{\"name\":\"%s\",\"iterations\":%d,\"total_ms\":%.1f,\"mean_ms\":%.2f," +
                "\"median_ms\":%.2f,\"p99_ms\":%.2f,\"min_ms\":%.2f,\"max_ms\":%.2f," +
                "\"output_sha256\":\"%s\"}",
                escapeJson(name), iterations, totalMs, meanMs, medianMs, p99Ms, minMs, maxMs, escapeJson(outputSha256)
        );
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void printUsage() {
        System.err.println("Usage: java io.heluna.vm.BenchmarkRunner --spec <path> --benchmark-dir <path> [--filter <name>] [--skip-native]");
    }
}
