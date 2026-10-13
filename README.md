# AddressStandardizer

A small, offline Kotlin/JVM library and command-line tool for parsing and formatting US addresses. Lookup data ships inside the JAR; a standardizer can be reused safely across threads.

It normalizes address syntax. It does **not** verify that an address exists, validate city/state/ZIP combinations, or provide USPS certification.

## Build and run

Requires JDK 21. The Gradle wrapper downloads the pinned build tools and Kotlin compiler; no separate Kotlin or Gradle installation is needed.

```sh
# macOS with Homebrew
brew install openjdk@21
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew test
./gradlew run --args='"123 North Main Street Apt 4, Springfield IL 62704"'
# 123 N MAIN ST APT 4 SPRINGFIELD IL 62704
```

`./gradlew build` also verifies the installed CLI from a different working directory. `./gradlew installDist` creates a standalone application directory under `build/install/`; its `bin/address-standardizer` launcher works outside this repository. `distZip` and `distTar` create distributable archives. The library and sources JARs are under `build/libs/`.

The CLI accepts one address, either quoted or as separate arguments. `--help` prints usage. Exit codes are `0` for success, `2` for invalid input/usage, and `1` when lookup data cannot load. Diagnostics go to stderr; successful output goes to stdout.

## Kotlin API

```kotlin
import com.wiseaddress.address_standardizer.us.USStandardizer

val standardizer = USStandardizer()
val address = standardizer.standardize("123 North Main Street Apt 4, Springfield IL 62704")

println(address.streetName)        // MAIN
println(address.secondaryRange)   // 4
println(address.format())          // 123 N MAIN ST APT 4 SPRINGFIELD IL 62704
println(address.formatLines())     // [123 N MAIN ST APT 4, SPRINGFIELD IL 62704]
```

Keep one instance for repeated calls. Default instances share immutable lookup tables loaded once from the classpath. `toString()` retains the data-class debug representation; use `format()` for display or `formatLines()` for a mailing label.

`USStandardizedAddress` keeps the original ten street/locality properties. Optional fields are empty when not supplied. `addressType` distinguishes `STREET`, `PO_BOX`, `RURAL_ROUTE`, `HIGHWAY_CONTRACT`, and `MILITARY`. PO boxes populate `boxNumber`; rural, highway contract, and military addresses also populate `routeNumber`. Only military addresses populate `militaryUnit`. Special addresses leave street fields empty. A numbered highway uses `STREET` with the entire highway designation in `streetName` and an empty suffix.

Java callers can use `new USStandardizer().standardize(address).format()`.

## Supported forms

| Input example | Canonical delivery line |
| --- | --- |
| `123 North Main Street South, Springfield IL 62704` | `123 N MAIN ST S` |
| `123 Oak Street Drive, Springfield IL 62704` | `123 OAK STREET DR` |
| `12-34 Main St #A-12, Queens NY 11354` | `12-34 MAIN ST # A-12` |
| `12 1 / 2 Main St, Boston MA 02108` | `12 1/2 MAIN ST` |
| `123 Broadway, New York NY 10001` | `123 BROADWAY` |
| `1155 US Hwy 70, Austin TX 78701` | `1155 US HIGHWAY 70` |
| `101 County Rd 20, Austin TX 78701` | `101 COUNTY ROAD 20` |
| `220 I-680, Austin TX 78701` | `220 INTERSTATE 680` |
| `P.O. Box 00123, Boston MA 02108` | `PO BOX 00123` |
| `Rural Route 03 Box 012A, Springfield IL 62704` | `RR 3 BOX 012A` |
| `HC 068 Box 45-1, Springfield IL 62704` | `HC 68 BOX 45-1` |
| `PSC 123 Box 456, APO AE 09012` | `PSC 123 BOX 456` |

Also supported: full state names (including District of Columbia), postal territory codes, mixed case, repeated whitespace, address-line breaks, common abbreviation periods, named apartment/unit identifiers, leading-zero ZIP codes, and compact/spaced/hyphenated ZIP+4. House numbers may contain supported letters, hyphens, or fractions. Numbered highways use the bundled county/state/local highway aliases, including farm-to-market roads.

PO box numbers retain meaningful zeros and may be a single letter. Rural/highway contract routes require a box number; route leading zeros are removed, while box zeros and supplied box hyphens are preserved. Military unit/box addresses accept CPR, OPC, PSC, UPR, and UNIT, and normalize legacy CMR to CPR. Military localities require APO/FPO/DPO paired with AA/AE/AP.

## Boundaries and errors

Use a comma or newline between the delivery line and city whenever possible. Those boundaries let the parser distinguish suffix-like street/city words, suffixless streets, and full postdirections. Unit information may occupy a separate delivery line before the city.

Without an explicit boundary, the parser uses the first eligible street suffix, treating two adjacent suffix words as a name followed by a suffix. Remaining words become the city after any direction and unit. Abbreviated directions after a suffix are postdirections; a spelled-out direction before city words is retained in the city unless a unit marker or explicit boundary resolves it. For example, `MAIN ST WEST SPRINGFIELD` retains city `WEST SPRINGFIELD`; use `MAIN ST WEST, SPRINGFIELD` for a west postdirection. These are deterministic syntax rules, not a geographic lookup.

For a multiword suffixless street, supply a comma/newline or an explicit unit boundary. Use `formatLines().joinToString("\n")` when parsing formatted output again: the flat `format()` string intentionally omits delivery/locality separators.

At least a state or ZIP code is required. Missing city, state, or ZIP values are not invented. Invalid recognized forms throw `InvalidAddressException`, an `IllegalArgumentException`, with a component-specific message. Unsupported inputs include intersections, recipient/company lines, multiple delivery addresses, general delivery, Puerto Rico urbanization lines, and international address formats. Inputs are limited to 4,096 characters.

## Lookup data and customization

The source of truth is [`usdata.json`](src/main/resources/com/wiseaddress/address_standardizer/usdata.json). Model loading rejects missing sections, invalid value types, conflicting aliases, and references to unknown canonical identifiers. Models make defensive copies and expose unmodifiable maps/lists.

```kotlin
import com.wiseaddress.address_standardizer.model.ModelFactory
import com.wiseaddress.address_standardizer.us.USStandardizer
import java.io.File

val custom = USStandardizer(ModelFactory.load(File("custom-us-data.json")))
```

Custom data uses the same US grammar and JSON schema; it does not add other countries. `ModelFactory.parse(json)` loads JSON directly. The old Java-serialized `.model` format and `ModelFactory.create()` are retired; applications no longer need a `models/` directory.

Normalization references include USPS Publication 28 [directionals](https://pe.usps.com/text/pub28/28c2_014.htm), [suffixes](https://pe.usps.com/text/pub28/28c2_015.htm), [highways](https://pe.usps.com/text/pub28/pub28c2_018.htm), and the [publication index](https://pe.usps.com/text/pub28/). The bundled data resolves the ambiguous `MDW` alias to its singular canonical identifier, preserving stable normalization.

## Tests and performance

`./gradlew clean build jmhClasses` runs regression tests, builds distributions, verifies packaged lookup data, and compiles benchmarks. CI runs this same command on JDK 21.

```sh
./gradlew benchmark
# Optional: run just the warmed parsing comparison
./gradlew benchmark -PbenchmarkArgs='.*commonParsing.*'
```

JMH measures successful and rejected parsing, cached construction, and fresh JSON model loading separately, with warmup, multiple forks, consumed results, and GC allocation profiling. Alongside the small compatibility corpus, `variedParsing` exercises 1,024 distinct addresses across eight delivery forms and checks every parsed field before measurement. Benchmark reports are written to ignored `agent_work/benchmarks/`. Measure on your workload before setting a throughput target; parsing correctness and startup costs are separate from steady-state speed.

Apache-2.0 licensed; see [LICENSE](LICENSE).
