package net.corda.training.plugin

import net.corda.core.messaging.CordaRPCOps
import net.corda.training.api.IOUApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class IOUPlugin : WebServerPluginRegistry {
    /**
     * Una lista de clases que esponen los APIs web.
     */
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::IOUApi))

    /**
     * Una lista de directorios en el directorio "resources" que serán servidos por Jetty bajo /web
     * La plantilla del frontend web es accesible en /web/template.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the iouWeb directory in resources to /web/template
            // Esto entregará el directorio iouweb en resources a /web/Template
            "iou" to javaClass.classLoader.getResource("iouWeb").toExternalForm()
    )
}