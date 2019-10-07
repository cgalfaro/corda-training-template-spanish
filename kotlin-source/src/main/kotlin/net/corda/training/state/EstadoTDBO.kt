package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.training.contract.IOUContract
import java.util.*

/**
 * Aquí es adonde agregaremos la definición de nuestro objeto estado. Mira a los "unit tests" en [IOUStateTests] para
 * instrucciones de como terminar la clase [EstadoTDBO].
 *
 * Elimina la propiedad "val data: String = "data" antes de comenzar las tareas de [EstadoTDBO].
 */
@BelongsToContract(IOUContract::class)
data class EstadoTDBO(val cantidad: Amount<Currency>,
                      val prestamista: Party,
                      val deudor: Party,
                      val pagado: Amount<Currency> = Amount(0, cantidad.token)): ContractState {
    override val participants: List<Party> get() = listOf()
}