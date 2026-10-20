package com.alabadla.internetcardledger.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alabadla.internetcardledger.data.backup.BackupDocument
import com.alabadla.internetcardledger.data.repository.BackupRepository
import com.alabadla.internetcardledger.security.PinSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupPreview(
    val createdAt: Long,
    val customerCount: Int,
    val saleCount: Int,
    val paymentCount: Int,
) {
    val recordCount: Int get() = customerCount + saleCount + paymentCount
}

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class Ready(val preview: BackupPreview) : BackupUiState
    data class Success(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

class BackupViewModel(
    private val repository: BackupRepository,
    private val pinSecurity: PinSecurityManager,
) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    private var inspectedDocument: BackupDocument? = null
    private var operationRunning = false

    fun export(contentResolver: ContentResolver, uri: Uri) = runOnce("جارٍ إنشاء النسخة الاحتياطية…") {
        val output = contentResolver.openOutputStream(uri, "w")
            ?: error("تعذر فتح الملف المحدد للكتابة.")
        output.use { repository.export(it) }
        inspectedDocument = null
        _state.value = BackupUiState.Success("تم حفظ النسخة الاحتياطية بنجاح.")
    }

    fun inspect(contentResolver: ContentResolver, uri: Uri) = runOnce("جارٍ فحص النسخة الاحتياطية…") {
        val input = contentResolver.openInputStream(uri)
            ?: error("تعذر فتح الملف المحدد.")
        val document = input.use { repository.inspect(it) }
        inspectedDocument = document
        _state.value = BackupUiState.Ready(document.toPreview())
    }

    fun merge() = runOnce("جارٍ دمج البيانات…") {
        val document = inspectedDocument ?: error("اختر نسخة احتياطية أولاً.")
        val report = repository.merge(document)
        inspectedDocument = null
        _state.value = BackupUiState.Success(
            "تم الدمج بنجاح: أضيف ${report.inserted}، حُدّث ${report.updated}، " +
                "دون تغيير ${report.unchanged}، تعارضات ${report.conflicts}.",
        )
    }

    fun replace() = runOnce("جارٍ إنشاء نسخة أمان واستبدال البيانات…") {
        val document = inspectedDocument ?: error("اختر نسخة احتياطية أولاً.")
        val report = repository.replace(document)
        inspectedDocument = null
        _state.value = BackupUiState.Success(
            "تم استبدال البيانات بنجاح واستعادة ${report.inserted} سجلًا. " +
                "حُفظت نسخة أمان تلقائية باسم ${report.safetyBackupName}.",
        )
    }

    fun verifyPin(pin: String, result: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) {
                runCatching { pinSecurity.verify(pin) }.getOrDefault(false)
            }
            if (!valid) _state.value = BackupUiState.Error("الرقم السري غير صحيح.")
            result(valid)
        }
    }

    fun dismissMessage() {
        if (_state.value is BackupUiState.Success || _state.value is BackupUiState.Error) {
            _state.value = BackupUiState.Idle
        }
    }

    private fun runOnce(message: String, operation: suspend () -> Unit) {
        if (operationRunning) return
        operationRunning = true
        _state.value = BackupUiState.Working(message)
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                _state.value = BackupUiState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "تعذر إكمال العملية. لم تتغير البيانات.",
                )
            } finally {
                operationRunning = false
            }
        }
    }

    private fun BackupDocument.toPreview() = BackupPreview(
        createdAt = createdAt,
        customerCount = customers.size,
        saleCount = sales.size,
        paymentCount = payments.size,
    )

    companion object {
        fun factory(repository: BackupRepository, pinSecurity: PinSecurityManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BackupViewModel(repository, pinSecurity) as T
            }
    }
}
