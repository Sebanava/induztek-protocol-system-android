package cl.induztek.protocol

import cl.induztek.protocol.domain.*
import cl.induztek.protocol.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReglasPruebaTest {
    private fun aislacion(uno: Double, diez: Double) =
        ReglasPrueba.resultado(
            "TRANSFORMADOR",
            "AISLACION",
            mapOf("LECTURA_1MIN" to uno, "LECTURA_10MIN" to diez),
            null,
        )

    @Test
    fun ipLimites() {
        assertEquals(Resultado.APROBADO, aislacion(100.0, 200.0))
        assertEquals(Resultado.OBSERVADO, aislacion(100.0, 100.0))
        assertEquals(Resultado.RECHAZADO, aislacion(100.0, 99.0))
    }

    @Test
    fun lecturaInferiorACienRechazaAunqueIpBueno() {
        assertEquals(Resultado.RECHAZADO, aislacion(99.0, 300.0))
    }

    @Test
    fun casoHistoricoDelAnexo() {
        assertEquals(Resultado.OBSERVADO, aislacion(6100.0, 10980.0))
        assertEquals(-37.7551, ReglasPrueba.variacion(6100.0, 9800.0)!!, .0001)
    }

    @Test
    fun ttrLimites() {
        fun t(v: Double) =
            ReglasPrueba.resultado(
                "TRANSFORMADOR",
                "TTR",
                mapOf("RELACION_TEORICA" to 100.0, "RELACION_MEDIDA" to v),
                null,
            )
        assertEquals(Resultado.APROBADO, t(100.5))
        assertEquals(Resultado.OBSERVADO, t(101.0))
        assertEquals(Resultado.RECHAZADO, t(101.1))
    }

    @Test
    fun contactosLimites() {
        fun c(v: Double) =
            ReglasPrueba.resultado("CELDA", "CONTACTOS", mapOf("RESISTENCIA_CONTACTO" to v), 60.0)
        assertEquals(Resultado.APROBADO, c(60.0))
        assertEquals(Resultado.OBSERVADO, c(72.0))
        assertEquals(Resultado.RECHAZADO, c(72.1))
    }

    @Test
    fun releLimitesYNoOpera() {
        fun r(t: Double, opera: Boolean = true) =
            ReglasPrueba.resultado(
                "RELE",
                "OPERACION_RELE",
                mapOf("TIEMPO_ESPERADO" to 100.0, "TIEMPO_MEDIDO" to t),
                null,
                opera,
            )
        assertEquals(Resultado.APROBADO, r(105.0))
        assertEquals(Resultado.OBSERVADO, r(110.0))
        assertEquals(Resultado.RECHAZADO, r(111.0))
        assertEquals(Resultado.RECHAZADO, r(100.0, false))
    }

    @Test
    fun devanadosNoTieneResultadoAutomatico() {
        assertNull(
            ReglasPrueba.resultado(
                "TRANSFORMADOR",
                "DEVANADOS",
                mapOf("RESISTENCIA" to 1842.0),
                null,
            )
        )
    }

    @Test
    fun celdaNoInventaCriterioDeAislacion() {
        assertNull(
            ReglasPrueba.resultado("CELDA", "AISLACION", mapOf("LECTURA_1MIN" to 500.0), null)
        )
    }

    @Test
    fun globalPeorResultadoYPendientes() {
        assertEquals(
            Resultado.RECHAZADO,
            ReglasPrueba.global(listOf(Resultado.APROBADO, Resultado.RECHAZADO)),
        )
        assertNull(ReglasPrueba.global(listOf(Resultado.APROBADO, null)))
        assertNull(ReglasPrueba.global(emptyList()))
    }

    @Test
    fun variacionMayorEstrictaConCero() {
        assertFalse(ReglasPrueba.significativa(25.0))
        assertFalse(ReglasPrueba.significativa(-25.0))
        assertTrue(ReglasPrueba.significativa(-25.01))
        assertNull(ReglasPrueba.variacion(10.0, 0.0))
    }

    @Test
    fun plazoDosDiasHabiles() {
        val viernes = LocalDate.of(2026, 10, 16)
        assertFalse(ReglasPrueba.atrasado(viernes, LocalDate.of(2026, 10, 20)))
        assertTrue(ReglasPrueba.atrasado(viernes, LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun rutValidoConYSinFormato() {
        assertTrue(Validacion.rut("14.226.791-8"))
        assertTrue(Validacion.rut("142267918"))
        assertFalse(Validacion.rut("14.226.791-9"))
        assertFalse(Validacion.rut("abc"))
    }

    @Test
    fun numerosRechazanNaNInfinitoNegativosYCero() {
        listOf("NaN", "Infinity", "-1", "0").forEach { v ->
            assertThrows(ErrorFormulario::class.java) { Validacion.numero(mapOf("x" to v), "x") }
        }
        assertEquals(1.25, Validacion.numero(mapOf("x" to "1,25"), "x"), .0001)
    }

    @Test
    fun hashNoEsLaClaveYUsaSal() {
        val a = Claves.crear("demo")
        val b = Claves.crear("demo")
        assertNotEquals(a, b)
        assertTrue(Claves.verificar("demo", a))
        assertFalse(Claves.verificar("otra", a))
    }

    @Test
    fun tecnicoNoFormalizaYFormalizadoInmutable() {
        assertFalse(
            Permisos.transicion(Perfil.TECNICO, Estado.REGISTRADO_TERRENO, Estado.FORMALIZADO)
        )
        assertTrue(
            Permisos.transicion(Perfil.INGENIERO, Estado.REGISTRADO_TERRENO, Estado.FORMALIZADO)
        )
        Estado.entries.forEach {
            assertFalse(Permisos.transicion(Perfil.JEFE_TECNICO, Estado.FORMALIZADO, it))
        }
    }

    @Test
    fun soloCreadorEditaBorradorUObservado() {
        assertTrue(Permisos.editar(Perfil.TECNICO, "uno", "uno", Estado.OBSERVADO))
        assertFalse(Permisos.editar(Perfil.TECNICO, "otro", "uno", Estado.BORRADOR))
        assertFalse(Permisos.editar(Perfil.CLIENTE, "uno", "uno", Estado.BORRADOR))
    }

    @Test
    fun clienteNoVeBorradoresNiAnulados() {
        assertFalse(Estado.BORRADOR.visible(Perfil.CLIENTE))
        assertFalse(Estado.ANULADO.visible(Perfil.CLIENTE))
        assertEquals("Pendiente de formalización", Estado.OBSERVADO.etiqueta(Perfil.CLIENTE))
    }

    @Test
    fun ttrDecimalEnUnoPorCientoDebeSerObservado() {
        assertEquals(
            Resultado.OBSERVADO,
            ReglasPrueba.resultado(
                "TRANSFORMADOR",
                "TTR",
                mapOf("RELACION_TEORICA" to 30.0, "RELACION_MEDIDA" to 30.3),
                null,
            ),
        )
    }

    @Test
    fun variacionDecimalEnVeinticincoNoEsSignificativa() {
        val variacion = ReglasPrueba.variacion(0.375, 0.3)
        assertFalse(ReglasPrueba.significativa(variacion))
    }
}
