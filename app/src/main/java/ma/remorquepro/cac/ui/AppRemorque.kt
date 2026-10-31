package ma.remorquepro.cac.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.exports.EcranExports
import ma.remorquepro.cac.ui.missions.EcranDetailMission
import ma.remorquepro.cac.ui.missions.EcranEditionMission
import ma.remorquepro.cac.ui.missions.EcranHistorique
import ma.remorquepro.cac.ui.parametres.EcranParametres
import ma.remorquepro.cac.ui.tableau.EcranTableauDeBord

object Routes {
    const val TABLEAU = "tableau"
    const val MISSIONS = "missions"
    const val EXPORTS = "exports"
    const val PARAMETRES = "parametres"
    const val DETAIL = "mission/{id}"
    const val EDITION = "edition?id={id}&dupliquer={dupliquer}"

    fun detail(id: Long) = "mission/$id"
    fun nouvelle() = "edition?id=0&dupliquer=false"
    fun modifier(id: Long) = "edition?id=$id&dupliquer=false"
    fun dupliquer(id: Long) = "edition?id=$id&dupliquer=true"
}

private data class Onglet(val route: String, val libelle: String, val icone: ImageVector)

private val ONGLETS = listOf(
    Onglet(Routes.TABLEAU, "Accueil", Icons.Default.Home),
    Onglet(Routes.MISSIONS, "Missions", Icons.AutoMirrored.Filled.List),
    Onglet(Routes.EXPORTS, "Exports", Icons.Default.Share),
    Onglet(Routes.PARAMETRES, "Paramètres", Icons.Default.Settings),
)

@Composable
fun AppRemorque(depot: Depot) {
    val nav = rememberNavController()
    val entree by nav.currentBackStackEntryAsState()
    val routeActuelle = entree?.destination?.route
    val afficherBarre = ONGLETS.any { it.route == routeActuelle }

    Scaffold(
        bottomBar = {
            if (afficherBarre) {
                NavigationBar {
                    ONGLETS.forEach { o ->
                        NavigationBarItem(
                            selected = routeActuelle == o.route,
                            onClick = { allerOnglet(nav, o.route) },
                            icon = { Icon(o.icone, contentDescription = null) },
                            label = { Text(o.libelle) },
                            modifier = Modifier.testTag("onglet_${o.route}"),
                        )
                    }
                }
            }
        },
    ) { marges ->
        NavHost(
            navController = nav,
            startDestination = Routes.TABLEAU,
            modifier = Modifier.padding(bottom = marges.calculateBottomPadding()),
        ) {
            composable(Routes.TABLEAU) {
                EcranTableauDeBord(
                    depot = depot,
                    onNouvelleMission = { nav.navigate(Routes.nouvelle()) },
                    onOuvrirMission = { nav.navigate(Routes.detail(it)) },
                    onVoirMissions = { allerOnglet(nav, Routes.MISSIONS) },
                    onParametres = { allerOnglet(nav, Routes.PARAMETRES) },
                )
            }
            composable(Routes.MISSIONS) {
                EcranHistorique(
                    depot = depot,
                    onNouvelleMission = { nav.navigate(Routes.nouvelle()) },
                    onOuvrir = { nav.navigate(Routes.detail(it)) },
                    onModifier = { nav.navigate(Routes.modifier(it)) },
                    onDupliquer = { nav.navigate(Routes.dupliquer(it)) },
                )
            }
            composable(Routes.EXPORTS) { EcranExports(depot) }
            composable(Routes.PARAMETRES) { EcranParametres(depot) }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                EcranDetailMission(
                    depot = depot,
                    id = id,
                    onRetour = { nav.popBackStack() },
                    onModifier = { nav.navigate(Routes.modifier(id)) },
                    onDupliquer = { nav.navigate(Routes.dupliquer(id)) },
                )
            }
            composable(
                Routes.EDITION,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("dupliquer") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                val dupliquer = e.arguments?.getBoolean("dupliquer") ?: false
                EcranEditionMission(
                    depot = depot,
                    id = id,
                    dupliquer = dupliquer,
                    onRetour = { nav.popBackStack() },
                    onEnregistree = { nouvelId ->
                        // Après création : on remplace le formulaire par la fiche de la mission.
                        nav.popBackStack()
                        if (id == 0L || dupliquer) nav.navigate(Routes.detail(nouvelId))
                    },
                )
            }
        }
    }
}

private fun allerOnglet(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
