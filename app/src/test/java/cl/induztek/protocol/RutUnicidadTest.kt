package cl.induztek.protocol

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.induztek.protocol.data.*
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.domain.Validacion
import cl.induztek.protocol.model.Estado
import cl.induztek.protocol.sync.Sincronizador
import cl.induztek.protocol.viewmodel.Accion
import cl.induztek.protocol.viewmodel.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    fun ingenieriaRegistraCriterioManualConTrazabilidad() = runBlocking {
        val prueba = db.dao().pruebas().single { it.id == "2026-DEVANADOS-AT-U" }
        db.dao().actualizar(prueba.copy(realizada = true))
        val revision = mapOf("resultado" to "APROBADO", "comentario" to "Comparación de devanados")
        entrar("mgodoy@induztek.ejemplo")
        assertTrue(runCatching { repo.revisarPrueba("p-2026", prueba.id, revision) }.isFailure)
        entrar("acontreras@induztek.ejemplo")
        repo.revisarPrueba("p-2026", prueba.id, revision)
        val aplicada = db.dao().pruebas().single { it.id == prueba.id }
        assertEquals("APROBADO", aplicada.resultado?.name)
        assertEquals(revision["comentario"], aplicada.comentario)
        assertTrue(db.dao().historialEstados().any {
            it.protocoloId == prueba.protocoloId && it.usuarioId == "acontreras" &&
                it.motivo.contains("Comparación de devanados")
        })
    }

    @Test
    fun jefaturaHabilitaCuentaClienteYProtegeSuPropioPerfil() = runBlocking {
        entrar("mgodoy@induztek.ejemplo")
        repo.guardarCliente(cliente("10.000.013-K"))
        val cuenta = db.dao().usuarios().single { it.correo == "cliente-nuevo@prueba.ejemplo" }
        assertFalse(cuenta.activo)

        entrar("respinoza@induztek.ejemplo")
        val clave = "ClientePrueba2026!"
        val datos = mapOf("clienteId" to cuenta.id, "clave" to clave)
        assertTrue(runCatching { repo.habilitarCliente(datos + ("clave" to "corta")) }.isFailure)
        repo.habilitarCliente(datos)
        val jefe = repo.datos().usuario
        val suCuenta = mapOf("personalId" to jefe.id, "nombre" to jefe.nombre,
            "rut" to jefe.rut, "correo" to jefe.correo, "rolId" to jefe.rolId, "activo" to "SI")
        assertTrue(runCatching { repo.guardarPersonal(suCuenta + ("activo" to "NO")) }.isFailure)
        assertTrue(runCatching { repo.guardarPersonal(suCuenta + ("rolId" to "TECNICO")) }.isFailure)
        assertEquals(cuenta.id, repo.ingresar(mapOf("correo" to cuenta.correo, "clave" to clave)).id)
    }

    @Test
    fun jefaturaActualizaInstrumentoYConservaLasCalibraciones() = runBlocking {
        entrar("respinoza@induztek.ejemplo")
        val hoy = Validacion.hoy()
        val datos = mapOf("nombre" to "Equipo de ensayo", "marcaModelo" to "Modelo A",
            "serie" to "QA-INS-01", "tipoPruebaId" to "AISLACION",
            "desde" to hoy.minusDays(2).toString(), "hasta" to hoy.plusYears(1).toString())
        assertTrue(runCatching { repo.guardarInstrumento(datos + ("desde" to hoy.plusDays(1).toString())) }.isFailure)
        assertTrue(runCatching { repo.guardarInstrumento(datos + ("hasta" to hoy.minusDays(3).toString())) }.isFailure)
        repo.guardarInstrumento(datos)
        val id = db.dao().instrumentos().single { it.serie == "QA-INS-01" }.id
        assertTrue(runCatching { repo.guardarInstrumento(datos + ("tipoPruebaId" to "OPERACION_RELE")) }.isFailure)
        repo.guardarInstrumento(datos + mapOf("marcaModelo" to "Modelo B", "desde" to hoy.toString()))
        assertEquals("Modelo B", db.dao().instrumentos().single { it.id == id }.marcaModelo)
        assertEquals(2, db.dao().calibracions().count { it.instrumentoId == id })
    }

    @Test
    fun instalacionNuevaPerteneceAlClienteYRechazaComunaDesconocida() = runBlocking {
        entrar("mgodoy@induztek.ejemplo")
        val comuna = db.dao().comunas().first().id
        val datos = mapOf("clienteId" to "pacifico", "nombre" to "Bodega de prueba",
            "ubicacion" to "Sector de pruebas", "comuna" to comuna)
        assertTrue(runCatching { repo.guardarInstalacion(datos + ("comuna" to "XXX")) }.isFailure)
        assertTrue(runCatching { repo.guardarInstalacion(datos + ("clienteId" to "mgodoy")) }.isFailure)
        repo.guardarInstalacion(datos)
        val creada = db.dao().instalacions().single { it.nombre == "Bodega de prueba" }
        assertEquals("pacifico", creada.clienteId)
        assertEquals(comuna, creada.comuna)
    }

    @Test
    fun protocoloDeReleExigeMedicionYCierreAntesDeFormalizar() = runBlocking {
        entrar("mgodoy@induztek.ejemplo")
        repo.guardarEquipo(
            mapOf("instalacionId" to "lampa", "tipoId" to "RELE", "tag" to "REL-QA",
                "marca" to "Ficticia", "modelo" to "Prueba", "serie" to "REL-QA")
        )
        val equipo = repo.datos().equipos.single { it.tag == "REL-QA" }
        val fecha = Validacion.hoy().toString()
        repo.guardarVisita(
            mapOf("instalacionId" to "lampa", "ingenieroId" to "acontreras",
                "ot" to "OT-QA", "fecha" to fecha, "tipo" to "SOLO_PRUEBAS")
        )
        val visita = repo.datos().visitas.single { it.ot == "OT-QA" }
        val id = repo.guardarProtocolo(
            mapOf("visitaId" to visita.id, "equipoId" to equipo.id, "fechaHora" to "${fecha}T00:00",
                "temperatura" to "19", "humedad" to "60")
        )
        repo.corregirProtocolo(id, mapOf("temperatura" to "20", "humedad" to "65"))
        assertEquals(20.0, repo.datos().protocolos.single { it.id == id }.temperatura ?: 0.0, 0.001)
        assertTrue(runCatching { repo.cambiarEstado(id, Estado.REGISTRADO_TERRENO, emptyMap()) }.isFailure)
        repo.guardarPrueba(
            id, mapOf("tipoId" to "OPERACION_RELE", "elemento" to "General", "momento" to "UNICA",
                "realizada" to "SI", "opera" to "SI", "instrumentoId" to "inyeccion",
                "CORRIENTE_AJUSTE" to "5", "CORRIENTE_OPERACION" to "5.1",
                "TIEMPO_ESPERADO" to "100", "TIEMPO_MEDIDO" to "104")
        )
        repo.cambiarEstado(id, Estado.REGISTRADO_TERRENO, emptyMap())
        entrar("acontreras@induztek.ejemplo")
        assertTrue(runCatching { repo.cambiarEstado(id, Estado.FORMALIZADO, emptyMap()) }.isFailure)
        entrar("mgodoy@induztek.ejemplo")
        repo.cerrarVisita(visita.id, mapOf("nombre" to "Marcela Fuentes", "rut" to "14.226.791-8", "cargo" to "Jefa"))
        entrar("acontreras@induztek.ejemplo")
        repo.cambiarEstado(id, Estado.FORMALIZADO, emptyMap())
        entrar("mfuentes@envpacifico.ejemplo")
        val informe = repo.documentoProtocolo(id)
        assertTrue(informe.contains("Formalizado"))
        assertTrue(informe.contains("104.00"))
        assertTrue(repo.documentoActa(visita.id).contains("OT-QA"))
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

    @Test
    fun clienteLocalCambiaSuClaveSinJefaturaYConservaSusDatos() = runBlocking {
        val correo = "mfuentes@envpacifico.ejemplo"
        val actual = "Induztek2026!"
        val nueva = "ClientePrueba2026!"
        entrar(correo)
        val antes = repo.datos()
        assertEquals("actual", (runCatching { repo.cambiarClaveLocal("incorrecta", nueva) }
            .exceptionOrNull() as ErrorFormulario).campos.keys.single())
        assertEquals("nueva", (runCatching { repo.cambiarClaveLocal(actual, actual) }
            .exceptionOrNull() as ErrorFormulario).campos.keys.single())
        repo.cambiarClaveLocal(actual, nueva)
        repo.salir()
        assertTrue(runCatching { entrar(correo) }.exceptionOrNull() is ErrorFormulario)
        repo.ingresar(mapOf("correo" to correo, "clave" to nueva))
        assertEquals(antes.usuario.id, repo.datos().usuario.id)
        assertEquals(antes.protocolos, repo.datos().protocolos)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AccesoViewModelTest {
    private lateinit var app: Application
    private lateinit var base: InduztekDatabase
    private lateinit var vm: AppViewModel
    private lateinit var store: ViewModelStore

    @Before
    fun abrir() {
        app = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(app, InduztekDatabase::class.java).build()
        store = ViewModelStore()
        vm = androidx.lifecycle.ViewModelProvider(
            store, object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(app, base, InduztekRepository(base), Sincronizador(app, base)) as T
            },
        )[AppViewModel::class.java]
        esperar { !vm.estado.value.cargando }
        assertNull(vm.estado.value.errorInicio)
    }

    @After
    fun cerrar() {
        store.clear()
        base.close()
    }

    private fun esperar(terminado: () -> Boolean) {
        val limite = System.nanoTime() + 15_000_000_000L
        while (!terminado() && System.nanoTime() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("No se completó la operación", terminado())
    }

    private fun ejecutar(accion: Accion, campos: Map<String, String> = emptyMap()): Result<String> {
        var resultado: Result<String>? = null
        vm.ejecutar(accion, "", campos, { resultado = Result.failure(it) }, { resultado = Result.success(it) })
        esperar { resultado != null }
        assertFalse(vm.estado.value.ocupado)
        return requireNotNull(resultado)
    }

    @Test
    fun sesionValidaCargaSuPerfilYSalirLimpiaEstado() {
        val campos = mapOf("correo" to "mgodoy@induztek.ejemplo", "clave" to "Induztek2026!")
        assertTrue(ejecutar(Accion.LOGIN, campos).isSuccess)
        assertEquals("mgodoy", vm.estado.value.datos?.usuario?.id)
        vm.recargar()
        vm.salir()
        assertNull(vm.estado.value.datos)
        assertNull(app.getSharedPreferences("sesion", 0).getString("usuarioId", null))
    }

    @Test
    fun formulariosIncompletosReportanErroresYNoCreanRegistros() {
        val campos = mapOf("correo" to "respinoza@induztek.ejemplo", "clave" to "Induztek2026!")
        assertTrue(ejecutar(Accion.LOGIN, campos).isSuccess)
        val dao = base.dao()
        val antes = runBlocking { listOf(dao.usuarios().size, dao.equipos().size, dao.protocolos().size) }
        val acciones = listOf(
            Accion.CLIENTE, Accion.CUENTA, Accion.INSTALACION, Accion.EQUIPO, Accion.VISITA,
            Accion.PROTOCOLO, Accion.CORREGIR, Accion.PRUEBA, Accion.REGISTRAR, Accion.FORMALIZAR,
            Accion.OBSERVAR, Accion.ANULAR, Accion.CERRAR, Accion.INSTRUMENTO, Accion.PERSONAL,
        )
        acciones.forEach { assertTrue(it.name, ejecutar(it).isFailure) }
        assertEquals(antes, runBlocking { listOf(dao.usuarios().size, dao.equipos().size, dao.protocolos().size) })
    }

    @Test
    fun fotografiaValidaActualizaFichaYArchivoGrandePreservaLaAnterior() {
        val campos = mapOf("correo" to "mgodoy@induztek.ejemplo", "clave" to "Induztek2026!")
        assertTrue(ejecutar(Accion.LOGIN, campos).isSuccess)
        val id = requireNotNull(vm.estado.value.datos).equipos.first().id
        val foto = byteArrayOf(1, 2, 3)
        var aviso: String? = null
        vm.foto(id, foto) { aviso = it }
        esperar { aviso != null }
        assertEquals("Fotografía guardada", aviso)
        assertArrayEquals(foto, requireNotNull(vm.estado.value.datos).equipos.first { it.id == id }.foto)

        aviso = null
        vm.foto(id, ByteArray(500_001)) { aviso = it }
        esperar { aviso != null }
        assertEquals("Imagen demasiado grande", aviso)
        assertArrayEquals(foto, requireNotNull(vm.estado.value.datos).equipos.first { it.id == id }.foto)
        assertFalse(vm.estado.value.ocupado)
    }

    @Test
    fun consultaElProtocoloPermitidoYRechazaIdentificadorInexistente() {
        val campos = mapOf("correo" to "mfuentes@envpacifico.ejemplo", "clave" to "Induztek2026!")
        assertTrue(ejecutar(Accion.LOGIN, campos).isSuccess)
        var salida: Pair<String?, String?>? = null
        vm.documento("p-2026", false) { texto, error -> salida = texto to error }
        esperar { salida != null }
        assertTrue(salida?.first.orEmpty().contains("PT-2026-0401"))
        assertNull(salida?.second)
        salida = null
        vm.documento("inexistente", false) { texto, error -> salida = texto to error }
        esperar { salida != null }
        assertNull(salida?.first)
        assertNotNull(salida?.second)
    }
}