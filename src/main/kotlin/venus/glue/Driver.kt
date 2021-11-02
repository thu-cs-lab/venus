package venus.glue

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import venus.assembler.Assembler
import venus.assembler.AssemblerError
import venus.linker.Linker
import venus.riscv.InstructionField
import venus.riscv.userStringToInt
import venus.simulator.Simulator

/**
 * The "driver" singleton which can be called from Javascript for all functionality.
 */
@JsExport @JsName("Driver") object Driver {
    lateinit var sim: Simulator
    private var timer: Int? = null

    /**
     * Run when the user clicks the "Simulator" tab.
     *
     * Assembles the text in the editor, and then renders the simulator.
     */
    @JsName("openSimulator") fun openSimulator() {
        val success = assemble(getText())
        if (success) Renderer.renderSimulator(sim)
    }

    /**
     * Opens and renders the editor.
     */
    @JsName("openEditor") fun openEditor() {
        runEnd()
        Renderer.renderEditor()
    }

    /**
     * Gets the text from the textarea editor.
     */
    internal fun getText(): String {
        val editor = document.getElementById("asm-editor") as HTMLTextAreaElement
        return editor.value
    }

    /**
     * Assembles and links the program, sets the simulator
     *
     * @param text the assembly code.
     */
    internal fun assemble(text: String): Boolean {
        val (prog, errors) = Assembler.assemble(text)
        if (errors.isNotEmpty()) {
            Renderer.displayError(errors.first())
            return false
        }
        try {
            val linked = Linker.link(listOf(prog))
            sim = Simulator(linked)
            return true
        } catch (e: AssemblerError) {
            Renderer.displayError(e)
            return false
        }
    }

    /**
     * Runs the simulator until it is done, or until the run button is pressed again.
     */
    @JsName("run") fun run() {
        if (currentlyRunning()) {
            runEnd()
        } else {
            Renderer.setRunButtonSpinning(true)
            timer = window.setTimeout(Driver::runStart, TIMEOUT_TIME)
            sim.step() // walk past breakpoint
        }
    }

    /**
     * Resets the simulator to its initial state
     */
    @JsName("reset") fun reset() {
        openSimulator()
    }

    @JsName("toggleBreakpoint") fun addBreakpoint(idx: Int) {
        val isBreakpoint = sim.toggleBreakpointAt(idx)
        Renderer.renderBreakpointAt(idx, isBreakpoint)
    }

    internal const val TIMEOUT_CYCLES = 100
    internal const val TIMEOUT_TIME = 10
    internal fun runStart() {
        var cycles = 0
        while (cycles < TIMEOUT_CYCLES) {
            if (sim.isDone() || sim.atBreakpoint()) {
                runEnd()
                Renderer.updateAll()
                return
            }

            sim.step()
            cycles++
        }

        timer = window.setTimeout(Driver::runStart, TIMEOUT_TIME)
    }

    internal fun runEnd() {
        Renderer.setRunButtonSpinning(false)
        timer?.let(window::clearTimeout)
        timer = null
    }

    /**
     * Runs the simulator for one step and renders any updates.
     */
    @JsName("step") fun step() {
        val diffs = sim.step()
        Renderer.updateFromDiffs(diffs)
        Renderer.updateControlButtons()
    }

    /**
     * Undo the last executed instruction and render any updates.
     */
    @JsName("undo") fun undo() {
        val diffs = sim.undo()
        Renderer.updateFromDiffs(diffs)
        Renderer.updateControlButtons()
    }

    /**
     * Change to memory tab.
     */
    @JsName("openMemoryTab") fun openMemoryTab() {
        Renderer.renderMemoryTab()
    }

    /**
     * Change to register tab.
     */
    @JsName("openRegisterTab") fun openRegisterTab() {
        Renderer.renderRegisterTab()
    }

    internal fun currentlyRunning(): Boolean = timer != null

    /**
     * Save a register's value
     */
    @JsName("saveRegister") fun saveRegister(reg: HTMLInputElement, id: Int) {
        if (!currentlyRunning()) {
            try {
                val input = reg.value
                sim.setRegNoUndo(id, userStringToInt(input))
            } catch (e: NumberFormatException) {
                /* do nothing */
            }
        }
        Renderer.updateRegister(id, sim.getReg(id))
    }

    @JsName("updateRegMemDisplay") fun updateRegMemDisplay() {
        Renderer.updateRegMemDisplay()
    }

    @JsName("moveMemoryJump") fun moveMemoryJump() = Renderer.moveMemoryJump()

    @JsName("moveMemoryUp") fun moveMemoryUp() = Renderer.moveMemoryUp()

    @JsName("moveMemoryDown") fun moveMemoryDown() = Renderer.moveMemoryDown()

    fun getInstructionDump(): String {
        val sb = StringBuilder()
        for (i in 0 until sim.linkedProgram.prog.insts.size) {
            val mcode = sim.linkedProgram.prog.insts[i]
            val hexRepresentation = Renderer.toHex(mcode[InstructionField.ENTIRE])
            sb.append(hexRepresentation.removePrefix("0x"))
            sb.append("\n")
        }
        return sb.toString()
    }

    @JsName("dump") fun dump() {
        Renderer.clearConsole()
        Renderer.printConsole(getInstructionDump())
        val ta = document.getElementById("console-output") as HTMLTextAreaElement
        ta.select()
        val success = document.execCommand("copy")
        if (success) {
            window.alert("Successfully copied machine code to clipboard")
        }
    }
}
