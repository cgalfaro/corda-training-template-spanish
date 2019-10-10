package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.training.state.EstadoTDBO
import java.util.*

/**
 * Este es el flujo que maneja la liquidación (parcial) de TDBOs existentes en el libro mayor.
 * La adquisición de la firma de la contraparte es manejado por [CollectSignaturesFlow].
 * La notarización (si es necesaria) y la actualización del libro mayor es manejado por [FinalityFlow].
 * El flujo devuelve la transacción firmada ([SignedTransaction]) que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOLiquidarFlow(val linearId: UniqueIdentifier, val amount: Amount<Currency>): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Codigo por defecto para evitar problemas al ejecutar los unit tests. ¡Remover antes de comenzar las tareas de flujos!
        return serviceHub.signInitialTransaction(
                TransactionBuilder(notary = null)
        )
    }
}

/**
 * Este es el flow que firma las liquidaciones de TDBOs.
 * La firma es manejada por el [SignTransactionFlow].
 */
@InitiatedBy(TDBOLiquidarFlow::class)
class IOUSettleFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val outputStates = stx.tx.outputs.map { it.data::class.java.name }.toList()
                "There must be an IOU transaction." using (outputStates.contains(EstadoTDBO::class.java.name))
            }
        }

        subFlow(signedTransactionFlow)
    }
}

@InitiatingFlow
@StartableByRPC
/**
 * Emite la cantidad de cash en la moneda deseada al nodo que llama.
 * ¡Sólo para ser utilizado para propósito de demo/ejemplo/entrenamiento.
 */
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {
    @Suspendable
    override fun call(): Cash.State {
        /** Crea el comando de emitir (issue) cash. */
        val issueRef = OpaqueBytes.of(0)
        /** Nota: seguimos trabajando en poder manejar con multiples notarios, aún no está listo. */
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        /** Crea la transacción para emitir el cash. */
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, notary))
        /** Devuelve el cash de salida. */
        return cashIssueTransaction.stx.tx.outputs.single().data as Cash.State
    }
}