package tech.ydb.slo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slo")
public class SloConfig {

    private int readRps = 100;
    private int writeRps = 100;
    private int initialDataCount = 1000;
    private int runTimeSeconds = 600;
    private String ref = "unknown";

    public int getReadRps() {
        return readRps;
    }

    public void setReadRps(int readRps) {
        this.readRps = readRps;
    }

    public int getWriteRps() {
        return writeRps;
    }

    public void setWriteRps(int writeRps) {
        this.writeRps = writeRps;
    }

    public int getInitialDataCount() {
        return initialDataCount;
    }

    public void setInitialDataCount(int initialDataCount) {
        this.initialDataCount = initialDataCount;
    }

    public int getRunTimeSeconds() {
        return runTimeSeconds;
    }

    public void setRunTimeSeconds(int runTimeSeconds) {
        this.runTimeSeconds = runTimeSeconds;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }
}
