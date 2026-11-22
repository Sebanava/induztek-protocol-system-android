package cl.induztek.protocol

import android.app.Application
import androidx.room.Room
import cl.induztek.protocol.data.*

class InduztekApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, InduztekDatabase::class.java, "induztek.db")
            .addMigrations(MIGRACION_1_2)
            .build()
    }
    val repository by lazy { InduztekRepository(database) }
    val sincronizador by lazy { cl.induztek.protocol.sync.Sincronizador(this, database) }
}
