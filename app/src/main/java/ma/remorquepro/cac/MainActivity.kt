package ma.remorquepro.cac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ma.remorquepro.cac.ui.AppRemorque
import ma.remorquepro.cac.ui.theme.ThemeRemorque

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val depot = (application as RemorqueApp).depot
        setContent {
            ThemeRemorque {
                AppRemorque(depot)
            }
        }
    }
}
