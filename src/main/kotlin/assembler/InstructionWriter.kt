package venus.assembler

import venus.riscv.Instruction
import venus.riscv.InstructionFormat

abstract class InstructionWriter {
    operator fun invoke(prog: Program, iform: InstructionFormat, args: List<String>) {
        val inst = Instruction(0)
        iform.ifields.forEach({ inst.setField(it.ifield, it.required) })
        this(prog, inst, args)
    }

    abstract operator fun invoke(prog: Program, inst: Instruction, args: List<String>)
}