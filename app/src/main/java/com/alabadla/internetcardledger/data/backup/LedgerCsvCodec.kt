package com.alabadla.internetcardledger.data.backup

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus

/** UTF-8 CSV intended for spreadsheet import. Deleted synchronization tombstones are not user reports. */
object LedgerCsvCodec {
    private val header = listOf(
        "نوع السجل",
        "الزبون",
        "الوصف",
        "الكمية",
        "سعر الوحدة (شيكل)",
        "المبلغ (شيكل)",
        "حالة الدفع",
        "المبلغ المدفوع (شيكل)",
        "المبلغ المتبقي (شيكل)",
        "التاريخ (مللي ثانية)",
        "ملاحظات",
    )

    fun write(snapshot: LedgerSnapshot, output: OutputStream) {
        val customers = snapshot.customers.filterNot { it.isDeleted }.associateBy { it.id }
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            writer.write('\uFEFF'.code)
            writer.writeRow(header)
            customers.values.sortedBy { it.name }.forEach { customer ->
                writer.writeRow(listOf("زبون", customer.name, "", "", "", "", "", "", "", customer.updatedAt.toString(), customer.notes.orEmpty()))
            }
            snapshot.sales.filterNot { it.isDeleted }.sortedByDescending { it.soldAt }.forEach { sale ->
                writer.writeRow(
                    listOf(
                        "بيع",
                        sale.customerId?.let { customers[it]?.name }.orEmpty(),
                        sale.description,
                        sale.quantity.toString(),
                        sale.unitPriceMinor.toString(),
                        Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor).toString(),
                        sale.paymentStatus.csvLabel,
                        sale.paidAmountMinor.toString(),
                        Math.subtractExact(
                            Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor),
                            sale.paidAmountMinor,
                        ).toString(),
                        sale.soldAt.toString(),
                        sale.notes.orEmpty(),
                    ),
                )
            }
            snapshot.payments.filterNot { it.isDeleted }.sortedByDescending { it.paidAt }.forEach { payment ->
                writer.writeRow(
                    listOf(
                        "دفعة",
                        customers[payment.customerId]?.name.orEmpty(),
                        "", "", "",
                        payment.amountMinor.toString(),
                        PaymentStatus.PAID.csvLabel,
                        payment.amountMinor.toString(),
                        "0",
                        payment.paidAt.toString(),
                        payment.notes.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun OutputStreamWriter.writeRow(values: List<String>) {
        append(values.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" })
        append("\r\n")
    }

    private val PaymentStatus.csvLabel: String
        get() = when (this) {
            PaymentStatus.PAID -> "مدفوع"
            PaymentStatus.UNPAID -> "غير مدفوع"
            PaymentStatus.PARTIALLY_PAID -> "مدفوع جزئيًا"
        }
}
