package com.alabadla.internetcardledger.data.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.domain.SalePaymentRules
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

object BackupJsonCodec {
    private const val MAX_BACKUP_BYTES = 50L * 1024L * 1024L

    fun write(document: BackupDocument, output: OutputStream) {
        JsonWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name("format").value(document.format)
            writer.name("version").value(document.version.toLong())
            writer.name("createdAt").value(document.createdAt)
            writer.name("deviceId").value(document.deviceId)
            writer.name("customers").beginArray()
            document.customers.forEach { writer.writeCustomer(it) }
            writer.endArray()
            writer.name("sales").beginArray()
            document.sales.forEach { writer.writeSale(it) }
            writer.endArray()
            writer.name("payments").beginArray()
            document.payments.forEach { writer.writePayment(it) }
            writer.endArray()
            writer.endObject()
        }
    }

    @Throws(InvalidBackupException::class)
    fun readAndValidate(input: InputStream): BackupDocument {
        try {
            val reader = JsonReader(InputStreamReader(LimitedInputStream(input), StandardCharsets.UTF_8))
            reader.use {
                val document = it.readDocument()
                if (it.peek() != JsonToken.END_DOCUMENT) invalid("يحتوي الملف على بيانات إضافية غير صالحة.")
                validate(document)
                return document
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("ملف النسخة الاحتياطية تالف أو غير صالح.")
        }
    }

    fun validate(document: BackupDocument) {
        if (document.format != BACKUP_FORMAT_NAME) invalid("هذا الملف ليس نسخة احتياطية للتطبيق.")
        if (document.version !in 1..BACKUP_FORMAT_VERSION) invalid("إصدار النسخة الاحتياطية غير مدعوم.")
        if (document.createdAt <= 0L) invalid("تاريخ النسخة الاحتياطية غير صالح.")
        requireUuid(document.deviceId, "معرّف الجهاز")
        ensureUnique(document.customers.map { it.id }, "الزبائن")
        ensureUnique(document.sales.map { it.id }, "المبيعات")
        ensureUnique(document.payments.map { it.id }, "الدفعات")

        document.customers.forEach { customer ->
            validateCommon(customer.id, customer.createdAt, customer.updatedAt, customer.createdByDeviceId)
            if (customer.name.isBlank()) invalid("تحتوي النسخة على زبون بلا اسم.")
        }
        val customerIds = document.customers.mapTo(hashSetOf()) { it.id }
        document.sales.forEach { sale ->
            validateCommon(sale.id, sale.createdAt, sale.updatedAt, sale.createdByDeviceId)
            if (sale.description.isBlank() || sale.quantity <= 0 || sale.unitPriceMinor < 0L || sale.soldAt <= 0L) {
                invalid("تحتوي النسخة على سجل بيع غير صالح.")
            }
            if (runCatching { Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor) }.isFailure) {
                invalid("تحتوي النسخة على قيمة بيع أكبر من الحد المسموح.")
            }
            val total = sale.quantity.toLong() * sale.unitPriceMinor
            if (document.version >= 2 &&
                !SalePaymentRules.isConsistent(total, sale.paymentStatus, sale.paidAmountMinor)
            ) {
                invalid("تحتوي النسخة على حالة دفع أو نوع بطاقة غير صالح.")
            }
            if (sale.customerId != null && sale.customerId !in customerIds) {
                invalid("تحتوي النسخة على بيع مرتبط بزبون غير موجود.")
            }
        }
        val salesById = document.sales.associateBy { it.id }
        document.payments.forEach { payment ->
            validateCommon(payment.id, payment.createdAt, payment.updatedAt, payment.createdByDeviceId)
            if (payment.amountMinor <= 0L || payment.paidAt <= 0L) invalid("تحتوي النسخة على دفعة غير صالحة.")
            if (payment.customerId !in customerIds) invalid("تحتوي النسخة على دفعة مرتبطة بزبون غير موجود.")
            payment.saleId?.let { saleId ->
                val sale = salesById[saleId] ?: invalid("تحتوي النسخة على دفعة مرتبطة ببيع غير موجود.")
                if (sale.customerId != null && sale.customerId != payment.customerId) {
                    invalid("العلاقة بين الدفعة والبيع والزبون غير صالحة.")
                }
            }
        }
    }

    private fun validateCommon(id: String, createdAt: Long, updatedAt: Long, deviceId: String) {
        requireUuid(id, "معرّف السجل")
        requireUuid(deviceId, "معرّف جهاز إنشاء السجل")
        if (createdAt <= 0L || updatedAt < createdAt) invalid("تحتوي النسخة على توقيت سجل غير صالح.")
    }

    private fun ensureUnique(ids: List<String>, type: String) {
        if (ids.size != ids.toSet().size) invalid("تحتوي قائمة $type على معرّفات مكررة.")
    }

    private fun requireUuid(value: String, field: String) {
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            invalid("$field غير صالح.")
        }
    }

    private fun JsonReader.readDocument(): BackupDocument {
        var format: String? = null
        var version: Int? = null
        var createdAt: Long? = null
        var deviceId: String? = null
        var customers: List<CustomerEntity>? = null
        var sales: List<SaleEntity>? = null
        var payments: List<PaymentEntity>? = null
        beginObject()
        while (hasNext()) when (nextName()) {
            "format" -> format = nextString()
            "version" -> version = nextInt()
            "createdAt" -> createdAt = nextLong()
            "deviceId" -> deviceId = nextString()
            "customers" -> customers = readArray { readCustomer() }
            "sales" -> sales = readArray { readSale() }
            "payments" -> payments = readArray { readPayment() }
            else -> skipValue()
        }
        endObject()
        return BackupDocument(
            format = format ?: invalid("اسم تنسيق النسخة مفقود."),
            version = version ?: invalid("إصدار النسخة مفقود."),
            createdAt = createdAt ?: invalid("تاريخ النسخة مفقود."),
            deviceId = deviceId ?: invalid("معرّف الجهاز مفقود."),
            customers = customers ?: invalid("قائمة الزبائن مفقودة."),
            sales = sales ?: invalid("قائمة المبيعات مفقودة."),
            payments = payments ?: invalid("قائمة الدفعات مفقودة."),
        )
    }

    private fun JsonReader.readCustomer(): CustomerEntity {
        val values = readFields()
        return CustomerEntity(
            id = values.requiredString("id"), name = values.requiredString("name"),
            phone = values.nullableString("phone"), notes = values.nullableString("notes"),
            createdAt = values.requiredLong("createdAt"), updatedAt = values.requiredLong("updatedAt"),
            createdByDeviceId = values.requiredString("createdByDeviceId"),
            syncStatus = values.requiredStatus(), isDeleted = values.requiredBoolean("isDeleted"),
        )
    }

    private fun JsonReader.readSale(): SaleEntity {
        val values = readFields()
        val cardType = values.optionalString("cardType")?.let { value ->
            runCatching { CardType.valueOf(value) }.getOrElse { invalid("نوع البطاقة في النسخة غير صالح.") }
        } ?: CardType.SIX_HOURS
        val paymentStatus = values.optionalString("paymentStatus")?.let { value ->
            runCatching { PaymentStatus.valueOf(value) }.getOrElse { invalid("حالة الدفع في النسخة غير صالحة.") }
        } ?: PaymentStatus.UNPAID
        val quantity = values.requiredInt("quantity")
        val unitPriceMinor = values.requiredLong("unitPriceMinor")
        val paidAmountMinor = values.nullableLong("paidAmountMinor") ?: 0L
        return SaleEntity(
            id = values.requiredString("id"), customerId = values.nullableString("customerId"),
            description = values.requiredString("description"), quantity = quantity,
            unitPriceMinor = unitPriceMinor, cardType = cardType, paymentStatus = paymentStatus,
            paidAmountMinor = paidAmountMinor, soldAt = values.requiredLong("soldAt"),
            notes = values.nullableString("notes"), createdAt = values.requiredLong("createdAt"),
            updatedAt = values.requiredLong("updatedAt"),
            createdByDeviceId = values.requiredString("createdByDeviceId"),
            syncStatus = values.requiredStatus(), isDeleted = values.requiredBoolean("isDeleted"),
        )
    }

    private fun JsonReader.readPayment(): PaymentEntity {
        val values = readFields()
        return PaymentEntity(
            id = values.requiredString("id"), customerId = values.requiredString("customerId"),
            saleId = values.nullableString("saleId"), amountMinor = values.requiredLong("amountMinor"),
            paidAt = values.requiredLong("paidAt"), notes = values.nullableString("notes"),
            createdAt = values.requiredLong("createdAt"), updatedAt = values.requiredLong("updatedAt"),
            createdByDeviceId = values.requiredString("createdByDeviceId"),
            syncStatus = values.requiredStatus(), isDeleted = values.requiredBoolean("isDeleted"),
        )
    }

    private fun JsonReader.readFields(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            result[name] = when (peek()) {
                JsonToken.NULL -> { nextNull(); null }
                JsonToken.STRING -> nextString()
                JsonToken.NUMBER -> nextString()
                JsonToken.BOOLEAN -> nextBoolean()
                else -> { skipValue(); Unit }
            }
        }
        endObject()
        return result
    }

    private inline fun <T> JsonReader.readArray(readItem: JsonReader.() -> T): List<T> = buildList {
        beginArray()
        while (hasNext()) add(readItem())
        endArray()
    }

    private fun JsonWriter.writeCommon(
        id: String, createdAt: Long, updatedAt: Long, createdByDeviceId: String,
        syncStatus: SyncStatus, isDeleted: Boolean,
    ) {
        name("id").value(id)
        name("createdAt").value(createdAt)
        name("updatedAt").value(updatedAt)
        name("createdByDeviceId").value(createdByDeviceId)
        name("syncStatus").value(syncStatus.name)
        name("isDeleted").value(isDeleted)
    }

    private fun JsonWriter.writeCustomer(value: CustomerEntity) {
        beginObject()
        writeCommon(value.id, value.createdAt, value.updatedAt, value.createdByDeviceId, value.syncStatus, value.isDeleted)
        name("name").value(value.name); name("phone").value(value.phone); name("notes").value(value.notes)
        endObject()
    }

    private fun JsonWriter.writeSale(value: SaleEntity) {
        beginObject()
        writeCommon(value.id, value.createdAt, value.updatedAt, value.createdByDeviceId, value.syncStatus, value.isDeleted)
        name("customerId").value(value.customerId); name("description").value(value.description)
        name("quantity").value(value.quantity.toLong()); name("unitPriceMinor").value(value.unitPriceMinor)
        name("cardType").value(value.cardType.name); name("paymentStatus").value(value.paymentStatus.name)
        name("paidAmountMinor").value(value.paidAmountMinor)
        name("soldAt").value(value.soldAt); name("notes").value(value.notes)
        endObject()
    }

    private fun JsonWriter.writePayment(value: PaymentEntity) {
        beginObject()
        writeCommon(value.id, value.createdAt, value.updatedAt, value.createdByDeviceId, value.syncStatus, value.isDeleted)
        name("customerId").value(value.customerId); name("saleId").value(value.saleId)
        name("amountMinor").value(value.amountMinor); name("paidAt").value(value.paidAt); name("notes").value(value.notes)
        endObject()
    }

    private fun Map<String, Any?>.requiredString(name: String): String =
        this[name] as? String ?: invalid("الحقل $name مفقود أو غير صالح.")

    private fun Map<String, Any?>.nullableString(name: String): String? {
        if (!containsKey(name)) invalid("الحقل $name مفقود.")
        return this[name] as? String
    }

    private fun Map<String, Any?>.optionalString(name: String): String? = this[name] as? String

    private fun Map<String, Any?>.nullableLong(name: String): Long? {
        if (!containsKey(name)) return null
        val str = this[name] as? String ?: return null
        return str.toLongOrNull()
    }

    private fun Map<String, Any?>.requiredLong(name: String): Long =
        requiredString(name).toLongOrNull() ?: invalid("الحقل $name غير صالح.")

    private fun Map<String, Any?>.requiredInt(name: String): Int =
        requiredString(name).toIntOrNull() ?: invalid("الحقل $name غير صالح.")

    private fun Map<String, Any?>.requiredBoolean(name: String): Boolean =
        this[name] as? Boolean ?: invalid("الحقل $name مفقود أو غير صالح.")

    private fun Map<String, Any?>.requiredStatus(): SyncStatus = try {
        SyncStatus.valueOf(requiredString("syncStatus"))
    } catch (_: IllegalArgumentException) {
        invalid("حالة المزامنة في النسخة غير صالحة.")
    }

    private class LimitedInputStream(input: InputStream) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) checked(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) checked(it) }
        private fun checked(amount: Int) {
            count += amount
            if (count > MAX_BACKUP_BYTES) invalid("ملف النسخة الاحتياطية أكبر من الحد المسموح.")
        }
    }

    private fun invalid(message: String): Nothing = throw InvalidBackupException(message)
}
