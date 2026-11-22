package cl.induztek.protocol.data

import androidx.room.*
import cl.induztek.protocol.model.*

@Dao
interface InduztekDao {
    @Upsert suspend fun importar(value: HistorialEstado)

    @Upsert suspend fun importar(value: Medicion)

    @Upsert suspend fun importar(value: Prueba)

    @Upsert suspend fun importar(value: Protocolo)

    @Upsert suspend fun importar(value: Recepcion)

    @Upsert suspend fun importar(value: Participante)

    @Upsert suspend fun importar(value: Visita)

    @Upsert suspend fun importar(value: Calibracion)

    @Upsert suspend fun importar(value: Instrumento)

    @Upsert suspend fun importar(value: Equipo)

    @Upsert suspend fun importar(value: Instalacion)

    @Upsert suspend fun importar(value: Usuario)

    @Upsert suspend fun guardarMeta(value: SincronizacionMeta)

    @Query("SELECT * FROM sincronizacion_meta") suspend fun metas(): List<SincronizacionMeta>

    @Query("DELETE FROM prueba WHERE protocoloId = :id") suspend fun borrarPruebas(id: String)

    @Query("DELETE FROM medicion WHERE pruebaId IN (SELECT id FROM prueba WHERE protocoloId = :id)")
    suspend fun borrarMedicionesProtocolo(id: String)

    @Query("DELETE FROM historialestado WHERE protocoloId = :id")
    suspend fun borrarHistorial(id: String)

    @Query("DELETE FROM participante WHERE visitaId = :id")
    suspend fun borrarParticipantes(id: String)

    @Query("DELETE FROM recepcion WHERE visitaId = :id") suspend fun borrarRecepcion(id: String)

    @Insert suspend fun insertar(value: Region)

    @Insert suspend fun insertar(value: Comuna)

    @Query("SELECT * FROM region") suspend fun regiones(): List<Region>

    @Query("SELECT * FROM comuna") suspend fun comunas(): List<Comuna>

    @Insert suspend fun insertar(value: Rol)

    @Query("SELECT * FROM rol") suspend fun rols(): List<Rol>

    @Insert suspend fun insertar(value: Usuario)

    @Query("SELECT * FROM usuario") suspend fun usuarios(): List<Usuario>

    @Insert suspend fun insertar(value: Instalacion)

    @Query("SELECT * FROM instalacion") suspend fun instalacions(): List<Instalacion>

    @Insert suspend fun insertar(value: TipoEquipo)

    @Query("SELECT * FROM tipoequipo") suspend fun tipoEquipos(): List<TipoEquipo>

    @Insert suspend fun insertar(value: Equipo)

    @Query("SELECT * FROM equipo") suspend fun equipos(): List<Equipo>

    @Insert suspend fun insertar(value: TipoPrueba)

    @Query("SELECT * FROM tipoprueba") suspend fun tipoPruebas(): List<TipoPrueba>

    @Insert suspend fun insertar(value: Parametro)

    @Query("SELECT * FROM parametro") suspend fun parametros(): List<Parametro>

    @Insert suspend fun insertar(value: Instrumento)

    @Query("SELECT * FROM instrumento") suspend fun instrumentos(): List<Instrumento>

    @Insert suspend fun insertar(value: Calibracion)

    @Query("SELECT * FROM calibracion") suspend fun calibracions(): List<Calibracion>

    @Insert suspend fun insertar(value: Visita)

    @Query("SELECT * FROM visita") suspend fun visitas(): List<Visita>

    @Insert suspend fun insertar(value: Participante)

    @Query("SELECT * FROM participante") suspend fun participantes(): List<Participante>

    @Insert suspend fun insertar(value: Recepcion)

    @Query("SELECT * FROM recepcion") suspend fun recepcions(): List<Recepcion>

    @Insert suspend fun insertar(value: Protocolo)

    @Query("SELECT * FROM protocolo") suspend fun protocolos(): List<Protocolo>

    @Insert suspend fun insertar(value: Prueba)

    @Query("SELECT * FROM prueba") suspend fun pruebas(): List<Prueba>

    @Insert suspend fun insertar(value: Medicion)

    @Query("SELECT * FROM medicion") suspend fun medicions(): List<Medicion>

    @Insert suspend fun insertar(value: HistorialEstado)

    @Query("SELECT * FROM historialestado") suspend fun historialEstados(): List<HistorialEstado>

    @Update suspend fun actualizar(value: Usuario)

    @Update suspend fun actualizar(value: Equipo)

    @Update suspend fun actualizar(value: Visita)

    @Update suspend fun actualizar(value: Protocolo)

    @Update suspend fun actualizar(value: Recepcion)

    @Update suspend fun actualizar(value: Instrumento)

    @Update suspend fun actualizar(value: Prueba)

    @Query("DELETE FROM medicion WHERE pruebaId = :id") suspend fun borrarMediciones(id: String)

    @Query("SELECT * FROM usuario WHERE lower(correo) = lower(:correo) LIMIT 1")
    suspend fun usuarioPorCorreo(correo: String): Usuario?

    @Query("SELECT * FROM usuario WHERE id = :id LIMIT 1") suspend fun usuario(id: String): Usuario?

    @Query(
        "SELECT e.* FROM equipo e JOIN instalacion i ON i.id = e.instalacionId WHERE :clienteId IS NULL OR (i.clienteId = :clienteId AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':instalacion' AND m.registroId = i.id AND m.version = 0) AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':equipo' AND m.registroId = e.id AND m.version = 0))"
    )
    suspend fun equiposVisibles(clienteId: String?): List<Equipo>

    @Query("SELECT i.* FROM instalacion i WHERE :clienteId IS NULL OR (i.clienteId = :clienteId AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':instalacion' AND m.registroId = i.id AND m.version = 0))")
    suspend fun instalacionesVisibles(clienteId: String?): List<Instalacion>

    @Query(
        "SELECT p.* FROM protocolo p JOIN equipo e ON e.id = p.equipoId JOIN instalacion i ON i.id = e.instalacionId WHERE :clienteId IS NULL OR (i.clienteId = :clienteId AND p.estado NOT IN ('BORRADOR', 'ANULADO') AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':instalacion' AND m.registroId = i.id AND m.version = 0) AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':equipo' AND m.registroId = e.id AND m.version = 0) AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':protocolo' AND m.registroId = p.id AND m.version = 0)) ORDER BY p.fechaHora DESC"
    )
    suspend fun protocolosVisibles(clienteId: String?): List<Protocolo>

    @Query(
        "SELECT v.* FROM visita v JOIN instalacion i ON i.id = v.instalacionId WHERE :clienteId IS NULL OR (i.clienteId = :clienteId AND v.cierre IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':instalacion' AND m.registroId = i.id AND m.version = 0) AND NOT EXISTS (SELECT 1 FROM sincronizacion_meta m WHERE m.tipo = 'acceso:' || :clienteId || ':visita' AND m.registroId = v.id AND m.version = 0))"
    )
    suspend fun visitasVisibles(clienteId: String?): List<Visita>

    @Query("SELECT * FROM prueba WHERE protocoloId IN (:ids)")
    suspend fun pruebasVisibles(ids: List<String>): List<Prueba>

    @Query("SELECT * FROM medicion WHERE pruebaId IN (:ids)")
    suspend fun medicionesVisibles(ids: List<String>): List<Medicion>

    @Query("SELECT * FROM historialestado WHERE protocoloId IN (:ids) ORDER BY fechaHora")
    suspend fun historialVisible(ids: List<String>): List<HistorialEstado>

    @Query("SELECT * FROM recepcion WHERE visitaId IN (:ids)")
    suspend fun recepcionesVisibles(ids: List<String>): List<Recepcion>
}
