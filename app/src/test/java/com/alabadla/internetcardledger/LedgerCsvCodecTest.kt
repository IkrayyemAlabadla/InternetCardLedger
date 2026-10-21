package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.data.backup.LedgerCsvCodec
import com.alabadla.internetcardledger.data.backup.LedgerSnapshot
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCsvCodecTest {
    @Test
    fun write_isUtf8GoogleSheetsCompatibleAndPreservesRelationships() {
        val customer = CustomerEntity("c1", "أحمد، التاجر", null, "ملاحظة \"مهمة\"", 1, 2, "d1", SyncStatus.PENDING, false)
        val sale = SaleEntity("s1", "c1", "بطاقة 10", 3, 1_250, 3, null, 1, 3, "d1", SyncStatus.PENDING, false)
        val payment = PaymentEntity("p1", "c1", "s1", 2_000, 4, null, 1, 4, "d1", SyncStatus.PENDING, false)
        val deleted = customer.copy(id = "deleted", name = "محذوف", isDeleted = true)
        val output = ByteArrayOutputStream()

        LedgerCsvCodec.write(LedgerSnapshot(listOf(customer, deleted), listOf(sale), listOf(payment)), output)

        val bytes = output.toByteArray()
        assertTrue(bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val csv = bytes.toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"أحمد، التاجر\""))
        assertTrue(csv.contains("\"ملاحظة \"\"مهمة\"\"\""))
        assertTrue(csv.contains("\"3750\""))
        assertTrue(csv.contains("\"2000\""))
        assertFalse(csv.contains("محذوف"))
        assertTrue(csv.lines().filter(String::isNotBlank).all { it.count { char -> char == ',' } == 7 })
    }
}
