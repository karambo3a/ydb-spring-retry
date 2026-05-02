package tech.ydb.slo;

import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtelConfig {

    @Bean(destroyMethod = "close")
    public OpenTelemetrySdk openTelemetry() {
        PrometheusHttpServer prometheusHttpServer = PrometheusHttpServer.builder()
                .setPort(9464)
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(prometheusHttpServer)
                .build();

        return OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
    }
}
