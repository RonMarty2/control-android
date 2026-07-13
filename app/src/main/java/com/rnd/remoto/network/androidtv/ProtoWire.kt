package com.rnd.remoto.network.androidtv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal hand-rolled protobuf wire-format encoder/decoder (no schema/codegen).
 * Only implements what's needed to talk polo.proto / remotemessage.proto:
 * varint and length-delimited fields.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun rawVarint(value: Long) {
        var v = value
        while (true) {
            val bits = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(bits)
                return
            }
            out.write(bits or 0x80)
        }
    }

    private fun tag(fieldNumber: Int, wireType: Int) {
        rawVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    fun writeVarintField(fieldNumber: Int, value: Long) {
        tag(fieldNumber, 0)
        rawVarint(value)
    }

    fun writeVarintField(fieldNumber: Int, value: Int) = writeVarintField(fieldNumber, value.toLong())

    fun writeBytesField(fieldNumber: Int, value: ByteArray) {
        tag(fieldNumber, 2)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun writeStringField(fieldNumber: Int, value: String) = writeBytesField(fieldNumber, value.toByteArray(Charsets.UTF_8))

    fun writeMessageField(fieldNumber: Int, message: ByteArray) = writeBytesField(fieldNumber, message)

    fun toByteArray(): ByteArray = out.toByteArray()
}

data class ProtoField(val number: Int, val wireType: Int, val raw: Any)

class ProtoReader(private val data: ByteArray) {
    private var pos = 0

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data[pos].toInt() and 0xFF
            pos++
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readFields(): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        while (pos < data.size) {
            val tag = readVarint()
            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            when (wireType) {
                0 -> fields.add(ProtoField(fieldNumber, wireType, readVarint()))
                2 -> {
                    val len = readVarint().toInt()
                    val bytes = data.copyOfRange(pos, pos + len)
                    pos += len
                    fields.add(ProtoField(fieldNumber, wireType, bytes))
                }
                1 -> { // fixed64
                    val bytes = data.copyOfRange(pos, pos + 8)
                    pos += 8
                    fields.add(ProtoField(fieldNumber, wireType, bytes))
                }
                5 -> { // fixed32
                    val bytes = data.copyOfRange(pos, pos + 4)
                    pos += 4
                    fields.add(ProtoField(fieldNumber, wireType, bytes))
                }
                else -> return fields // unknown wire type, stop rather than risk misreading position
            }
        }
        return fields
    }
}

fun List<ProtoField>.varint(fieldNumber: Int): Long? =
    firstOrNull { it.number == fieldNumber && it.wireType == 0 }?.raw as? Long

fun List<ProtoField>.bytes(fieldNumber: Int): ByteArray? =
    firstOrNull { it.number == fieldNumber && it.wireType == 2 }?.raw as? ByteArray

fun List<ProtoField>.submessage(fieldNumber: Int): List<ProtoField>? =
    bytes(fieldNumber)?.let { ProtoReader(it).readFields() }

fun List<ProtoField>.has(fieldNumber: Int): Boolean = any { it.number == fieldNumber }

/** Wire framing used by both polo (pairing) and remotemessage (control) streams: varint length + payload. */
object ProtoFraming {
    fun writeFramed(stream: OutputStream, message: ByteArray) {
        writeRawVarint(stream, message.size)
        stream.write(message)
        stream.flush()
    }

    fun readFramed(stream: InputStream): ByteArray {
        val len = readRawVarint(stream)
        val buffer = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = stream.read(buffer, read, len - read)
            if (n < 0) throw java.io.EOFException("Conexión cerrada mientras se leía un mensaje")
            read += n
        }
        return buffer
    }

    private fun writeRawVarint(stream: OutputStream, value: Int) {
        var v = value
        while (true) {
            val bits = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                stream.write(bits)
                return
            }
            stream.write(bits or 0x80)
        }
    }

    private fun readRawVarint(stream: InputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = stream.read()
            if (b < 0) throw java.io.EOFException("Conexión cerrada")
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
