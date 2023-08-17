import com.wiseaddress.address_standardizer.model.Model
import com.wiseaddress.address_standardizer.model.ModelFactory
import java.io.File

fun main(args: Array<String>) {
    createModel()
    loadModel()
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