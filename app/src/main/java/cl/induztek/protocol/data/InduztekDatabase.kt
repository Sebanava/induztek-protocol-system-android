package cl.induztek.protocol.data

import androidx.room.*
import cl.induztek.protocol.model.*

class Convertidores {
    @TypeConverter fun estado(value: String): Estado = Estado.valueOf(value)

    @TypeConverter fun estado(value: Estado): String = value.name

    @TypeConverter fun resultado(value: String?): Resultado? = value?.let(Resultado::valueOf)

    @TypeConverter fun resultado(value: Resultado?): String? = value?.name

    @TypeConverter fun momento(value: String): Momento = Momento.valueOf(value)

    @TypeConverter fun momento(value: Momento): String = value.name

    @TypeConverter fun visita(value: String): TipoVisita = TipoVisita.valueOf(value)

    @TypeConverter fun visita(value: TipoVisita): String = value.name
}

@Database(
    entities =
        [
            SincronizacionMeta::class,
            Region::class,
            Comuna::class,
            Rol::class,
            Usuario::class,
            Instalacion::class,
            TipoEquipo::class,
            Equipo::class,
            TipoPrueba::class,
            Parametro::class,
            Instrumento::class,
            Calibracion::class,
            Visita::class,
            Participante::class,
            Recepcion::class,
            Protocolo::class,
            Prueba::class,
            Medicion::class,
            HistorialEstado::class,
        ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class InduztekDatabase : RoomDatabase() {
    abstract fun dao(): InduztekDao
}

val MIGRACION_1_2 =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sincronizacion_meta (tipo TEXT NOT NULL, registroId TEXT NOT NULL, version INTEGER NOT NULL, huella TEXT NOT NULL, PRIMARY KEY(tipo, registroId))"
            )
        }
    }
