package com.moneyper5h.app

import android.app.Application
import com.moneyper5h.app.data.UsageRepository

class WalletApplication : Application() {
    val repository by lazy { UsageRepository(this) }
}
