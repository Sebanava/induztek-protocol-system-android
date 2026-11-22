package cl.induztek.protocol.data

import androidx.room.*
import cl.induztek.protocol.model.*

@Entity(tableName = "rol") data class Rol(@PrimaryKey val id: String, val nombre: String)

@Entity(
    tableName = "usuario",
    foreignKeys =
        [
            ForeignKey(
                entity = Rol::class,
                parentColumns = ["id"],
                childColumns = ["rolId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index(value = ["correo"], unique = true),
            Index(value = ["rut"], unique = true),
            Index("rolId"),
        ],
)
data class Usuario(
    @PrimaryKey val id: String,
    val rolId: String,
    val nombre: String,
    val rut: String,
    val correo: String,
    val claveHash: String,
    val activo: Boolean = true,
    val contacto: String = "",
    val cargo: String = "",
    val giro: String = "",
    val telefono: String = "",
) {
    val perfil: Perfil
        get() = Perfil.valueOf(rolId)
}

@Entity(
    tableName = "instalacion",
    foreignKeys =
        [
            ForeignKey(
                entity = Usuario::class,
                parentColumns = ["id"],
                childColumns = ["clienteId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Comuna::class,
                parentColumns = ["id"],
                childColumns = ["comuna"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("clienteId"), Index("comuna")],
)
data class Instalacion(
    @PrimaryKey val id: String,
    val clienteId: String,
    val nombre: String,
    val ubicacion: String,
    val comuna: String,
    val acceso: String = "",
)

@Entity(tableName = "tipoequipo")
data class TipoEquipo(@PrimaryKey val id: String, val nombre: String, val clasificacion: String)

@Entity(
    tableName = "equipo",
    foreignKeys =
        [
            ForeignKey(
                entity = Instalacion::class,
                parentColumns = ["id"],
                childColumns = ["instalacionId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = TipoEquipo::class,
                parentColumns = ["id"],
                childColumns = ["tipoId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["instalacionId", "tag"], unique = true), Index("tipoId")],
)
data class Equipo(
    @PrimaryKey val id: String,
    val instalacionId: String,
    val tipoId: String,
    val tag: String,
    val marca: String,
    val modelo: String,
    val serie: String,
    val anio: Int?,
    val potencia: Double?,
    val tensiones: String,
    val corriente: Double?,
    val maxContactos: Double?,
    val observaciones: String = "",
    val foto: ByteArray? = null,
)

@Entity(tableName = "tipoprueba")
data class TipoPrueba(@PrimaryKey val id: String, val nombre: String)

@Entity(
    tableName = "parametro",
    foreignKeys =
        [
            ForeignKey(
                entity = TipoPrueba::class,
                parentColumns = ["id"],
                childColumns = ["tipoId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["tipoId", "codigo"], unique = true)],
)
data class Parametro(
    @PrimaryKey val id: String,
    val tipoId: String,
    val codigo: String,
    val nombre: String,
    val unidad: String,
)

@Entity(
    tableName = "instrumento",
    foreignKeys =
        [
            ForeignKey(
                entity = TipoPrueba::class,
                parentColumns = ["id"],
                childColumns = ["tipoPruebaId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["serie"], unique = true), Index("tipoPruebaId")],
)
data class Instrumento(
    @PrimaryKey val id: String,
    val tipoPruebaId: String,
    val nombre: String,
    val marcaModelo: String,
    val serie: String,
)

@Entity(
    tableName = "calibracion",
    foreignKeys =
        [
            ForeignKey(
                entity = Instrumento::class,
                parentColumns = ["id"],
                childColumns = ["instrumentoId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("instrumentoId")],
)
data class Calibracion(
    @PrimaryKey val id: String,
    val instrumentoId: String,
    val desde: String,
    val hasta: String,
)

@Entity(
    tableName = "visita",
    foreignKeys =
        [
            ForeignKey(
                entity = Instalacion::class,
                parentColumns = ["id"],
                childColumns = ["instalacionId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Usuario::class,
                parentColumns = ["id"],
                childColumns = ["ingenieroId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["ot"], unique = true), Index("instalacionId"), Index("ingenieroId")],
)
data class Visita(
    @PrimaryKey val id: String,
    val instalacionId: String,
    val ingenieroId: String,
    val ot: String,
    val fecha: String,
    val tipo: TipoVisita,
    val cierre: String? = null,
)

@Entity(
    tableName = "participante",
    foreignKeys =
        [
            ForeignKey(
                entity = Visita::class,
                parentColumns = ["id"],
                childColumns = ["visitaId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Usuario::class,
                parentColumns = ["id"],
                childColumns = ["usuarioId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["visitaId", "usuarioId"], unique = true), Index("usuarioId")],
)
data class Participante(@PrimaryKey val id: String, val visitaId: String, val usuarioId: String)

@Entity(
    tableName = "recepcion",
    foreignKeys =
        [
            ForeignKey(
                entity = Visita::class,
                parentColumns = ["id"],
                childColumns = ["visitaId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["visitaId"], unique = true)],
)
data class Recepcion(
    @PrimaryKey val id: String,
    val visitaId: String,
    val nombre: String,
    val rut: String,
    val cargo: String,
    val fechaHora: String,
    val documento: String,
)

@Entity(
    tableName = "protocolo",
    foreignKeys =
        [
            ForeignKey(
                entity = Visita::class,
                parentColumns = ["id"],
                childColumns = ["visitaId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Equipo::class,
                parentColumns = ["id"],
                childColumns = ["equipoId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Usuario::class,
                parentColumns = ["id"],
                childColumns = ["creadorId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Protocolo::class,
                parentColumns = ["id"],
                childColumns = ["reemplazaId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["numero"], unique = true),
            Index(value = ["visitaId", "equipoId"], unique = true),
            Index("equipoId"),
            Index("creadorId"),
            Index("reemplazaId"),
        ],
)
data class Protocolo(
    @PrimaryKey val id: String,
    val numero: String,
    val visitaId: String,
    val equipoId: String,
    val creadorId: String,
    val fechaHora: String,
    val temperatura: Double?,
    val humedad: Double?,
    val mantenimiento: String = "",
    val estado: Estado = Estado.BORRADOR,
    val motivo: String = "",
    val reemplazaId: String? = null,
)

@Entity(
    tableName = "prueba",
    foreignKeys =
        [
            ForeignKey(
                entity = Protocolo::class,
                parentColumns = ["id"],
                childColumns = ["protocoloId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = TipoPrueba::class,
                parentColumns = ["id"],
                childColumns = ["tipoId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Instrumento::class,
                parentColumns = ["id"],
                childColumns = ["instrumentoId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["protocoloId", "tipoId", "elemento", "momento"], unique = true),
            Index("tipoId"),
            Index("instrumentoId"),
        ],
)
data class Prueba(
    @PrimaryKey val id: String,
    val protocoloId: String,
    val tipoId: String,
    val elemento: String,
    val momento: Momento,
    val realizada: Boolean,
    val motivo: String = "",
    val instrumentoId: String?,
    val resultado: Resultado?,
    val comentario: String = "",
    val opera: Boolean = true,
)

@Entity(
    tableName = "medicion",
    foreignKeys =
        [
            ForeignKey(
                entity = Prueba::class,
                parentColumns = ["id"],
                childColumns = ["pruebaId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Parametro::class,
                parentColumns = ["id"],
                childColumns = ["parametroId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["pruebaId", "parametroId"], unique = true), Index("parametroId")],
)
data class Medicion(
    @PrimaryKey val id: String,
    val pruebaId: String,
    val parametroId: String,
    val valor: Double,
)

@Entity(
    tableName = "historialestado",
    foreignKeys =
        [
            ForeignKey(
                entity = Protocolo::class,
                parentColumns = ["id"],
                childColumns = ["protocoloId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Usuario::class,
                parentColumns = ["id"],
                childColumns = ["usuarioId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("protocoloId"), Index("usuarioId")],
)
data class HistorialEstado(
    @PrimaryKey val id: String,
    val protocoloId: String,
    val usuarioId: String,
    val anterior: Estado?,
    val nuevo: Estado,
    val fechaHora: String,
    val motivo: String,
)

@Entity(tableName = "region") data class Region(@PrimaryKey val id: String, val nombre: String)

@Entity(
    tableName = "comuna",
    foreignKeys =
        [
            ForeignKey(
                entity = Region::class,
                parentColumns = ["id"],
                childColumns = ["regionId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("regionId")],
)
data class Comuna(@PrimaryKey val id: String, val regionId: String)

@Entity(tableName = "sincronizacion_meta", primaryKeys = ["tipo", "registroId"])
data class SincronizacionMeta(
    val tipo: String,
    val registroId: String,
    val version: Long,
    val huella: String,
)
