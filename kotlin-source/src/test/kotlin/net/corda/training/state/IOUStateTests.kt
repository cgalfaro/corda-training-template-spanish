package net.corda.training.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.finance.*
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

/**
 * Instrucciones de ejercicio práctico.
 * Descomenta el primer unit test [hasIOUAmountFieldOfCorrectType] y luego ejecuta el unit test con la flecha verde
 * a la izquierda de la clase [IOUStateTests] o el método [hasIOUAmountFieldOfCorrectType].
 * Ejecutando las pruebas desde [IOUStateTests] ejecuta todos los unit tests definidos en la clase.
 * La primera prueba debería de fallar por que necesitas hacer algunos cambios al EstadoTDBO para que pase. Lea el TODO
 * bajo cada número de tarea para una descripción y un concejo de que es necesario hacer.
 * Una vez hayas la prueba pase, descomenta la siguiente prueba.
 * Continúa con todas las pruebas hasta que todas pasen.
 * Consejo: CMD / Ctrl + en los nombres color café en corchetes "[]" para su definición en el código base.
 */
class IOUStateTests {

    /**
     * Tarea 1.
     * TODO: Agregue una propiedad 'cantidad' de tipo [Amount] a la clase [EstadoTDBO] para que esta prueba pase.
     * Consejo: [Amount] es una clase plantilla que toma una clase como parámetro del token que quieras de [Amount].
     * Como estamos lidiando con dinero prestado de un participante a otro hace sentioo que nuestro token sea [Currency].
     */
    @Test
    fun hasIOUAmountFieldOfCorrectType() {
        // Does the amount field exist?
        EstadoTDBO::class.java.getDeclaredField("cantidad")
        // Is the amount field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("cantidad").type, Amount::class.java)
    }

    /**
     * Tarea 2.
     * TODO: Agregar 'prestamista' del tipo [Party] a la clase [EstadoTDBO] para que esta prueba pase.
     */
    @Test
    fun hasLenderFieldOfCorrectType() {
        // Does the lender field exist?
        EstadoTDBO::class.java.getDeclaredField("prestamista")
        // Is the lender field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("prestamista").type, Party::class.java)
    }

    /**
     * Tarea 3.
     * TODO: Agregar 'deudor' de tipo [Party] a la clase [EstadoTDBO] para que esta prueba pase.
     */
    @Test
    fun hasBorrowerFieldOfCorrectType() {
        // Does the borrower field exist?
        EstadoTDBO::class.java.getDeclaredField("deudor")
        // Is the borrower field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("deudor").type, Party::class.java)
    }

    /**
     * Tarea 4.
     * TODO: Agregar una propiedad 'pagado' de tipo [Amount] a la clase [EstadoTDBO] para que esta prueba pase.
     * Consejo:
     * - Queremos que esta propiedad sea iniciada con monto cero en la creación de [EstadoTDBO].
     * - Puedes usar [POUNDS] una extensión sobre [Int] para crear una cantidad de libras esterlina e.j. '10.POUNDS'.
     * - Esta propiedad lleva el registro de cuanto del inicial de [EstadoTDBO.amount] ha sido pagado por el deudor.
     * - Puedes iniciar una propiedad con un valor por defecto en Kotlin de la siguiente manera:
     *
     *       data class(val numero: Int = 10)
     *
     * - Necesitamos asegurarnos que la propiedad [EstadoTDBO.paid] sea de la misma moneda que la propiedad
     *   [EstadoTDBO.amount]. Puedes crear una instancia de la clase [Amount] que toma como valor cero y un token
     *   representando la moneda - que deberia ser la misma moneda que la propiedad [EstadoTDBO.amount].
     */
    @Test
    fun hasPaidFieldOfCorrectType() {
        // Does the paid field exist?
        EstadoTDBO::class.java.getDeclaredField("pagado")
        // Is the paid field of the correct type?
        assertEquals(EstadoTDBO::class.java.getDeclaredField("pagado").type, Amount::class.java)
    }

    /**
     * Task 5.
     * TODO: Include the lender within the [EstadoTDBO.participants] list
     * Hint: [listOf] takes any number of parameters and will add them to the list
     */
//    @Test
//    fun lenderIsParticipant() {
//        val iouState = IOUState(1.POUNDS, ALICE.party, BOB.party)
//        assertNotEquals(iouState.participants.indexOf(ALICE.party), -1)
//    }

    /**
     * Task 6.
     * TODO: Similar to the last task, include the borrower within the [EstadoTDBO.participants] list
     */
//    @Test
//    fun borrowerIsParticipant() {
//        val iouState = IOUState(1.POUNDS, ALICE.party, BOB.party)
//        assertNotEquals(iouState.participants.indexOf(BOB.party), -1)
//    }

    /**
     * Task 7.
     * TODO: Implement [LinearState] along with the required properties and methods.
     * Hint: [LinearState] implements [ContractState] which defines an additional property and method. You can use
     * IntellIJ to automatically add the member definitions for you or you can add them yourself. Look at the definition
     * of [LinearState] for what requires adding.
     */
//    @Test
//    fun isLinearState() {
//        assert(LinearState::class.java.isAssignableFrom(IOUState::class.java))
//    }

    /**
     * Task 8.
     * TODO: Override the [LinearState.linearId] property and assign it a value via your state's constructor.
     * Hint:
     * - The [LinearState.linearId] property is of type [UniqueIdentifier]. You need to create a new instance of
     * the [UniqueIdentifier] class.
     * - The [LinearState.linearId] is designed to link all [LinearState]s (which represent the state of an
     * agreement at a specific point in time) together. All the [LinearState]s with the same [LinearState.linearId]
     * represent the complete life-cycle to date of an agreement, asset or shared fact.
     * - Provide a default value for [linearId] for a new [EstadoTDBO]
     */
//    @Test
//    fun hasLinearIdFieldOfCorrectType() {
//        // Does the linearId field exist?
//        IOUState::class.java.getDeclaredField("linearId")
//        // Is the linearId field of the correct type?
//        assertEquals(IOUState::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
//    }

    /**
     * Task 9.
     * TODO: Ensure parameters are ordered correctly.
     * Hint: Make sure that the lender and borrower fields are not in the wrong order as this may cause some
     * confusion in subsequent tasks!
     */
//    @Test
//    fun checkIOUStateParameterOrdering() {
//        val fields = IOUState::class.java.declaredFields
//        val amountIdx = fields.indexOf(IOUState::class.java.getDeclaredField("amount"))
//        val lenderIdx = fields.indexOf(IOUState::class.java.getDeclaredField("lender"))
//        val borrowerIdx = fields.indexOf(IOUState::class.java.getDeclaredField("borrower"))
//        val paidIdx = fields.indexOf(IOUState::class.java.getDeclaredField("paid"))
//        val linearIdIdx = fields.indexOf(IOUState::class.java.getDeclaredField("linearId"))
//
//        assert(amountIdx < lenderIdx)
//        assert(lenderIdx < borrowerIdx)
//        assert(borrowerIdx < paidIdx)
//        assert(paidIdx < linearIdIdx)
//    }

    /**
     * Task 10.
     * TODO: Add a helper method called [pay] that can be called from an [EstadoTDBO] to settle an amount of the IOU.
     * Hint:
     * - You will need to increase the [EstadoTDBO.paid] property by the amount the borrower wishes to pay.
     * - Add a new function called [pay] in [EstadoTDBO]. This function will need to return an [EstadoTDBO].
     * - The existing state is immutable so a new state must be created from the existing state. Kotlin provides a [copy]
     * method which creates a new object with new values for specified fields.
     * - [copy] returns a copy of the object instance and the fields can be changed by specifying new values as
     * parameters to [copy]     */
//    @Test
//    fun checkPayHelperMethod() {
//        val iou = IOUState(10.DOLLARS, ALICE.party, BOB.party)
//        assertEquals(5.DOLLARS, iou.pay(5.DOLLARS).paid)
//        assertEquals(3.DOLLARS, iou.pay(1.DOLLARS).pay(2.DOLLARS).paid)
//        assertEquals(10.DOLLARS, iou.pay(5.DOLLARS).pay(3.DOLLARS).pay(2.DOLLARS).paid)
//    }

    /**
     * Task 11.
     * TODO: Add a helper method called [withNewLender] that can be called from an [EstadoTDBO] to change the IOU's lender.
     */
//    @Test
//    fun checkWithNewLenderHelperMethod() {
//        val iou = IOUState(10.DOLLARS, ALICE.party, BOB.party)
//        assertEquals(MINICORP.party, iou.withNewLender(MINICORP.party).lender)
//        assertEquals(MEGACORP.party, iou.withNewLender(MEGACORP.party).lender)
//    }
}
