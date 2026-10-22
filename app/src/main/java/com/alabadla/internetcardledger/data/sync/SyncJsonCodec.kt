package com.alabadla.internetcardledger.data.sync

import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import org.json.JSONArray
import org.json.JSONObject

object SyncJsonCodec {

    fun customerToJson(customer: CustomerEntity): String = JSONObject().apply {
        put("id", customer.id)
        put("name", customer.name)
        put("phone", customer.phone ?: JSONObject.NULL)
        put("notes", customer.notes ?: JSONObject.NULL)
        put("createdAt", customer.createdAt)
        put("updatedAt", customer.updatedAt)
        put("createdByDeviceId", customer.createdByDeviceId)
        put("syncStatus", customer.syncStatus.name)
        put("isDeleted", customer.isDeleted)
    }.toString()

    fun customerFromJson(jsonStr: String): CustomerEntity {
        val json = JSONObject(jsonStr)
        return CustomerEntity(
            id = json.getString("id"),
            name = json.getString("name"),
            phone = if (json.isNull("phone")) null else json.getString("phone"),
            notes = if (json.isNull("notes")) null else json.getString("notes"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            createdByDeviceId = json.getString("createdByDeviceId"),
            syncStatus = SyncStatus.valueOf(json.getString("syncStatus")),
            isDeleted = json.getBoolean("isDeleted"),
        )
    }

    fun saleToJson(sale: SaleEntity): String = JSONObject().apply {
        put("id", sale.id)
        put("customerId", sale.customerId ?: JSONObject.NULL)
        put("description", sale.description)
        put("quantity", sale.quantity)
        put("unitPriceMinor", sale.unitPriceMinor)
        put("cardType", sale.cardType.name)
        put("paymentStatus", sale.paymentStatus.name)
        put("paidAmountMinor", sale.paidAmountMinor)
        put("soldAt", sale.soldAt)
        put("notes", sale.notes ?: JSONObject.NULL)
        put("createdAt", sale.createdAt)
        put("updatedAt", sale.updatedAt)
        put("createdByDeviceId", sale.createdByDeviceId)
        put("syncStatus", sale.syncStatus.name)
        put("isDeleted", sale.isDeleted)
    }.toString()

    fun saleFromJson(jsonStr: String): SaleEntity {
        val json = JSONObject(jsonStr)
        val cardType = CardType.valueOf(json.optString("cardType", "SIX_HOURS"))
        val paymentStatus = PaymentStatus.valueOf(json.optString("paymentStatus", "UNPAID"))
        val quantity = json.getInt("quantity")
        val unitPriceMinor = json.getLong("unitPriceMinor")
        val paidAmountMinor = if (json.has("paidAmountMinor")) json.getLong("paidAmountMinor") else 0L
        return SaleEntity(
            id = json.getString("id"),
            customerId = if (json.isNull("customerId")) null else json.getString("customerId"),
            description = json.getString("description"),
            quantity = quantity,
            unitPriceMinor = unitPriceMinor,
            cardType = cardType,
            paymentStatus = paymentStatus,
            paidAmountMinor = paidAmountMinor,
            soldAt = json.getLong("soldAt"),
            notes = if (json.isNull("notes")) null else json.getString("notes"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            createdByDeviceId = json.getString("createdByDeviceId"),
            syncStatus = SyncStatus.valueOf(json.getString("syncStatus")),
            isDeleted = json.getBoolean("isDeleted"),
        )
    }

    fun paymentToJson(payment: PaymentEntity): String = JSONObject().apply {
        put("id", payment.id)
        put("customerId", payment.customerId)
        put("saleId", payment.saleId ?: JSONObject.NULL)
        put("amountMinor", payment.amountMinor)
        put("paidAt", payment.paidAt)
        put("notes", payment.notes ?: JSONObject.NULL)
        put("createdAt", payment.createdAt)
        put("updatedAt", payment.updatedAt)
        put("createdByDeviceId", payment.createdByDeviceId)
        put("syncStatus", payment.syncStatus.name)
        put("isDeleted", payment.isDeleted)
    }.toString()

    fun paymentFromJson(jsonStr: String): PaymentEntity {
        val json = JSONObject(jsonStr)
        return PaymentEntity(
            id = json.getString("id"),
            customerId = json.getString("customerId"),
            saleId = if (json.isNull("saleId")) null else json.getString("saleId"),
            amountMinor = json.getLong("amountMinor"),
            paidAt = json.getLong("paidAt"),
            notes = if (json.isNull("notes")) null else json.getString("notes"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            createdByDeviceId = json.getString("createdByDeviceId"),
            syncStatus = SyncStatus.valueOf(json.getString("syncStatus")),
            isDeleted = json.getBoolean("isDeleted"),
        )
    }

    fun changeLogToJson(changeLog: SyncChangeLogEntity): JSONObject = JSONObject().apply {
        put("id", changeLog.id)
        put("recordId", changeLog.recordId)
        put("entityType", changeLog.entityType)
        put("operationType", changeLog.operationType)
        put("deviceId", changeLog.deviceId)
        put("sequenceNumber", changeLog.sequenceNumber)
        put("createdAt", changeLog.createdAt)
        put("updatedAt", changeLog.updatedAt)
        put("syncStatus", changeLog.syncStatus.name)
        put("payloadJson", changeLog.payloadJson)
    }

    fun changeLogFromJson(json: JSONObject): SyncChangeLogEntity = SyncChangeLogEntity(
        id = json.getString("id"),
        recordId = json.getString("recordId"),
        entityType = json.getString("entityType"),
        operationType = json.getString("operationType"),
        deviceId = json.getString("deviceId"),
        sequenceNumber = json.getLong("sequenceNumber"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
        syncStatus = SyncStatus.valueOf(json.getString("syncStatus")),
        payloadJson = json.getString("payloadJson"),
    )

    fun batchToJson(batch: SyncBatch): String = JSONObject().apply {
        put("batchId", batch.batchId)
        put("senderDeviceId", batch.senderDeviceId)
        put("senderDeviceName", batch.senderDeviceName)
        put("senderRole", batch.senderRole.name)
        put("createdAt", batch.createdAt)
        put("maxSequenceNumber", batch.maxSequenceNumber)

        val changesArr = JSONArray()
        batch.changes.forEach { changesArr.put(changeLogToJson(it)) }
        put("changes", changesArr)

        val customersArr = JSONArray()
        batch.customers.forEach { customersArr.put(JSONObject(customerToJson(it))) }
        put("customers", customersArr)

        val salesArr = JSONArray()
        batch.sales.forEach { salesArr.put(JSONObject(saleToJson(it))) }
        put("sales", salesArr)

        val paymentsArr = JSONArray()
        batch.payments.forEach { paymentsArr.put(JSONObject(paymentToJson(it))) }
        put("payments", paymentsArr)
    }.toString()

    fun batchFromJson(jsonStr: String): SyncBatch {
        val json = JSONObject(jsonStr)

        val changesArr = json.getJSONArray("changes")
        val changes = mutableListOf<SyncChangeLogEntity>()
        for (i in 0 until changesArr.length()) {
            changes.add(changeLogFromJson(changesArr.getJSONObject(i)))
        }

        val customersArr = json.getJSONArray("customers")
        val customers = mutableListOf<CustomerEntity>()
        for (i in 0 until customersArr.length()) {
            customers.add(customerFromJson(customersArr.getJSONObject(i).toString()))
        }

        val salesArr = json.getJSONArray("sales")
        val sales = mutableListOf<SaleEntity>()
        for (i in 0 until salesArr.length()) {
            sales.add(saleFromJson(salesArr.getJSONObject(i).toString()))
        }

        val paymentsArr = json.getJSONArray("payments")
        val payments = mutableListOf<PaymentEntity>()
        for (i in 0 until paymentsArr.length()) {
            payments.add(paymentFromJson(paymentsArr.getJSONObject(i).toString()))
        }

        return SyncBatch(
            batchId = json.getString("batchId"),
            senderDeviceId = json.getString("senderDeviceId"),
            senderDeviceName = json.getString("senderDeviceName"),
            senderRole = DeviceRole.valueOf(json.getString("senderRole")),
            createdAt = json.getLong("createdAt"),
            maxSequenceNumber = json.getLong("maxSequenceNumber"),
            changes = changes,
            customers = customers,
            sales = sales,
            payments = payments,
        )
    }
}
