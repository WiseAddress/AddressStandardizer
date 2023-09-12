import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory
import com.wiseaddress.address_standardizer.us.USStandardizer
import java.io.File

fun main(args: Array<String>) {
//    createModel()
//    loadModel()

    val standardizer = USStandardizer()
    val address = standardizer.standardize("789 North South Oak Street Drive Suite 101C West Springfield West Virginia 12345 6789")
    println(address)
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