import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory
import com.wiseaddress.address_standardizer.us.USStandardizer
import java.io.File

fun main(args: Array<String>) {
    val standardizer = USStandardizer()
    val address = standardizer.standardize("789 South Oak Street Springfield Illinois 12345 6789")
    println(address)
//    createModel()
//    loadModel()
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