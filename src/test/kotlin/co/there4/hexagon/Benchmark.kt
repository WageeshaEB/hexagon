package co.there4.hexagon

import co.there4.hexagon.serialization.convertToMap
import co.there4.hexagon.serialization.serialize
import co.there4.hexagon.server.*
import co.there4.hexagon.server.backend.servlet.ServletServer

import java.net.InetAddress.getByName as address
import java.time.LocalDateTime.now
import java.util.concurrent.ThreadLocalRandom
import javax.servlet.annotation.WebListener

// DATA CLASSES
internal data class Message(val message: String = "Hello, World!")
internal data class Fortune(val _id: Int, val message: String)
internal data class World(val _id: Int, val id: Int = _id, val randomNumber: Int = rnd())

// CONSTANTS
private val CONTENT_TYPE_JSON = "application/json"
private val QUERIES_PARAM = "queries"

var server: Server? = null

// UTILITIES
internal fun rnd() = ThreadLocalRandom.current().nextInt(DB_ROWS) + 1

private fun Exchange.returnWorlds(worldsList: List<World>) {
    val worlds = worldsList.map { it.convertToMap() - "_id" }
    val result = if (worlds.size == 1) worlds.first().serialize() else worlds.serialize()

    ok(result, CONTENT_TYPE_JSON)
}

private fun Exchange.getQueries() =
    try {
        val queries = request[QUERIES_PARAM]?.toInt() ?: 1
        when {
            queries < 1 -> 1
            queries > 500 -> 500
            else -> queries
        }
    }
    catch (ex: NumberFormatException) {
        1
    }

// HANDLERS
private fun Exchange.listFortunes(store: Repository) {
    val fortunes = store.findFortunes() + Fortune(0, "Additional fortune added at request time.")
    response.contentType = "text/html; charset=utf-8"
    template("fortunes.html", "fortunes" to fortunes.sortedBy { it.message })
}

private fun Router.benchmarkRoutes(store: Repository) {
    before {
        response.addHeader("Server", "Servlet/3.1")
        response.addHeader("Transfer-Encoding", "chunked")
        response.addHeader("Date", httpDate(now()))
    }

    get("/plaintext") { ok("Hello, World!", "text/plain") }
    get("/json") { ok(Message().serialize(), CONTENT_TYPE_JSON) }
    get("/fortunes") { listFortunes(store) }
    get("/db") { returnWorlds(store.findWorlds(getQueries())) }
    get("/query") { returnWorlds(store.findWorlds(getQueries())) }
    get("/update") { returnWorlds(store.replaceWorlds(getQueries())) }
}

@WebListener internal class Web : ServletServer () {
    init {
        router.initRoutes()
    }

    override fun Router.initRoutes() {
        benchmarkRoutes(createStore("mongodb"))
    }
}

fun main(args: Array<String>) {
    val store = createStore(if (args.isEmpty()) "mongodb" else args[0])
    server = serve {
        benchmarkRoutes(store)
    }
}
