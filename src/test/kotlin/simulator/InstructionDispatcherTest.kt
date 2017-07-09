package venus.simulator

import org.junit.Test
import kotlin.test.assertEquals
import venus.riscv.Instruction

class InstructionDispatcherTest {
    @Test
    fun correctDispatchAdd() {
        // add x3 x1 x2
        val inst = Instruction(0b00000000000100010000000110110011)
        val impl = InstructionDispatcher.dispatch(inst)
        assertEquals(impl, InstructionDispatcher.add.implementation)
    }

    @Test
    fun basicDispatchWorks() {
        // add x3 x1 x2
        val inst = Instruction(0b00000000000100010000000110110011)
        val impl = InstructionDispatcher.dispatch(inst)
        val state = SimulatorState()
        state.setReg(1, 10)
        state.setReg(2, 20)
        impl!!(inst, state)
        assertEquals(30, state.getReg(3))
    }

}