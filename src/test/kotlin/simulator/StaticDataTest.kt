package venus.simulator

import kotlin.test.Test
import kotlin.test.assertEquals
import venus.assembler.Assembler
import venus.linker.Linker
import venus.riscv.MemorySegments

class StaticDataTest {
    @Test fun easyManualLoad() {
        val (prog, _) = Assembler.assemble("""
        .data
        .byte 1 2 3 4
        .text
        nop
        """)
        val linked = Linker.link(listOf(prog))
        val sim = Simulator(linked)
        assertEquals(1, sim.loadByte(MemorySegments.STATIC_BEGIN))
        assertEquals(2, sim.loadByte(MemorySegments.STATIC_BEGIN + 1))
        assertEquals(3, sim.loadByte(MemorySegments.STATIC_BEGIN + 2))
        assertEquals(4, sim.loadByte(MemorySegments.STATIC_BEGIN + 3))
    }

    @Test fun asciizNulTerminated() {
        val (prog, _) = Assembler.assemble("""
        .data
        .asciiz "a"
        .asciiz "b"
        """)
        val linked = Linker.link(listOf(prog))
        val sim = Simulator(linked)
        val offset = MemorySegments.STATIC_BEGIN
        assertEquals('a'.code, sim.loadByte(offset))
        assertEquals(0, sim.loadByte(offset + 1))
        assertEquals('b'.code, sim.loadByte(offset + 2))
        assertEquals(0, sim.loadByte(offset + 3))
    }

    @Test fun linkedStaticBytes() {
        val (prog1, _) = Assembler.assemble("""
        .data
        .byte 1
        """)
        val (prog2, _) = Assembler.assemble("""
        .data
        .byte 2
        """)
        val linked = Linker.link(listOf(prog1, prog2))
        val sim = Simulator(linked)
        val offset = MemorySegments.STATIC_BEGIN
        assertEquals(1, sim.loadByte(offset))
        assertEquals(2, sim.loadByte(offset + 1))
    }

    @Test fun wordManualLoad() {
        val (prog, _) = Assembler.assemble("""
        .data
        .word -21231234
        .text
        nop
        """)
        val linked = Linker.link(listOf(prog))
        val sim = Simulator(linked)
        assertEquals(-21231234, sim.loadWord(MemorySegments.STATIC_BEGIN))
    }
}
