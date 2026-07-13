package com.rnd.remoto.network.androidtv

/**
 * Message builders for the "polo" pairing protocol (polo.proto), based on
 * https://github.com/tronikos/androidtvremote2 (src/androidtvremote2/polo.proto + pairing.py).
 */
object PoloMessages {
    const val STATUS_OK = 200

    const val FIELD_STATUS = 2
    const val FIELD_PAIRING_REQUEST_ACK = 11
    const val FIELD_OPTIONS = 20
    const val FIELD_CONFIGURATION_ACK = 31
    const val FIELD_SECRET_ACK = 41

    private fun outerMessage(bodyFieldNumber: Int, body: ByteArray): ByteArray = ProtoWriter().apply {
        writeVarintField(1, 2) // protocol_version = 2 (required field, always sent explicitly)
        writeVarintField(2, STATUS_OK) // status = STATUS_OK
        writeMessageField(bodyFieldNumber, body)
    }.toByteArray()

    fun pairingRequest(clientName: String): ByteArray {
        val body = ProtoWriter().apply {
            writeStringField(1, "atvremote") // service_name
            writeStringField(2, clientName)  // client_name
        }.toByteArray()
        return outerMessage(10, body)
    }

    fun options(): ByteArray {
        val encoding = ProtoWriter().apply {
            writeVarintField(1, 3) // EncodingType.HEXADECIMAL
            writeVarintField(2, 6) // symbol_length
        }.toByteArray()
        val body = ProtoWriter().apply {
            writeMessageField(1, encoding) // input_encodings (repeated; we send one)
            writeVarintField(3, 1) // preferred_role = ROLE_TYPE_INPUT
        }.toByteArray()
        return outerMessage(20, body)
    }

    fun configuration(): ByteArray {
        val encoding = ProtoWriter().apply {
            writeVarintField(1, 3) // EncodingType.HEXADECIMAL
            writeVarintField(2, 6) // symbol_length
        }.toByteArray()
        val body = ProtoWriter().apply {
            writeMessageField(1, encoding) // encoding
            writeVarintField(2, 1) // client_role = ROLE_TYPE_INPUT
        }.toByteArray()
        return outerMessage(30, body)
    }

    fun secret(secretBytes: ByteArray): ByteArray {
        val body = ProtoWriter().apply {
            writeBytesField(1, secretBytes)
        }.toByteArray()
        return outerMessage(40, body)
    }
}
