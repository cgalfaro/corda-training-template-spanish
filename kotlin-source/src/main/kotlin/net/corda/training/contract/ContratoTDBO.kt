package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.training.state.EstadoTDBO
import java.lang.reflect.Type

/**
 * Aquí es donde agregarás el código de contrato que define como el [EstadoTDBO] se comporta. Revisa los unit tests en
 * [IOUContractTests] para instrucciones de como terminar la clase [ContratoTDBO].
 */
class ContratoTDBO : Contract {
    companion object {
        @JvmStatic
        val TDBO_CONTRACT_ID = "net.corda.training.contract.ContratoTDBO"
    }

    /**
     * Agrega cualquier comando requerido como clases dentro de esta interfaz
     * Generalmente es útil encapsular los comandos dentro de una interfaz, para lo cual puedes utilizar la funcion
     * [requireSingleCommand] para encontrar un numero de coandos que implementan esta interfaz.
     */
    interface Commands : CommandData {
        // Agrega los comandos aquí
        // Ej:
        // class HazAlgo : TypeOnlyCommandData(), Commands
        class Emitir : TypeOnlyCommandData(), Commands
        class Transferir : TypeOnlyCommandData(), Commands
        class Liquidar : TypeOnlyCommandData(), Commands
    }

    /**
     * El codigo de contrato para el [ContratoTDBO].
     * Estas limitantes se documentan así misma por lo cual no requieren de una explicación adicional.
     */
    override fun verify(tx: LedgerTransaction) {
        // Agrega codigo de contrato aquí
        // requireThat {
        //     ...
        // }
        val comando = tx.commands.requireSingleCommand<Commands>()
        when (comando.value) {
            is Commands.Emitir -> requireThat {
                "No se deben incluir entradas para la emision de un TDBO" using (tx.inputs.isEmpty())
                "Solo un estado de salida debe ser creado al emitir un TDBO." using (tx.outputs.size == 1)
                val estadoSalida = tx.outputStates.single() as EstadoTDBO
                "Un TDBO recién emitido debe contener una cantidad positiva" using (estadoSalida.cantidad.quantity > 0)
                "El prestamista y el deudor no pueden tener la misma identidad." using (estadoSalida.prestamista != estadoSalida.deudor)
                "El prestamista y el deudor deben de firmar juntos para emitir un TDBO." using
                        (comando.signers.toSet() == estadoSalida.participants.map { it.owningKey }.toSet())
            }
            is Commands.Transferir -> requireThat {
                "Una transferencia de TDBO solo debe consumir un estado de entrada." using (tx.inputs.size == 1)
                "Solo un estado de salida debe ser creado al transferir un TDBO." using (tx.outputs.size == 1)
                val estadoEntrada = tx.inputStates.single() as EstadoTDBO
                val estadoSalida = tx.outputStates.single() as EstadoTDBO
                "Solo la propiedad prestamista puede cambiar." using
                        (estadoEntrada == estadoSalida.conNuevoPrestamista(estadoEntrada.prestamista))
                "La propiedad prestamista debe de cambiar en una transferencia." using
                        (estadoEntrada.prestamista != estadoSalida.prestamista)
                "El deudor, el prestamista anterior y el nuevo prestamista deben firmar una transferencia de TDBO" using
                        (comando.signers.toSet() == (estadoEntrada.participants.map { it.owningKey }.toSet() union
                                estadoSalida.participants.map { it.owningKey }.toSet()))
            }
            is Commands.Liquidar -> requireThat {
                val tdbos = tx.groupStates<EstadoTDBO, UniqueIdentifier> { it.linearId }.single()
                "Debe existir un TDBO de entrada para liquidar." using (tdbos.inputs.size == 1)
                val cash = tx.outputsOfType<Cash.State>()
                "Debe existir una salida cash." using (cash.isNotEmpty())
                val tdboEntrada = tdbos.inputs.single()
                val cashAceptado = cash.filter { it.owner == tdboEntrada.prestamista }
                "Debe existir cash en estado de salida pagado al prestamista." using (cashAceptado.isNotEmpty())
                val sumaCashAceptado = cashAceptado.sumCash().withoutIssuer()
                val faltaPorPagar = tdboEntrada.cantidad - tdboEntrada.pagado
                "La cantidad que se paga no puede ser más que la cantidad que falta pagar." using
                        (faltaPorPagar >= sumaCashAceptado)
                if (faltaPorPagar == sumaCashAceptado) {
                    "La deuda se pagó completa no debe existir TDBO de salida." using
                            (tdbos.outputs.isEmpty())
                } else {
                    "La deuda no se pagó completa debe haber un TDBO de salida." using
                            (tdbos.outputs.size == 1)
                    val tdboSalida = tdbos.outputs.single()
                    "El deudor no puede cambiar cuando liquidamos." using (tdboEntrada.deudor == tdboSalida.deudor)
                    "La cantidad no puede cambiar cuando liquidamos." using (tdboEntrada.cantidad == tdboSalida.cantidad)
                    "El prestamista no puede cambiar cuando liquidamos." using (tdboEntrada.prestamista == tdboSalida.prestamista)
                }
                "Ambos el prestamista y el deudor deben firmar una transacción de liquidar." using
                        (comando.signers.toSet() == tdboEntrada.participants.map { it.owningKey }.toSet())
            }
        }

    }
}