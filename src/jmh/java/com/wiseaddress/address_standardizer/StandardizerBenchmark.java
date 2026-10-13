package com.wiseaddress.address_standardizer;

import com.wiseaddress.address_standardizer.exception.InvalidAddressException;
import com.wiseaddress.address_standardizer.model.Model;
import com.wiseaddress.address_standardizer.model.ModelFactory;
import com.wiseaddress.address_standardizer.us.USStandardizedAddress;
import com.wiseaddress.address_standardizer.us.USStandardizer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@State(Scope.Thread)
public class StandardizerBenchmark {
    private static final String[] COMMON_ADDRESSES = {
        "123 Main Street Springfield IL 62704",
        "789 North South Oak Street Drive # 123-A West Springfield 12345 6789"
    };
    private static final USStandardizedAddress[] COMMON_EXPECTED = {
        new USStandardizedAddress("123", "", "MAIN", "ST", "", "", "", "SPRINGFIELD", "IL", "62704"),
        new USStandardizedAddress("789", "N", "SOUTH OAK STREET", "DR", "", "#", "123-A", "WEST SPRINGFIELD", "", "12345-6789")
    };
    private static final String[][] ADDRESSES = {
        {"123 Main Street Springfield IL 62704", "123 MAIN ST SPRINGFIELD IL 62704"},
        {"1600 Pennsylvania Avenue NW, Washington DC 20500", "1600 PENNSYLVANIA AVE NW WASHINGTON DC 20500"},
        {"42 Oak Road Apt 7B, Boston MA 02108", "42 OAK RD APT 7B BOSTON MA 02108"},
        {"12 1/2 West Elm Street, Los Angeles CA 90012", "12 1/2 W ELM ST LOS ANGELES CA 90012"},
        {"PO Box 123, Springfield IL 62704", "PO BOX 123 SPRINGFIELD IL 62704"},
        {"RR 2 Box 152, Springfield IL 62704", "RR 2 BOX 152 SPRINGFIELD IL 62704"},
        {"HC 68 Box 23A, Springfield IL 62704", "HC 68 BOX 23A SPRINGFIELD IL 62704"},
        {"PSC 3 Box 4120, APO AE 09021", "PSC 3 BOX 4120 APO AE 09021"}
    };
    private static final String[] INVALID_ADDRESSES = {"", "not an address", "123"};

    private USStandardizer standardizer;
    private String modelJson;
    private int successIndex;
    private int invalidIndex;
    private int commonIndex;

    @Setup
    public void setup() throws IOException {
        standardizer = new USStandardizer();
        for (int i = 0; i < COMMON_ADDRESSES.length; i++) {
            if (!standardizer.standardize(COMMON_ADDRESSES[i]).equals(COMMON_EXPECTED[i])) {
                throw new IllegalStateException("Historical comparison address parsed incorrectly: " + COMMON_ADDRESSES[i]);
            }
        }
        for (String[] address : ADDRESSES) {
            String actual = standardizer.standardize(address[0]).format();
            if (!actual.equals(address[1])) {
                throw new IllegalStateException("Benchmark address parsed incorrectly: " + address[0] + " -> " + actual);
            }
        }
        for (String address : INVALID_ADDRESSES) {
            try {
                standardizer.standardize(address);
                throw new IllegalStateException("Benchmark invalid address was accepted: " + address);
            } catch (InvalidAddressException expected) {
                // Validate rejection outside the measured path.
            }
        }
        try (InputStream resource = ModelFactory.class.getResourceAsStream("/com/wiseaddress/address_standardizer/usdata.json")) {
            if (resource == null) {
                throw new IllegalStateException("Bundled model resource is missing");
            }
            modelJson = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Benchmark
    public USStandardizedAddress commonParsing() {
        String address = COMMON_ADDRESSES[commonIndex];
        commonIndex = (commonIndex + 1) % COMMON_ADDRESSES.length;
        return standardizer.standardize(address);
    }

    @Benchmark
    public USStandardizedAddress successfulParsing() {
        String address = ADDRESSES[successIndex][0];
        successIndex = (successIndex + 1) % ADDRESSES.length;
        return standardizer.standardize(address);
    }

    @Benchmark
    public String invalidParsing() {
        String address = INVALID_ADDRESSES[invalidIndex];
        invalidIndex = (invalidIndex + 1) % INVALID_ADDRESSES.length;
        try {
            standardizer.standardize(address);
            throw new IllegalStateException("Invalid benchmark address was accepted");
        } catch (InvalidAddressException expected) {
            return expected.getMessage();
        }
    }

    @Benchmark
    public USStandardizer cachedConstruction() {
        return new USStandardizer();
    }

    @Benchmark
    public Model coldModelParsing() {
        return ModelFactory.parse(modelJson);
    }
}
