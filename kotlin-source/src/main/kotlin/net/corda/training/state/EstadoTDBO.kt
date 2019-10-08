package net.corda.training.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.training.contract.ContratoTDBO
import java.util.*

/**
 * Aquí es adonde agregaremos la definición de nuestro objeto estado. Mira a los "unit tests" en [IOUStateTests] para
 * instrucciones de como terminar la clase [EstadoTDBO].
 *
 * Elimina la propiedad "val data: String = "data" antes de comenzar las tareas de [EstadoTDBO].
 */
@BelongsToContract(ContratoTDBO::class)
data class EstadoTDBO(val cantidad: Amount<Currency>,
                      val prestamista: Party,
                      val deudor: Party,
                      val pagado: Amount<Currency> = Amount(0, cantidad.token),
                      override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    override val participants: List<Party> get() = listOf(prestamista, deudor)

    fun pagar(cantidadParaLiquidar: Amount<Currency>) = copy(pagado = pagado.plus(cantidadParaLiquidar))
    fun conNuevoPrestamista(nuevoPrestamista: Party) = copy(prestamista = nuevoPrestamista)
}