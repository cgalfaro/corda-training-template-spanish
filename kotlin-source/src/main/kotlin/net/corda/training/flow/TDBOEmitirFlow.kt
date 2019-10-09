package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
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
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO

/**
 * Este es el flujo que se encarga de Emitir un nuevo TDBO al libro mayor.
 * Recopilar la firma de la contraparte es manejado por el flujo [CollectSignaturesFlow].
 * La notarización (si es requerida) y su almacenamiento en el libro mayor es manejado por el flow [FinalityFlow].
 * este flujo devuelve la [SignedTransaction] que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOEmitirFlow(val estado: EstadoTDBO) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Codigo por defecto para evitar problemas al ejecutar los unit tests. ¡Remover antes de comenzar las tareas de flujos!
        val notario = serviceHub.networkMapCache.notaryIdentities.first()

        val comandoEmitir = Command(ContratoTDBO.Commands.Emitir(), estado.participants.map { it.owningKey })

        val constructorDeTransaccion = TransactionBuilder(notary = notario)
        constructorDeTransaccion.addOutputState(estado, ContratoTDBO.TDBO_CONTRACT_ID)
        constructorDeTransaccion.addCommand(comandoEmitir)
        constructorDeTransaccion.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(constructorDeTransaccion)
        return ptx

    }
}

/**
 * Este es el flujo que firma las emisiones de TDBOs.
 * El firmar es manejador por el flujo [SignTransactionFlow].
 */
@InitiatedBy(TDBOEmitirFlow::class)
class TDBOEmitirFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is EstadoTDBO)
            }
        }
        subFlow(signedTransactionFlow)
    }
}