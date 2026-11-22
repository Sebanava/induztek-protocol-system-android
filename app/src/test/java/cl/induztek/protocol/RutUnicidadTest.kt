package cl.induztek.protocol

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.induztek.protocol.data.*
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.domain.Validacion
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
class RutUnicidadTest {
    private lateinit var db: InduztekDatabase
    private lateinit var repo: InduztekRepository

    @Before
    fun preparar() = runBlocking {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(contexto, InduztekDatabase::class.java).build()
        DatosSemilla.preparar(db)
        repo = InduztekRepository(db)
    }

    @After
    fun cerrar() {
        db.close()
    }

    private suspend fun entrar(correo: String) {
        repo.ingresar(mapOf("correo" to correo, "clave" to "Induztek2026!"))
    }

    private fun cliente(rut: String) =
        mapOf(
            "nombre" to "Empresa ficticia de prueba",
            "rut" to rut,
            "correo" to "cliente-nuevo@prueba.ejemplo",
            "contacto" to "Contacto ficticio",
            "cargo" to "Encargado",
        )

    private fun personal(rut: String) =
        mapOf(
            "nombre" to "Personal ficticio de prueba",
            "rut" to rut,
            "correo" to "personal-nuevo@prueba.ejemplo",
            "rolId" to "TECNICO",
            "activo" to "SI",
            "clave" to "CuentaPrueba2026!",
        )

    private suspend fun rechazaRut(bloque: suspend () -> Unit) {
        val usuariosAntes = db.dao().usuarios().associateBy { it.id }
        val metasAntes = db.dao().metas().associateBy { it.tipo to it.registroId }
        val error = runCatching { bloque() }.exceptionOrNull()
        assertTrue(error is ErrorFormulario)
        assertEquals(setOf("rut"), (error as ErrorFormulario).campos.keys)
        assertEquals(usuariosAntes, db.dao().usuarios().associateBy { it.id })
        assertEquals(metasAntes, db.dao().metas().associateBy { it.tipo to it.registroId })
    }

    @Test
    fun altaClienteRechazaRutExistenteConEspaciosOPresentacionDistinta() = runBlocking {
        entrar("mgodoy@induztek.ejemplo")
        listOf(" 17.284.519-3 ", " 17284519-3 ", " 172845193 ", "\t17.284.519-3\n")
            .forEach { rut -> rechazaRut { repo.guardarCliente(cliente(rut)) } }
    }

    @Test
    fun altaYEdicionPersonalNoDuplicanRutsDeOtrasCuentas() = runBlocking {
        entrar("respinoza@induztek.ejemplo")
        rechazaRut { repo.guardarPersonal(personal(" 76.482.915-8 ")) }
        listOf(" 17.284.519-3 ", "172845193", "17284519-3").forEach { rut ->
            rechazaRut {
                repo.guardarPersonal(
                    personal(rut) + mapOf(
                        "personalId" to "jtapia",
                        "correo" to "jtapia@induztek.ejemplo",
                        "nombre" to "Nombre que no debe guardarse",
                    )
                )
            }
        }
    }

    @Test
    fun rutNuevoConKSeGuardaSinEspaciosYNoSeDuplicaPorMayusculasOFormato() = runBlocking {
        entrar("mgodoy@induztek.ejemplo")
        repo.guardarCliente(cliente("\t10.000.013-k \n"))
        val guardado = db.dao().usuarios().single { it.correo == "cliente-nuevo@prueba.ejemplo" }
        assertEquals("10.000.013-k", guardado.rut)
        assertEquals("10000013K", Validacion.normalizarRut(guardado.rut))
        entrar("respinoza@induztek.ejemplo")
        listOf("10000013K", "10000013-k", " 10.000.013-K ")
            .forEach { rut -> rechazaRut { repo.guardarPersonal(personal(rut)) } }
    }

    @Test
    fun telefonoChilenoNormalizaFijosYMovilesSinTruncar() {
        assertEquals("+56912345678", Validacion.telefonoChileno(" 9 1234 5678 "))
        assertEquals("+56223456789", Validacion.telefonoChileno("+56 (2) 2345-6789"))
        assertEquals("", Validacion.telefonoChileno(" "))
        listOf("9123456789", "91234567", "+54912345678", "91234567x").forEach {
            assertEquals("telefono", assertThrows(ErrorFormulario::class.java) {
                Validacion.telefonoChileno(it)
            }.campos.keys.single())
        }
    }

    @Test
    fun correoRechazaDominiosYEstructurasInvalidas() {
        assertTrue(Validacion.correoValido(" Nombre+informe@empresa.cl "))
        listOf("a..b@empresa.cl", "a@-empresa.cl", "a@empresa..cl", "a@empresa.123", "a@empresa", "a b@empresa.cl").forEach {
            assertFalse(it, Validacion.correoValido(it))
        }
        assertFalse(Validacion.rut("00.000.000-0"))
    }

    @Test
    fun fabricacionSeEvaluaContraFechaDelRegistro() {
        val visita = java.time.LocalDate.of(2024, 8, 1)
        assertTrue(Validacion.fabricacionValida(2015, visita))
        assertTrue(Validacion.fabricacionValida(2024, visita))
        assertFalse(Validacion.fabricacionValida(2025, visita))
    }
}
