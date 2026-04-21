package tech.ydb.slo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = {"tech.ydb.slo", "tech.ydb.retry"})
public class SloWorkloadApp {

    private static final Logger log = LoggerFactory.getLogger(SloWorkloadApp.class);
    private static final String TABLE_NAME = "slo_test_table";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        SloConfig.ParsedArgs parsed;
        try {
            parsed = SloConfig.parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printUsage();
            System.exit(1);
            return;
        }

        System.setProperty("spring.datasource.url", "jdbc:ydb:" + parsed.connectionString());

        SpringApplication app = new SpringApplication(SloWorkloadApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext ctx = app.run(parsed.springArgs())) {
            SloService service = ctx.getBean(SloService.class);

            switch (parsed.command()) {
                case "create" -> {
                    log.info("Creating table '{}' with initial rows...", TABLE_NAME);
                    service.createTable(TABLE_NAME);
                    service.seedData(TABLE_NAME);
                    log.info("Done.");
                }
                case "run" -> {
                    SloConfig config = SloConfig.parse(parsed.connectionString(), parsed.options());
                    WorkloadRunner runner = ctx.getBean(WorkloadRunner.class);
                    log.info("Running workload: table={}, readRPS={}, writeRPS={}, time={}s",
                            TABLE_NAME, config.readRps(), config.writeRps(), config.time());
                    runner.run(config, TABLE_NAME);
                    log.info("Workload finished.");
                }
                case "cleanup" -> {
                    log.info("Dropping table '{}'...", TABLE_NAME);
                    service.dropTable(TABLE_NAME);
                    log.info("Done.");
                }
                default -> {
                    System.err.println("Unknown command: " + parsed.command());
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            log.error("Command failed", e);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: slo <command> <endpoint> <database> [options]");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  create   <endpoint> <database>   Create test table and seed data");
        System.err.println("  run      <endpoint> <database>   Run read/write workload");
        System.err.println("  cleanup  <endpoint> <database>   Drop test table");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  <endpoint>   YDB endpoint, e.g. grpc://localhost:2136");
        System.err.println("  <database>   YDB database path, e.g. /Root/testdb");
        System.err.println();
        System.err.println("Run options:");
        System.err.println("  --otlp-endpoint=<url>       OpenTelemetry OTLP endpoint URL (required)");
        System.err.println("  --read-rps=<n>              Target read RPS (required)");
        System.err.println("  --write-rps=<n>             Target write RPS (required)");
        System.err.println("  --time=<sec>                Run duration (required)");
        System.err.println("  --report-period=<ms>        Metrics export interval (default: 250)");
        System.err.println("  --read-timeout=<ms>         Read timeout (default: 10000)");
        System.err.println("  --write-timeout=<ms>        Write timeout (default: 10000)");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  slo create grpc://localhost:2136 /Root/testdb");
        System.err.println("  slo run grpc://localhost:2136 /Root/testdb \\");
        System.err.println("    --otlp-endpoint http://localhost:9090/api/v1/otlp/v1/metrics \\");
        System.err.println("    --read-rps 1000 --write-rps 100 --time 600");
        System.err.println("  slo cleanup grpc://localhost:2136 /Root/testdb");
    }
}
