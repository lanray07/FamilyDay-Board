package com.familyday.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.familyday.board.security.ParentGate
import com.familyday.board.ui.FamilyDayApp
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val gate: ParentGate by inject()
    var externalActivity = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FamilyDayApp() }
    }
    override fun onStop() {
        super.onStop()
        // External speech/export activities also cause a lock, requiring fresh authentication.
        if (!isChangingConfigurations && !externalActivity) gate.lock()
    }
}
