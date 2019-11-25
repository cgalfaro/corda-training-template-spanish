package net.corda.training.api

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.loggerFor
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.workflows.getCashBalances
import net.corda.training.flow.TDBOLiquidarFlow
import net.corda.training.flow.TDBOTransferirFlow
import net.corda.training.flow.SelfIssueCashFlow
import net.corda.training.flow.TDBOEmitirFlow
import net.corda.training.state.EstadoTDBO
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.slf4j.Logger
import java.util.Currency
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Este API es accesible desde /api/tdbo. Las rutas de los endpoints especificados abajo son relativos a la ruta anterior.
 * Hemos definido varios endpoints para manejar los TDBOs, cash y las distintas operaciones que se pueden hacer con ellos.
 */
@Path("tdbo")
class IOUApi(val rpcOps: CordaRPCOps) {
    private val me = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<IOUApi>()
    }

    fun X500Name.toDisplayString() : String  = BCStyle.INSTANCE.toString(this)

    /** Funciones de ayuda para filtrar el network map cache. */
    private fun isNotary(nodeInfo: NodeInfo) = rpcOps.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }
    private fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
    private fun isNetworkMap(nodeInfo : NodeInfo) = nodeInfo.legalIdentities.single().name.organisation == "Network Map Service"

    /**
     * Devuelve el nombre del nodo.
     */
    @GET
    @Path("yo")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("yo" to me.toString())

    /**
     * Devuelve todos los participantes registrados en el [NetworkMapService]. Estos nombres pueden ser utilizados para buscar identidades
     * usando el [IdentityService].
     */
    @GET
    @Path("contrapartes")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<String>> {
        return mapOf("contrapartes" to rpcOps.networkMapSnapshot()
                .filter { isNotary(it).not() && isMe(it).not() && isNetworkMap(it).not() }
                .map { it.legalIdentities.first().name.toX500Name().toDisplayString() })
    }

    /**
     * Tarea 1
     * Muestra todos los EstadoTDBOs que existen en la bóveda de un nodo.
     * TODO: Devuelva una lista de EstadoTDBOs registrados en el libro mayor.
     * Consejo - Utilice [rpcOps] para buscar en la bóveda todos los [EstadoTDBO]s no consumidos.
     */
    @GET
    @Path("tdbos")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIOUs(): List<StateAndRef<ContractState>> {
        // Filtro por tipo de estado: EstadoTDBO.
        return rpcOps.vaultQueryBy<EstadoTDBO>().states
    }

    /**
     * Muestra todos los estados cash que existen en la bóveda del nodo.
     */
    @GET
    @Path("cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash(): List<StateAndRef<ContractState>> {
        // Filtro por tipo de estado: Cash.
        return rpcOps.vaultQueryBy<Cash.State>().states
    }

    /**
     * Muestra todos los estados cash por moneda que existen en la bóveda del nodo.
     */
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
            // Muestra los balances de cash por moneda.
    fun getCashBalances() = rpcOps.getCashBalances()

    /**
     * Inicia el flujo para que dos participantes acuerden un TDBO.
     */
    @GET
    @Path("emitir-iou")
    fun issueIOU(@QueryParam(value = "cantidad") amount: Int,
                 @QueryParam(value = "moneda") currency: String,
                 @QueryParam(value = "participante") party: String): Response {
        // Obtener los objetos (party) participantes para mi nodo y el de la contraparte (prestamista).
        val yo = rpcOps.nodeInfo().legalIdentities.first()
        val prestamista = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(party)) ?: throw IllegalArgumentException("Nombre de participante desconocido")
        // Crear el nuevo TDBO con los parametros recibidos.
        try {
            val estado = EstadoTDBO(Amount(amount.toLong() * 100, Currency.getInstance(currency)), prestamista, yo)
            // Iniciar el TDBOEmitirFlow. Bloqueamos y espera para que el flujo regrese.
            val resultado = rpcOps.startTrackedFlow(::TDBOEmitirFlow, estado).returnValue.get()
            // Devuelve la respuesta.
            return Response
                    .status(Response.Status.CREATED)
                    .entity("ID de transacción: ${resultado.id} almacenado en el libro mayor.\n${resultado.tx.outputs.single()}")
                    .build()
            // Para los propósitos de este entrenamiento, no diferenciamos por tipo de excepción.
        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }

    /**
     * Transfiere un TDBO especificado por [linearId] a un nuevo participante.
     */
    @GET
    @Path("transferir-iou")
    fun transferIOU(@QueryParam(value = "id") id: String,
                    @QueryParam(value = "participante") party: String): Response {
        val linearId = UniqueIdentifier.fromString(id)
        val nuevoPrestamista = rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(party)) ?: throw IllegalArgumentException("Nombre de participante desconocido.")
        try {
            rpcOps.startFlow(::TDBOTransferirFlow, linearId, nuevoPrestamista).returnValue.get()
            return Response.status(Response.Status.CREATED).entity("TDBO $id transferido a $party.").build()

        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }

    /**
     * Liquida un TDBO. Requiere cash en la moneda correcta para que se pueda liquidar.
     * Ejemplo de petición:
     * curl -X PUT 'http://localhost:10007/api/iou/issue-iou?amount=99&currency=GBP&party=O=ParticipantC,L=New%20York,C=US
     */
    @GET
    @Path("liquidar-iou")
    fun settleIOU(@QueryParam(value = "id") id: String,
                  @QueryParam(value = "cantidad") amount: Int,
                  @QueryParam(value = "moneda") currency: String): Response {
        val linearId = UniqueIdentifier.fromString(id)
        val cantidadALiquidar = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        try {
            rpcOps.startFlow(::TDBOLiquidarFlow, linearId, cantidadALiquidar).returnValue.get()
            return Response.status(Response.Status.CREATED).entity("$amount $currency pagados al TDBO con id: $id.").build()

        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }

    /**
     * Endpoint asistente para emitir algo de cash para nosotros.
     */
    @GET
    @Path("auto-emitir-cash")
    fun selfIssueCash(@QueryParam(value = "cantidad") amount: Int,
                      @QueryParam(value = "moneda") currency: String): Response {
        val cantidadAEmitir = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        try {
            val estadoCash = rpcOps.startFlow(::SelfIssueCashFlow, cantidadAEmitir).returnValue.get()
            return Response.status(Response.Status.CREATED).entity(estadoCash.toString()).build()

        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }
}