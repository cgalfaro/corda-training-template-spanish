package net.corda.training.flow

import net.corda.core.contracts.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.node.MockNetwork
import net.corda.training.state.EstadoTDBO
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.training.contract.ContratoTDBO
import org.junit.*
import java.util.*
import kotlin.test.assertEquals

/**
 * Instrucciones para ejercicio práctico de flujos parte 3.
 * Descomente las pruebas y utilice los consejos + descripciones de tareas para completar el flujo para que las pruebas pasen.
 */
class TDBOLiquidarFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.training", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // Para nodos reales esto pasa automaticamente, pero necesitamos registrar a mano el flujo para las pruebas
        startedNodes.forEach { it.registerInitiatedFlow(TDBOEmitirFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(IOUSettleFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Emitir un TDBO en el libro mayor, necesitamos hacer esto antes de transferir uno.
     */
    private fun issueIou(iou: EstadoTDBO): SignedTransaction {
        val flow = TDBOEmitirFlow(iou)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Emitir un poco de cash en el libro mayor para nosotros, necesitamos esto antes de poder liquidar un TDBO.
     */
    private fun issueCash(amount: Amount<Currency>): Cash.State {
        val flow = SelfIssueCashFlow(amount)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Tarea 1.
     * La primera tarea es obtener de la boveda el [EstadoTDBO] para el [linearId] que recibimos, construye la transacción
     * y fírmala.
     * TODO: Obten el TDBO de la bóveda para [linearId] que recibimos, construye y firma la transacción de liquiar.
     * Consejos:
     * - Use el codigo de [TDBOTransferirFlow] para obtener [EstadoTDBO] correcto de la bóveda.
     * - Necesitará utilizar la funcionalidad de [Cash.generateSpend] de la bóveda para agregar los estados cash y comando cash
     *   a tu transacción. El API es bastante simple. Toma una referencia al [TransactionBuilder], un [Amount] y
     *   el objeto [Party] del recipiente. La función mutará tu constructor agregando los estados y los comandos.
     * - Despues debes producir la salida de [EstadoTDBO] utilizando la función [EstadoTDBO.pay].
     * - Agrega la entrada [EstadoTDBO] [StateAndRef] a la nueva salida de [EstadoTDBO] a la transacción.
     * - Firma la transacción y devuélvela.
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val stx = issueIou(EstadoTDBO(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
        issueCash(5.POUNDS)
        val tdboEntrada = stx.tx.outputs.single().data as EstadoTDBO
        val flujo = TDBOLiquidarFlow(tdboEntrada.linearId, 5.POUNDS)
        val futuro = a.startFlow(flujo)
        mockNetwork.runNetwork()
        val resultadoLiquidar = futuro.getOrThrow()
        // Revisa que la transacción esté bien formada...
        // Una EstadoTDBO de salida, una referencia de EstadoTDBO de entrada, cash de entrada y de salida
        a.transaction {
            val ledgerTx = resultadoLiquidar.toLedgerTransaction(a.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 2)
            val tdboSalida = ledgerTx.outputs.map { it.data }.filterIsInstance<EstadoTDBO>().single()
            assertEquals(
                    tdboSalida,
                    tdboEntrada.pagar(5.POUNDS))
            // Suma cash de salida completo. Esto es complejo ya que pueda que existan múltiples estados de salida cash
            // y puede que no todos han sido asignados al prestamista.
            val outputCashSum = ledgerTx.outputs
                    .map { it.data }
                    .filterIsInstance<Cash.State>()
                    .filter { it.owner == b.info.chooseIdentityAndCert().party }
                    .sumCash()
                    .withoutIssuer()
            // Compare el cash asignado al prestamista con la cantidad reclamada que el deudor liquida.
            assertEquals(
                    outputCashSum,
                    (tdboEntrada.cantidad - tdboEntrada.pagado - tdboSalida.pagado))
            val command = ledgerTx.commands.requireSingleCommand<ContratoTDBO.Commands>()
            assert(command.value == ContratoTDBO.Commands.Liquidar())
            // Compruebe que la transacción haya sido firmada por el deudor.
            resultadoLiquidar.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey,
                    mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
        }
    }

    /**
     * Task 2.
     * Only the borrower should be running this flow for a particular IOU.
     * TODO: Grab the IOU for the given [linearId] from the vault and check the node running the flow is the borrower.
     * Hint: Use the data within the iou obtained from the vault to check the right node is running the flow.
     */
//    @Test
//    fun settleFlowCanOnlyBeRunByBorrower() {
//        val stx = issueIou(IOUState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as IOUState
//        val flow = IOUSettleFlow(inputIou.linearId, 5.POUNDS)
//        val future = b.startFlow(flow)
//        mockNetwork.runNetwork()
//        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
//    }

    /**
     * Task 3.
     * The borrower must have at least SOME cash in the right currency to pay the lender.
     * TODO: Add a check in the flow to ensure that the borrower has a balance of cash in the right currency.
     * Hint:
     * - Use [serviceHub.getCashBalances] - it is a map which can be queried by [Currency].
     * - Use an if statement to check there is cash in the right currency present.
     */
//    @Test
//    fun borrowerMustHaveCashInRightCurrency() {
//        val stx = issueIou(IOUState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
//        val inputIou = stx.tx.outputs.single().data as IOUState
//        val flow = IOUSettleFlow(inputIou.linearId, 5.POUNDS)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        assertFailsWith<IllegalArgumentException>("Borrower has no GBP to settle.") { future.getOrThrow() }
//    }

    /**
     * Task 4.
     * The borrower must have enough cash in the right currency to pay the lender.
     * TODO: Add a check in the flow to ensure that the borrower has enough cash to pay the lender.
     * Hint: Add another if statement similar to the one required above.
     */
//    @Test
//    fun borrowerMustHaveEnoughCashInRightCurrency() {
//        val stx = issueIou(IOUState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
//        issueCash(1.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as IOUState
//        val flow = IOUSettleFlow(inputIou.linearId, 5.POUNDS)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        assertFailsWith<IllegalArgumentException>("Borrower has only 1.00 GBP but needs 5.00 GBP to settle.") { future.getOrThrow() }
//    }

    /**
     * Task 5.
     * We need to get the transaction signed by the other party.
     * TODO: Use a subFlow call to [initateFlow] and the [SignTransactionFlow] to get a signature from the lender.
     */
//    @Test
//    fun flowReturnsTransactionSignedByBothParties() {
//        val stx = issueIou(IOUState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as IOUState
//        val flow = IOUSettleFlow(inputIou.linearId, 5.POUNDS)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        val settleResult = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output IOUState, one input IOUState reference, input and output cash
//        settleResult.verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
//    }

    /**
     * Task 6.
     * We need to get the transaction signed by the notary service
     * TODO: Use a subFlow call to the [FinalityFlow] to get a signature from the lender.
     */
//    @Test
//    fun flowReturnsCommittedTransaction() {
//        val stx = issueIou(IOUState(10.POUNDS, b.info.chooseIdentityAndCert().party, a.info.chooseIdentityAndCert().party))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as IOUState
//        val flow = IOUSettleFlow(inputIou.linearId, 5.POUNDS)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        val settleResult = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output IOUState, one input IOUState reference, input and output cash
//        settleResult.verifyRequiredSignatures()
//    }
}
