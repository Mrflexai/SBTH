package ma.remorquepro.cac.ui.composants

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Les écrans internes ne réappliquent pas les marges système déjà gérées par le Scaffold principal. */
val SANS_MARGES = WindowInsets(0, 0, 0, 0)

/** ViewModel créé avec une fabrique simple (pas de framework d'injection). */
@Composable
inline fun <reified VM : ViewModel> vmAvec(crossinline creer: () -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { creer() } })
