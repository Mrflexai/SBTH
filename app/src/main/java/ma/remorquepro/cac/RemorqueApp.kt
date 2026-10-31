package ma.remorquepro.cac

import android.app.Application
import ma.remorquepro.cac.data.BaseDeDonnees
import ma.remorquepro.cac.data.Depot

class RemorqueApp : Application() {
    val baseDeDonnees: BaseDeDonnees by lazy { BaseDeDonnees.creer(this) }
    val depot: Depot by lazy { Depot(baseDeDonnees) }
}
