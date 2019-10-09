package net.corda.training.flow

import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.*
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO
import org.junit.*
import kotlin.test.assertFailsWith

/**
 * Instrucciones para ejercicio práctico de Flujos parte 1.
 * Descomenta las pruebas, utiliza los consejos y la descripción de las tareas para completar los flujos y que las pruebas pasen.
 * ¡Nota! Estas pruebas dependen de que Quasar sea cargado, cambia tu "run configuration" a "-ea -javaagent:lib/quasar.jar"
 * Run configuration puede ser editado en IntelliJ bajo Run -> Edit Configurations -> VM options
 * En algunas maquinas/configuraciones pueda que sea necesario que proveas un path completo al archivo quasar.jar.
 * En algunas maquinas/configuraciones es posible que puedas utilizar la opcion "JAR manifest" para acortar el comando.
 */
class TDBOEmitirFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.training"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // Para nodos reales esto pasa automaticamente, pero necesitamos registrar a mano el flujo para las pruebas
        startedNodes.forEach { it.registerInitiatedFlow(TDBOEmitirFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Tarea 1.
     * Construir el [TDBOEmitirFlow]!
     * TODO: Implementar el flujo [TDBOEmitirFlow] que construye y devuelve una transacción parcialmente firmada del tipo [SignedTransaction].
     * Consejo:
     * - ¡Hay bastante por hacer para que esta prueba pase!
     * - Crear un [TransactionBuilder] y pasalo a una referencia de notario.
     * -- Un objeto notario del tipo [Party] se puede obtener de: [FlowLogic.serviceHub.networkMapCache].
     * -- Para este proyecto de entrenamiento únicamente existe un notario.
     * - Crear un [ContratoTDBO.Commands.Emitir] dentro de un nuevo [Command].
     * -- Los firmantes requeridos serán los mismos que los participantes de los estados.
     * -- Agrega el [Command] al constructor de la transacción (TransactionBuilder) con [addCommand].
     * - Utilice el parametro del flow [EstadoTDBO] como el estado de salida con [addOutputState]
     * - Puntos extras: Utilice [TransactionBuilder.withItems] para crear la transacción.
     * - Firme la transacción y convértala a una [SignedTransaction] usando el método [serviceHub.signInitialTransaction].
     * - Devuelva la [SignedTransaction].
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val flujo = TDBOEmitirFlow(tdbo)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        // Devuelva el objeto SignedTransaction parcialmente firmado del TDBOEmitirFlow.
        val ptx: SignedTransaction = futuro.getOrThrow()
        // Imprima la transacción para propósitos de debugging.
        println(ptx.tx)
        // Compruebe que la transacción está bien formada...
        // Cero salidas, una entrada de EstadoTDBO y un comando con las propiedades correctas.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is EstadoTDBO)
        val command = ptx.tx.commands.single()
        assert(command.value is ContratoTDBO.Commands.Emitir)
        assert(command.signers.toSet() == tdbo.participants.map { it.owningKey }.toSet())
        ptx.verifySignaturesExcept(deudor.owningKey,
                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }

    /**
     * Tarea 2.
     * Ahora ya tenemos una transacción bien formada, necesitamos verificarla adecuadamente usando el [ContratoTDBO].
     * TODO: Agregue al [TDBOEmitirFlow] para que ademas de firmar la transacción también la verifique usando verify.
     * Consejo: Puedes verificar directamente en el constructor antes de finalizar la transacción. De esta forma
     * puedes confirmar la transacción antes de hacerla inmutable con la firma.
     */
    @Test
    fun flowReturnsVerifiedPartiallySignedTransaction() {
        // Prueba con un TDBO de cantidad 0 falla.
        val prestamista = a.info.chooseIdentityAndCert().party
        val deudor = b.info.chooseIdentityAndCert().party
        val tdboCero = EstadoTDBO(0.POUNDS, prestamista, deudor)
        val futuroUno = a.startFlow(TDBOEmitirFlow(tdboCero))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futuroUno.getOrThrow() }
        // Prueba con un TDBO del mismo particpante falla.
        val deudorEsPrestamista = EstadoTDBO(10.POUNDS, prestamista, prestamista)
        val futuroDos = a.startFlow(TDBOEmitirFlow(deudorEsPrestamista))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futuroDos.getOrThrow() }
        // Prueba que un TDBO bien hecho pasa
        val tdbo = EstadoTDBO(10.POUNDS, prestamista, deudor)
        val futureThree = a.startFlow(TDBOEmitirFlow(tdbo))
        mockNetwork.runNetwork()
        futureThree.getOrThrow()
    }

    /**
     * IMPORTANTE: Revisa [CollectSignaturesFlow] antes de continuar. //File es read only no se puede traducir
     * Tarea 3.
     * Ahora necesitamos recolectar la firma de la [otherParty] (contraparte) usando el flow [CollectSignaturesFlow].
     * TODO: Agregar al [TDBOEmitirFlow] para recolectar la firma de [otherParty] (otro participante).
     * Consejo:
     * Del lado del inicador:
     * - Get a set of signers required from the participants who are not the node
     * - - [ourIdentity] will give you the identity of the node you are operating as
     * - Use [initiateFlow] to get a set of [FlowSession] objects
     * - - Using [state.participants] as a base to determine the sessions needed is recommended. [participants] is on
     * - - the state interface so it is guaranteed to exist where [lender] and [borrower] are not.
     * - - Hint: [ourIdentity] will give you the [Party] that represents the identity of the initiating flow.
     * - Use [subFlow] to start the [CollectSignaturesFlow]
     * - Pass it a [SignedTransaction] object and [FlowSession] set
     * - It will return a [SignedTransaction] with all the required signatures
     * - The subflow performs the signature checking and transaction verification for you
     *
     * On the Responder side:
     * - Create a subclass of [SignTransactionFlow]
     * - Override [SignTransactionFlow.checkTransaction] to impose any constraints on the transaction
     *
     * Using this flow you abstract away all the back-and-forth communication required for parties to sign a
     * transaction.
     */
//    @Test
//    fun flowReturnsTransactionSignedByBothParties() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val iou = IOUState(10.POUNDS, lender, borrower)
//        val flow = IOUIssueFlow(iou)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        val stx = future.getOrThrow()
//        stx.verifyRequiredSignatures()
//    }

    /**
     * Task 4.
     * Now we need to store the finished [SignedTransaction] in both counter-party vaults.
     * TODO: Amend the [TDBOEmitirFlow] by adding a call to [FinalityFlow].
     * Hint:
     * - As mentioned above, use the [FinalityFlow] to ensure the transaction is recorded in both [Party] vaults.
     * - Do not use the [BroadcastTransactionFlow]!
     * - The [FinalityFlow] determines if the transaction requires notarisation or not.
     * - We don't need the notary's signature as this is an issuance transaction without a timestamp. There are no
     *   inputs in the transaction that could be double spent! If we added a timestamp to this transaction then we
     *   would require the notary's signature as notaries act as a timestamping authority.
     */
//    @Test
//    fun flowRecordsTheSameTransactionInBothPartyVaults() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val iou = IOUState(10.POUNDS, lender, borrower)
//        val flow = IOUIssueFlow(iou)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        val stx = future.getOrThrow()
//        println("Signed transaction hash: ${stx.id}")
//        listOf(a, b).map {
//            it.services.validatedTransactions.getTransaction(stx.id)
//        }.forEach {
//                    val txHash = (it as SignedTransaction).id
//                    println("$txHash == ${stx.id}")
//                    assertEquals(stx.id, txHash)
//                }
//    }
}
