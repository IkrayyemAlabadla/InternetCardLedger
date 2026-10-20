package com.alabadla.internetcardledger

import android.app.Application
import com.alabadla.internetcardledger.data.AppContainer

class LedgerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
