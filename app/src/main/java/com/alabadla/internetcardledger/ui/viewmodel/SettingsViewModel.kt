package com.alabadla.internetcardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alabadla.internetcardledger.data.repository.BackupReminder
import com.alabadla.internetcardledger.data.repository.LocalSettings
import com.alabadla.internetcardledger.data.repository.SettingsRepository
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.SyncSessionEntity
import com.alabadla.internetcardledger.security.PinSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: LocalSettings? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val pinSecurity: PinSecurityManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    val approvedDevices: StateFlow<List<ApprovedDeviceEntity>> = repository.approvedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val auditSessions: StateFlow<List<SyncSessionEntity>> = repository.auditSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun completeOnboarding(name: String, role: DeviceRole, pin: String, confirmPin: String) {
        if (name.isBlank()) return show("أدخل اسمًا واضحًا لهذا الهاتف.")
        if (!com.alabadla.internetcardledger.security.PinHasher.isValidFormat(pin)) {
            return show("يجب أن يتكون الرقم السري من 4 إلى 8 أرقام.")
        }
        if (pin != confirmPin) return show("الرقمان السريان غير متطابقين.")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) { pinSecurity.setPin(pin) }
                repository.completeOnboarding(name, role)
            }.onSuccess { refresh("تم إعداد الهاتف بنجاح.") }
                .onFailure { show(it.message ?: "تعذر حفظ الإعداد الأول.") }
        }
    }

    fun verifyPin(pin: String, result: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) { runCatching { pinSecurity.verify(pin) }.getOrDefault(false) }
            if (!valid) show("الرقم السري غير صحيح.")
            result(valid)
        }
    }

    fun updateIdentity(name: String, role: DeviceRole) {
        viewModelScope.launch {
            runCatching { repository.updateIdentity(name, role) }
                .onSuccess { refresh("تم تحديث اسم الجهاز ودوره.") }
                .onFailure { show(it.message ?: "تعذر تحديث بيانات الجهاز.") }
        }
    }

    fun updateReminder(value: BackupReminder) {
        runCatching { repository.setBackupReminder(value) }
            .onSuccess { refresh("تم تحديث تذكير النسخ الاحتياطي.") }
            .onFailure { show("تعذر حفظ إعداد التذكير.") }
    }

    fun removeApprovedDevice(deviceId: String) {
        viewModelScope.launch {
            runCatching { repository.removeApprovedDevice(deviceId) }
                .onSuccess { show("تمت إزالة الجهاز المعتمد.") }
                .onFailure { show("تعذر إزالة الجهاز.") }
        }
    }

    fun changePin(current: String, newPin: String, confirmation: String) {
        if (newPin != confirmation) return show("الرقمان السريان الجديدان غير متطابقين.")
        viewModelScope.launch {
            val changed = withContext(Dispatchers.Default) {
                runCatching { pinSecurity.changePin(current, newPin) }.getOrDefault(false)
            }
            show(if (changed) "تم تغيير الرقم السري." else "تعذر تغيير الرقم السري. تحقق من الرقم الحالي والجديد.")
        }
    }

    fun resetPinAfterBackup(newPin: String, confirmation: String) {
        if (newPin != confirmation) return show("الرقمان السريان غير متطابقين.")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { pinSecurity.resetAfterRecoveryBackup(newPin) } }
                .onSuccess { show("تم تعيين رقم سري جديد بعد حفظ نسخة الاسترداد.") }
                .onFailure { show(it.message ?: "تعذر تعيين الرقم السري.") }
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private fun refresh(message: String? = null) {
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { _state.value = SettingsUiState(false, it, message) }
                .onFailure { _state.value = SettingsUiState(false, message = it.message ?: "تعذر تحميل الإعدادات.") }
        }
    }

    private fun show(message: String) { _state.value = _state.value.copy(loading = false, message = message) }

    companion object {
        fun factory(repository: SettingsRepository, pinSecurity: PinSecurityManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(repository, pinSecurity) as T
            }
    }
}
