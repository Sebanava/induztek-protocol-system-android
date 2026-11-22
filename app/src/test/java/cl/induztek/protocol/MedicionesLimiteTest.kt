package cl.induztek.protocol

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.induztek.protocol.data.*
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.model.Resultado
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MedicionesLimiteTest {
    private lateinit var db: InduztekDatabase
    private lateinit var repo: InduztekRepository
    private lateinit var protocoloId: String

    @Before
    fun preparar() = runBlocking {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(contexto, InduztekDatabase::class.java).build()
        DatosSemilla.preparar(db)
        repo = InduztekRepository(db)
        repo.ingresar(mapOf("correo" to "mgodoy@induztek.ejemplo", "clave" to "Induztek2026!"))

        repo.guardarVisita(
            mapOf(
                "ot" to "OT-LIMITE-GOHM",
                "instalacionId" to "lampa",
                "ingenieroId" to "acontreras",
                "fecha" to "2026-09-28",
                "tipo" to "SOLO_PRUEBAS",
            )
        )
        val visita = repo.datos().visitas.single { it.ot == "OT-LIMITE-GOHM" }
        protocoloId = repo.guardarProtocolo(
            mapOf(
                "visitaId" to visita.id,
                "equipoId" to "tr01",
                "fechaHora" to "2026-09-28T10:00",
                "temperatura" to "19",
                "humedad" to "60",
            )
        )
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun aislacion(uno: String, diez: String) =
        mapOf(
            "tipoId" to "AISLACION",
            "elemento" to "AT-T",
            "momento" to "UNICA",
            "realizada" to "SI",
            "instrumentoId" to "meg5",
            "unidad" to "GΩ",
            "TENSION_PRUEBA" to "5",
            "LECTURA_1MIN" to uno,
            "LECTURA_10MIN" to diez,
        )

    @Test
    fun conversionFueraDeRepresentacionRechazaCadaLecturaSinGuardarDatosParciales() = runBlocking {
        val pruebasAntes = db.dao().pruebas().associateBy { it.id }
        val medicionesAntes = db.dao().medicions().associateBy { it.id }
        val historialAntes = db.dao().historialEstados().associateBy { it.id }

        listOf(
                "LECTURA_1MIN" to aislacion("1e308", "1e308"),
                "LECTURA_10MIN" to aislacion("2.5", "1e308"),
            )
            .forEach { (campo, datos) ->
                val error = runCatching { repo.guardarPrueba(protocoloId, datos) }.exceptionOrNull()
                assertTrue(error is ErrorFormulario)
                assertEquals(setOf(campo), (error as ErrorFormulario).campos.keys)
                assertEquals(pruebasAntes, db.dao().pruebas().associateBy { it.id })
                assertEquals(medicionesAntes, db.dao().medicions().associateBy { it.id })
                assertEquals(historialAntes, db.dao().historialEstados().associateBy { it.id })
            }
    }

    @Test
    fun conversionNormalDeGigaohmiosConservaValoresBaseYClasificacion() = runBlocking {
        repo.guardarPrueba(protocoloId, aislacion("2.5", "5"))
        val datos = repo.datos()
        val prueba = datos.pruebas.single { it.protocoloId == protocoloId }
        val valores = datos.valores(prueba.id)
        assertEquals(2500.0, valores.getValue("LECTURA_1MIN"), 0.0)
        assertEquals(5000.0, valores.getValue("LECTURA_10MIN"), 0.0)
        assertTrue(valores.values.all { it.isFinite() })
        assertEquals(Resultado.APROBADO, prueba.resultado)
    }
}
