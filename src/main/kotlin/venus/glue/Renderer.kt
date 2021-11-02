package venus.glue
/* ktlint-disable no-wildcard-imports */

import org.w3c.dom.*
import venus.assembler.AssemblerError
import venus.riscv.InstructionField
import venus.riscv.MachineCode
import venus.riscv.MemorySegments
import venus.riscv.insts.dsl.Instruction
import venus.simulator.*
import kotlin.browser.document

/* ktlint-enable no-wildcard-imports */

/**
 * This singleton is used to render different parts of the screen, it serves as an interface between the UI and the
 * internal simulator.
 *
 * @todo break this up into multiple objects
 */
internal object Renderer {
    /** The register currently being highlighted */
    private var activeRegister: HTMLElement? = null
    /** The instruction currently being highlighted */
    private var activeInstruction: HTMLElement? = null
    /** The memory location currently centered */
    private var activeMemoryAddress: Int = 0
    /** The simulator being rendered */
    private lateinit var sim: Simulator
    /* The way the information in the registers is displayed*/
    private var displayType = "hex"

    /**
     * Shows the simulator tab and hides other tabs
     *
     * @param displaySim the simulator to show
     */
    fun renderSimulator(displaySim: Simulator) {
        tabSetVisibility("simulator", "block")
        tabSetVisibility("editor", "none")
        sim = displaySim
        setRunButtonSpinning(false)
        renderProgramListing()
        clearConsole()
        updateAll()
    }

    /** Shows the editor tab and hides other tabs */
    fun renderEditor() {
        tabSetVisibility("simulator", "none")
        tabSetVisibility("editor", "block")
    }

    /**
     * Sets the tab to the desired visiblity.
     *
     * Also updates the highlighted tab at the top.
     *
     * @param tab the name of the tab (currently "editor" or "simulator")
     */
    private fun tabSetVisibility(tab: String, display: String) {
        val tabView = document.getElementById("$tab-tab-view") as HTMLElement
        val tabDisplay = document.getElementById("$tab-tab") as HTMLElement
        tabView.style.display = display
        if (display == "none") {
            tabDisplay.classList.remove("is-active")
        } else {
            tabDisplay.classList.add("is-active")
        }
    }

    /** Display a given [AssemblerError] */
    @Suppress("UNUSED_PARAMETER") fun displayError(e: AssemblerError) {
        js("alert(e.message)")
    }

    /**
     * Renders the program listing under the debugger
     */
    private fun renderProgramListing() {
        clearProgramListing()
        for (i in 0 until sim.linkedProgram.prog.insts.size) {
            val programDebug = sim.linkedProgram.dbg[i]
            val (_, dbg) = programDebug
            val (_, line) = dbg
            val mcode = sim.linkedProgram.prog.insts[i]
            addToProgramListing(i, mcode, line)
        }
    }

    /**
     * Refresh all of the simulator tab's content
     *
     * @todo refactor this into a "reset" and "update" all function
     */
    fun updateAll() {
        updatePC(sim.getPC())
        updateMemory(activeMemoryAddress)
        updateControlButtons()
        for (i in 0..31) {
            updateRegister(i, sim.getReg(i))
        }
    }

    /**
     * Updates the view by applying each individual diff.
     *
     * @param diffs the list of diffs to apply
     */
    fun updateFromDiffs(diffs: List<Diff>) {
        for (diff in diffs) {
            when (diff) {
                is RegisterDiff -> updateRegister(diff.id, diff.v, true)
                is PCDiff -> updatePC(diff.pc)
                is MemoryDiff -> updateMemory(diff.addr)
                is HeapSpaceDiff -> { /* do nothing */ }
            }
        }
    }

    /**
     * Clears the current program listing.
     *
     * @todo find a less hacky way to do this?
     */
    fun clearProgramListing() {
        getElement("program-listing-body").innerHTML = ""
    }

    /**
     * Adds an instruction with the given index to the program listing.
     *
     * @param idx the index of the instruction
     * @param mcode the machine code representation of the instruction
     * @param progLine the original assembly code
     */
    fun addToProgramListing(idx: Int, mcode: MachineCode, progLine: String) {
        val programTable = getElement("program-listing-body") as HTMLTableSectionElement

        val newRow = programTable.insertRow() as HTMLTableRowElement
        newRow.id = "instruction-$idx"
        newRow.onclick = { Driver.addBreakpoint(idx) }

        val hexRepresention = toHex(mcode[InstructionField.ENTIRE])
        val machineCode = newRow.insertCell(0)
        val machineCodeText = document.createTextNode(hexRepresention)
        machineCode.appendChild(machineCodeText)

        val basicCode = newRow.insertCell(1)
        val basicCodeText = document.createTextNode(Instruction[mcode].disasm(mcode))
        basicCode.appendChild(basicCodeText)

        val line = newRow.insertCell(2)
        val lineText = document.createTextNode(progLine)
        line.appendChild(lineText)
    }

    /**
     * Gets the element with a given ID
     *
     * @param id the id of the desired element
     *
     * @returns the HTML element corresponding to the given ID
     * @throws ClassCastException if the element is not an [HTMLElement] or does not exist
     */
    fun getElement(id: String): HTMLElement = document.getElementById(id) as HTMLElement

    /**
     * Updates the register with the given id and value.
     *
     * @param id the ID of the register (e.g., x13 has ID 13)
     * @param value the new value of the register
     * @param setActive whether the register should be set to the active register (i.e., highlighted for the user)
     */
    fun updateRegister(id: Int, value: Int, setActive: Boolean = false) {
        val register = getElement("reg-$id-val") as HTMLInputElement
        register.value = when (displayType) {
            "Hex" -> toHex(value)
            "Decimal" -> value.toString()
            "Unsigned" -> toUnsigned(value)
            "ASCII" -> toAscii(value)
            else -> toHex(value)
        }
        if (setActive) {
            activeRegister?.classList?.remove("is-modified")
            register.classList.add("is-modified")
            activeRegister = register
        }
    }

    /**
     * Updates the PC to the given value. It also highlights the to-be-executed instruction.
     *
     * @param pc the new PC
     * @todo abstract away instruction length
     */
    fun updatePC(pc: Int) {
        val idx = pc / 4
        activeInstruction?.classList?.remove("is-selected")
        val newActiveInstruction = document.getElementById("instruction-$idx") as HTMLElement?
        newActiveInstruction?.classList?.add("is-selected")
        newActiveInstruction?.scrollIntoView(false)
        activeInstruction = newActiveInstruction
    }

    /**
     * Prints the given thing to the console as a string.
     *
     * @param thing the thing to print
     */
    internal fun printConsole(thing: Any) {
        val console = getElement("console-output") as HTMLTextAreaElement
        console.value += thing.toString()
    }

    /**
     * Clears the console
     */
    fun clearConsole() {
        val console = getElement("console-output") as HTMLTextAreaElement
        console.value = ""
    }

    /**
     * Sets whether the run button is spinning.
     *
     * @param spinning whether the button should be spin
     */
    fun setRunButtonSpinning(spinning: Boolean) {
        val runButton = getElement("simulator-run")
        if (spinning) {
            runButton.classList.add("is-loading")
            disableControlButtons()
        } else {
            runButton.classList.remove("is-loading")
            updateControlButtons()
        }
    }

    /**
     * Sets whether a button is disabled.
     *
     * @param id the id of the button to change
     * @param disabled whether or not to disable the button
     */
    private fun setButtonDisabled(id: String, disabled: Boolean) {
        val button = getElement(id) as HTMLButtonElement
        button.disabled = disabled
    }

    /**
     * Renders the control buttons to be enabled / disabled appropriately.
     */
    fun updateControlButtons() {
        setButtonDisabled("simulator-reset", !sim.canUndo())
        setButtonDisabled("simulator-undo", !sim.canUndo())
        setButtonDisabled("simulator-step", sim.isDone())
        setButtonDisabled("simulator-run", sim.isDone())
    }

    /**
     * Disables the step, undo and reset buttons.
     *
     * Used while running, see [Driver.runStart].
     */
    fun disableControlButtons() {
        setButtonDisabled("simulator-reset", true)
        setButtonDisabled("simulator-undo", true)
        setButtonDisabled("simulator-step", true)
    }

    /**
     * Renders a change in breakpoint status
     *
     * @param idx the index to render
     * @param state whether or not there is a breakpoint
     */
    fun renderBreakpointAt(idx: Int, state: Boolean) {
        val row = getElement("instruction-$idx")
        if (state) {
            row.classList.add("is-breakpoint")
        } else {
            row.classList.remove("is-breakpoint")
        }
    }

    /**
     * Number of rows to show around the current address
     */
    const val MEMORY_CONTEXT = 6

    /** Show the memory sidebar tab */
    fun renderMemoryTab() {
        tabSetVisibility("memory", "block")
        tabSetVisibility("register", "none")
    }

    /** Show the register sidebar tab */
    fun renderRegisterTab() {
        tabSetVisibility("register", "block")
        tabSetVisibility("memory", "none")
    }

    /**
     * Update the [MEMORY_CONTEXT] words above and below the given address.
     *
     * Does not shift the memory display if it can be avoided
     *
     * @param addr the address to update around
     */
    private fun updateMemory(addr: Int) {
        val wordAddress = (addr shr 2) shl 2
        if (mustMoveMemoryDisplay(wordAddress)) {
            activeMemoryAddress = wordAddress
        }

        for (rowIdx in -MEMORY_CONTEXT..MEMORY_CONTEXT) {
            val row = getElement("mem-row-$rowIdx")
            val rowAddr = activeMemoryAddress + 4 * rowIdx
            renderMemoryRow(row, rowAddr)
        }
    }

    /**
     * Determines if we need to move the memory display to show the address
     *
     * @param wordAddress the address we want to show
     * @return true if we need to move the display
     */
    private fun mustMoveMemoryDisplay(wordAddress: Int) =
            (activeMemoryAddress - wordAddress) shr 2 !in -MEMORY_CONTEXT..MEMORY_CONTEXT

    /**
     * Renders a row of the memory.
     *
     * @param row the HTML element of the row to render
     * @param rowAddr the new address of that row
     */
    private fun renderMemoryRow(row: HTMLElement, rowAddr: Int) {
        val tdAddress = row.childNodes[0] as HTMLTableCellElement
        if (rowAddr >= 0) {
            tdAddress.innerText = toHex(rowAddr)
            for (i in 1..4) {
                val tdByte = row.childNodes[i] as HTMLTableCellElement
                val byte = sim.loadByte(rowAddr + i - 1)
                tdByte.innerText = when (displayType) {
                    "Hex" -> byteToHex(byte)
                    "Decimal" -> byteToDec(byte)
                    "Unsigned" -> byteToUnsign(byte)
                    "ASCII" -> toAscii(byte)
                    else -> byteToHex(byte)
                }
            }
        } else {
            tdAddress.innerText = "----------"
            for (i in 1..4) {
                val tdByte = row.childNodes[i] as HTMLTableCellElement
                tdByte.innerText = "--"
            }
        }
    }

    /** a map from integers to the corresponding hex digits */
    private val hexMap = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f')

    /**
     * Convert a certain byte to hex
     *
     * @param b the byte to convert
     * @return a hex string for the byte
     *
     * @throws IndexOutOfBoundsException if b is not in -127..255
     */
    private fun byteToHex(b: Int): String {
        val leftNibble = hexMap[b ushr 4]
        val rightNibble = hexMap[b and 15]
        return "$leftNibble$rightNibble"
    }

    private fun byteToDec(b: Int): String = b.toByte().toString()

    private fun byteToUnsign(b: Int): String = b.toString()

    /**
     * Converts a value to a two's complement hex number.
     *
     * By two's complement, I mean that -1 becomes 0xFFFFFFFF not -0x1.
     *
     * @param value the value to convert
     * @return the hexadecimal string corresponding to that value
     * @todo move this?
     */
    fun toHex(value: Int): String {
        var remainder = value.toLong()
        var suffix = ""

        repeat(8) {
            val hexDigit = hexMap[(remainder and 15).toInt()]
            suffix = hexDigit + suffix
            remainder = remainder ushr 4
        }

        return "0x" + suffix
    }

    private fun toUnsigned(value: Int): String =
            if (value >= 0) value.toString() else (value + 0x1_0000_0000L).toString()

    private fun toAscii(value: Int): String =
            when (value) {
                !in 0..255 -> toHex(value)
                !in 32..126 -> "\uFFFD"
                else -> "'${value.toChar()}'"
            }

    /**
     * Sets the display type for all of the registers and memory
     * Rerenders after
     */
    fun updateRegMemDisplay() {
        val displaySelect = getElement("display-settings") as HTMLSelectElement
        displayType = displaySelect.value
        updateAll()
    }

    fun moveMemoryJump() {
        val jumpSelect = getElement("address-jump") as HTMLSelectElement
        val where = jumpSelect.value
        activeMemoryAddress = when (where) {
            "Text" -> MemorySegments.TEXT_BEGIN
            "Data" -> MemorySegments.STATIC_BEGIN
            "Heap" -> MemorySegments.HEAP_BEGIN
            "Stack" -> MemorySegments.STACK_BEGIN
            else -> MemorySegments.TEXT_BEGIN
        }
        updateMemory(activeMemoryAddress)
        jumpSelect.selectedIndex = 0
    }

    private fun moveMemoryBy(rows: Int) {
        val bytes = 4 * rows
        if (activeMemoryAddress + bytes < 0) return
        activeMemoryAddress += bytes
        updateMemory(activeMemoryAddress)
    }

    fun moveMemoryUp() = moveMemoryBy(MEMORY_CONTEXT)
    fun moveMemoryDown() = moveMemoryBy(-MEMORY_CONTEXT)
}
