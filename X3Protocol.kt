package com.example.x3flight

/** X3 Header16 frame. Protocol commands need validation on real firmware. */
object X3Protocol {
    const val START_CAPTURE = 4
    const val STOP_CAPTURE = 5
    const val GET_CAPTURE_STATUS = 15
    fun packet(command: Int, sequence: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(sequence in 1..254)
        val b = ByteArray(16 + payload.size)
        b[0] = payload.size.toByte(); b[1] = (payload.size ushr 8).toByte()
        b[4] = 4; b[7] = command.toByte(); b[8] = (command ushr 8).toByte()
        b[9] = 2; b[10] = sequence.toByte(); b[13] = 0x80.toByte()
        payload.copyInto(b, 16)
        return b
    }
}
