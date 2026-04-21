package tech.ydb.slo;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.lang.Nullable;

public class Metrics {

    private static Meter meter;
    private static LongCounter operationsTotal;
    private static LongCounter operationsSuccessTotal;
    private static DoubleHistogram operationLatencySeconds;
    private static LongUpDownCounter pendingOperations;

    private static SdkMeterProvider meterProvider;

    private static final Map<String, AtomicLong> okCounts = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> notOkCounts = new ConcurrentHashMap<>();

    private Metrics() {
    }

    public static synchronized void init(String otlpEndpoint, int reportPeriodMs) {
        Resource resource = Resource.create(Attributes.builder()
                .build());

        OtlpHttpMetricExporter exporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();

        PeriodicMetricReader reader = PeriodicMetricReader.builder(exporter)
                .setInterval(reportPeriodMs, TimeUnit.MILLISECONDS)
                .build();

        meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(reader)
                .build();

        OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        meter = GlobalOpenTelemetry.getMeterProvider().get("ydb-retry-slo");

        operationsTotal = meter.counterBuilder("operations.total")
                .setDescription("Total number of operations performed by the SDK, categorized by type.")
                .build();

        operationsSuccessTotal = meter.counterBuilder("operations.success.total")
                .setDescription("Total number of successful operations, categorized by type.")
                .build();

        operationLatencySeconds = meter.histogramBuilder("operation.latency.seconds")
                .setUnit("s")
                .setDescription("Latency of operations in seconds, categorized by type and status.")
                .setExplicitBucketBoundariesAdvice(Arrays.asList(
                        0.001, 0.002, 0.003, 0.004, 0.005, 0.0075,
                        0.010, 0.020, 0.050, 0.100, 0.200, 0.500, 1.000
                ))
                .build();

        pendingOperations = meter.upDownCounterBuilder("pending.operations")
                .setDescription("Current number of pending operations, categorized by type.")
                .build();
    }

    private static Attributes tags(String operationType) {
        return tags(operationType, null);
    }

    private static Attributes tags(String operationType, @Nullable String status) {
        var builder = Attributes.builder()
                .put("operation_type", operationType);
        if (status != null) {
            builder.put("operation_status", status);
        }
        return builder.build();
    }

    public static void recordOk(String operationType, long elapsedNanos) {
        okCounts.computeIfAbsent(operationType, k -> new AtomicLong()).incrementAndGet();

        operationsTotal.add(1, tags(operationType));
        operationsSuccessTotal.add(1, tags(operationType));
        operationLatencySeconds.record(elapsedNanos / 1_000_000_000.0, tags(operationType, "success"));
    }

    public static void recordNotOk(String operationType, long elapsedNanos) {
        notOkCounts.computeIfAbsent(operationType, k -> new AtomicLong()).incrementAndGet();

        operationsTotal.add(1, tags(operationType));
        operationLatencySeconds.record(elapsedNanos / 1_000_000_000.0, tags(operationType, "failure"));
    }

    public static void inflightInc(String operationType) {
        pendingOperations.add(1, tags(operationType));
    }

    public static void inflightDec(String operationType) {
        pendingOperations.add(-1, tags(operationType));
    }

    public static void shutdown() {
        if (meterProvider != null) {
            meterProvider.shutdown();
        }
    }

    public static long getOks(String operationType) {
        AtomicLong t = okCounts.get(operationType);
        return t != null ? t.get() : 0;
    }

    public static long getNotOks(String operationType) {
        AtomicLong t = notOkCounts.get(operationType);
        return t != null ? t.get() : 0;
    }
}
