package com.alabadla.internetcardledger.data.repository

import android.content.Context
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.backup.BACKUP_FORMAT_VERSION
import com.alabadla.internetcardledger.data.backup.BackupDocument
import com.alabadla.internetcardledger.data.backup.BackupJsonCodec
import com.alabadla.internetcardledger.data.backup.LedgerCsvCodec
import com.alabadla.internetcardledger.data.backup.RestoreReport
import com.alabadla.internetcardledger.data.local.dao.BackupDao
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    context: Context,
    private val backupDao: BackupDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val applicationContext = context.applicationContext

    suspend fun createDocument(): BackupDocument = withContext(Dispatchers.IO) {
        val snapshot = backupDao.snapshot()
        BackupDocument(
            version = BACKUP_FORMAT_VERSION,
            createdAt = now(),
            deviceId = deviceIdProvider.getDeviceId(),
            customers = snapshot.customers,
            sales = snapshot.sales,
            payments = snapshot.payments,
        )
    }

    suspend fun export(output: OutputStream): BackupDocument = withContext(Dispatchers.IO) {
        createDocument().also { BackupJsonCodec.write(it, output) }
    }

    suspend fun exportCsv(output: OutputStream) = withContext(Dispatchers.IO) {
        LedgerCsvCodec.write(backupDao.snapshot(), output)
    }

    suspend fun inspect(input: InputStream): BackupDocument = withContext(Dispatchers.IO) {
        BackupJsonCodec.readAndValidate(input)
    }

    suspend fun merge(document: BackupDocument): RestoreReport = withContext(Dispatchers.IO) {
        BackupJsonCodec.validate(document)
        backupDao.merge(document)
    }

    suspend fun replace(document: BackupDocument): RestoreReport = withContext(Dispatchers.IO) {
        BackupJsonCodec.validate(document)
        val safetyBackup = createSafetyBackup()
        backupDao.replace(document).copy(safetyBackupName = safetyBackup.name)
    }

    private suspend fun createSafetyBackup(): File {
        val document = createDocument()
        val directory = File(applicationContext.filesDir, SAFETY_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) error("تعذر إنشاء مجلد نسخة الأمان.")
        val target = File(directory, "safety-${document.createdAt}-${UUID.randomUUID()}.json")
        val temporary = File(directory, "${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { BackupJsonCodec.write(document, it) }
            if (!temporary.renameTo(target)) error("تعذر حفظ نسخة الأمان.")
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private companion object {
        const val SAFETY_DIRECTORY = "safety-backups"
    }
}
