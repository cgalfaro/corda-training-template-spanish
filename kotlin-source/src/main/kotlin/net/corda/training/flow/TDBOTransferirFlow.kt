package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.ContratoTDBO
import net.corda.training.state.EstadoTDBO
import java.lang.IllegalArgumentException

/**
 * Este es el flujo que se encarga de transferir TDBOs existentes en el libro mayor.
 * Adquirir la firma de la contraparte es manejado por [CollectSignaturesFlow].
 * La notarización (si es necesaria) y la actualización del libro mayor es manejado por [FinalityFlow].
 * El flujo devuelve la transacción firmada ([SignedTransaction]) que fue almacenada en el libro mayor.
 */
@InitiatingFlow
@StartableByRPC
class TDBOTransferirFlow(val linearId: UniqueIdentifier, val nuevoPrestamista: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Codigo por defecto para evitar problemas al ejecutar los unit tests. ¡Remover antes de comenzar las tareas de flujos!
        val criterioDeBusqueda = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val tdboStateAndRef = serviceHub.vaultService.queryBy<EstadoTDBO>(criterioDeBusqueda).states.single()
        val tdboEntrada = tdboStateAndRef.state.data

        if (ourIdentity != tdboEntrada.prestamista) {
            throw IllegalArgumentException("La transferencia de un TDBO solo puede ser iniciada por el prestamista actual del TDBO")
        }

        val tdboSalida = tdboEntrada.conNuevoPrestamista(nuevoPrestamista)
        val firmantes = (tdboEntrada.participants + nuevoPrestamista).map { it.owningKey }
        val comandoTransferir = Command(ContratoTDBO.Commands.Transferir(), firmantes)
        val notario = serviceHub.networkMapCache.notaryIdentities.first()
        val constructorDeTransaccion = TransactionBuilder(notary = notario)

        constructorDeTransaccion.withItems(
                tdboStateAndRef,
                StateAndContract(tdboSalida, ContratoTDBO.TDBO_CONTRACT_ID),
                comandoTransferir
        )

        constructorDeTransaccion.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(constructorDeTransaccion)
        val sesiones = (tdboEntrada.participants - ourIdentity + nuevoPrestamista).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sesiones))
        return subFlow(FinalityFlow(stx, sesiones))
    }
}

/**
 * Este es el flujo que firma las transferencias de TDBOs.
 * La firma es manejada por el [SignTransactionFlow].
 */
@InitiatedBy(TDBOTransferirFlow::class)
class TDBOTransferirFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val flujoDeTransaccionFirmada = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "Esta debe ser una transacción de TDBO" using (output is EstadoTDBO)
            }
        }
        val txQueAcabamosDeFirmar = subFlow(flujoDeTransaccionFirmada)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txQueAcabamosDeFirmar.id))
    }
}