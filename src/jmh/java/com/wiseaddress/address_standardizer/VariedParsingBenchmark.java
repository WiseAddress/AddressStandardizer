package com.wiseaddress.address_standardizer;

import com.wiseaddress.address_standardizer.us.USAddressType;
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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@State(Scope.Thread)
public class VariedParsingBenchmark {
    private record AddressCase(String input, USStandardizedAddress expected) {}

    private static final AddressCase[] ADDRESSES = createAddresses();
    private USStandardizer standardizer;
    private int index;

    @Setup
    public void setup() {
        standardizer = new USStandardizer();
        for (AddressCase address : ADDRESSES) {
            USStandardizedAddress actual = standardizer.standardize(address.input());
            if (!actual.equals(address.expected())) {
                throw new IllegalStateException("Invalid benchmark fixture: " + address.input()
                    + "\nExpected: " + address.expected() + "\nActual: " + actual);
            }
        }
    }

    @Benchmark
    public USStandardizedAddress variedParsing() {
        String input = ADDRESSES[index].input();
        index = (index + 1) % ADDRESSES.length;
        return standardizer.standardize(input);
    }

    private static AddressCase[] createAddresses() {
        AddressCase[] addresses = new AddressCase[1024];
        String[] militaryUnits = {"PSC", "CMR", "UNIT", "CPR", "OPC", "UPR"};
        String[] militaryCities = {"APO", "FPO", "DPO"};
        String[] militaryStates = {"AE", "AP", "AA"};
        for (int i = 0; i < addresses.length; i++) {
            // Keep generated name labels distinct from Pub 28 suffix aliases such as BG and CT.
            String label = "TEST" + letters(i);
            String house = Integer.toString(100 + i);
            String city = "TESTVILLE " + label;
            String zip = Integer.toString(60000 + i);
            String route = Integer.toString(1 + i % 97);
            String box = String.format(Locale.ROOT, "%05d", 100 + i);
            addresses[i] = switch (i % 8) {
                case 0 -> street(house + " North Alder " + label + " Street, " + city + " Illinois " + zip,
                    house, "N", "ALDER " + label, "ST", "", "", "", city, "IL", zip);
                case 1 -> {
                    String number = house + (i % 3 == 0 ? " 3/4" : " 1/2");
                    yield street(number + " Birch " + label + " Avenue NW, " + city + " CA " + zip,
                        number, "", "BIRCH " + label, "AVE", "NW", "", "", city, "CA", zip);
                }
                case 2 -> {
                    String unit = "A-" + (i + 1);
                    yield street(house + " South East Cedar " + label + " Road Suite " + unit + ", " + city + " TX " + zip,
                        house, "SE", "CEDAR " + label, "RD", "", "STE", unit, city, "TX", zip);
                }
                case 3 -> {
                    String highway = Integer.toString(20 + i);
                    yield street(house + " US Highway " + highway + " West, " + city + " TX " + zip,
                        house, "", "US HIGHWAY " + highway, "", "W", "", "", city, "TX", zip);
                }
                case 4 -> special("Post Office Box " + box + ", " + city + " MA " + zip,
                    USAddressType.PO_BOX, "", box, "", city, "MA", zip);
                case 5 -> {
                    String ruralBox = box + "A";
                    yield special("Rural Route 00" + route + " Box " + ruralBox + ", " + city + " IL " + zip,
                        USAddressType.RURAL_ROUTE, route, ruralBox, "", city, "IL", zip);
                }
                case 6 -> {
                    String contractRoute = route + "A";
                    String contractBox = box + "-1B";
                    yield special("HC 00" + contractRoute + " Box " + contractBox + ", " + city + " IA " + zip,
                        USAddressType.HIGHWAY_CONTRACT, contractRoute, contractBox, "", city, "IA", zip);
                }
                default -> {
                    int sample = i / 8;
                    String rawUnit = militaryUnits[sample % militaryUnits.length];
                    String unit = rawUnit.equals("CMR") ? "CPR" : rawUnit;
                    String unitNumber = String.format(Locale.ROOT, "%04d", i);
                    String militaryCity = militaryCities[sample % militaryCities.length];
                    String state = militaryStates[sample % militaryStates.length];
                    String postalCode = String.format(Locale.ROOT, "%05d", 9000 + i);
                    yield special(rawUnit + " " + unitNumber + " BOX " + box + ", " + militaryCity + " " + state + " " + postalCode,
                        USAddressType.MILITARY, unitNumber, box, unit, militaryCity, state, postalCode);
                }
            };
        }
        return addresses;
    }

    private static AddressCase street(String input, String number, String pre, String name, String suffix,
                                      String post, String unit, String range, String city, String state, String zip) {
        return new AddressCase(input, new USStandardizedAddress(number, pre, name, suffix, post, unit, range,
            city, state, zip, USAddressType.STREET, "", "", ""));
    }

    private static AddressCase special(String input, USAddressType type, String route, String box, String unit,
                                       String city, String state, String zip) {
        return new AddressCase(input, new USStandardizedAddress("", "", "", "", "", "", "", city, state, zip,
            type, route, box, unit));
    }

    private static String letters(int value) {
        StringBuilder result = new StringBuilder();
        do {
            result.append((char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return result.reverse().toString();
    }
}
