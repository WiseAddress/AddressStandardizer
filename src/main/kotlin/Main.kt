import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory
import com.wiseaddress.address_standardizer.us.USStandardizer
import java.io.File
import kotlin.system.measureNanoTime

fun main(args: Array<String>) {
//    createModel()
//    loadModel()

    val standardizer = USStandardizer()
    val elapsed = measureNanoTime {
        for (i in 0..999999) {
            standardizer.standardize("$i North South Oak Street Drive # $i-A West Springfield 12345 6789")
        }
    }
    val elapsedInSeconds = elapsed / (1_000_000 * 1000)
    //My results:
    //Time to standardize 1000000 addresses: 1 seconds. Estimated time per address: 1391 nanoseconds
    println("Time to standardize 1000000 addresses: $elapsedInSeconds seconds. Estimated time per address: ${elapsed / (1_000_000)} nanoseconds")
    println(standardizer.standardize("789 North South Oak Street Drive # 123-A West Springfield 12345 6789"))
}

fun loadModel() {
    println("Loading model from \"models/us.model\"")

    val m : Model = ModelFactory.load("us")
    println(m)
}

fun createModel() {
    println("Creating model from \"data/usdata.json\"")

    ModelFactory.create(File("./data/usdata.json"), "us")
}