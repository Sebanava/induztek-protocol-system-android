package cl.induztek.protocol

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.induztek.protocol.data.*
import cl.induztek.protocol.sync.*
import com.google.gson.*
import java.io.File
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
class SincronizacionTest {
    private lateinit var db: InduztekDatabase
    private val contexto
        get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun preparar() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(contexto, InduztekDatabase::class.java).build()
        DatosSemilla.preparar(db)
    }

    @After
    fun cerrar() {
        db.close()
    }

    @Test
    fun huellaDeRegistroIgnoraCamposNulosOmitidosPorElServidor() {
        val local = JsonParser.parseString("""{"historial":[{"anterior":null,"nuevo":"BORRADOR"}]}""")
        val remoto = JsonParser.parseString("""{"historial":[{"nuevo":"BORRADOR"}]}""")
        val formalizado = JsonParser.parseString("""{"historial":[{"nuevo":"FORMALIZADO"}]}""")

        assertEquals(
            AlmacenSincronizacion.huella(remoto),
            AlmacenSincronizacion.huella(local, omitirNulos = true),
        )
        assertNotEquals(
            AlmacenSincronizacion.huella(formalizado),
            AlmacenSincronizacion.huella(local, omitirNulos = true),
        )
    }

    @Test
    fun exportacionContieneAgregadosCompletosYGeneraFixturesSinteticos() = runBlocking {
        val registros = AlmacenSincronizacion(db).exportar()
        assertEquals(7, registros.count { it.tipo == "protocolo" })
        assertTrue(
            registros.first { it.id == "p-2026" }.datos.getAsJsonArray("mediciones").size() > 0
        )
        val carpeta = File("build/fixtures-sync").apply { mkdirs() }
        File(carpeta, "registros-demo.json")
            .writeText(
                GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(registros)
            )
        val usuarios = Gson().toJsonTree(db.dao().usuarios()).asJsonArray
        usuarios.forEach { it.asJsonObject.remove("claveHash") }
        File(carpeta, "usuarios-demo.json")
            .writeText(GsonBuilder().setPrettyPrinting().create().toJson(usuarios))
    }

    @Test
    fun exportarImportarNoPierdeMedicionesYVersion() = runBlocking {
        val almacen = AlmacenSincronizacion(db)
        val original = almacen.exportar().first { it.id == "p-2026" }
        almacen.aplicar(RegistroRemoto(original.tipo, original.id, 3, original.datos))
        val nuevo = almacen.exportar().first { it.id == "p-2026" }
        assertEquals(
            AlmacenSincronizacion.huella(original.datos),
            AlmacenSincronizacion.huella(nuevo.datos),
        )
        assertEquals(3L, almacen.metas().first { it.registroId == original.id }.version)
    }

    @Test
    fun huellaIgnoraOrdenDeFilasPeroNoOrdenDePixeles() {
        assertEquals(
            AlmacenSincronizacion.huella(
                JsonParser.parseString("{\"x\":1.0,\"rows\":[{\"id\":\"b\"},{\"id\":\"a\"}]}")
            ),
            AlmacenSincronizacion.huella(
                JsonParser.parseString("{\"rows\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"x\":1}")
            ),
        )
        assertNotEquals(
            AlmacenSincronizacion.huella(JsonParser.parseString("[1,2,3]")),
            AlmacenSincronizacion.huella(JsonParser.parseString("[3,2,1]")),
        )
    }

    @Test
    fun migracionConservaLaVersionUno() = runBlocking {
        val esquema = JsonParser.parseString(
            File("schemas/cl.induztek.protocol.data.InduztekDatabase/1.json").readText()
        ).asJsonObject.getAsJsonObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                esquema.getAsJsonArray("entities").forEach { entidad ->
                    val tabla = entidad.asJsonObject
                    val nombre = tabla["tableName"].asString
                    db.execSQL(tabla["createSql"].asString.replace("\${TABLE_NAME}", nombre))
                    tabla.getAsJsonArray("indices").forEach { indice ->
                        db.execSQL(indice.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", nombre))
                    }
                }
                db.execSQL("INSERT INTO rol (id,nombre) VALUES ('TECNICO','Técnico de prueba')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                MIGRACION_1_2.migrate(db)
            }
        }
        val configuracion = SupportSQLiteOpenHelper.Configuration.builder(contexto).callback(callback).build()
        FrameworkSQLiteOpenHelperFactory().create(configuracion).use { conexion ->
            val db = conexion.writableDatabase
            callback.onUpgrade(db, 1, 2)
            db.query("SELECT nombre FROM rol WHERE id='TECNICO'").use { fila ->
                assertTrue(fila.moveToFirst())
                assertEquals("Técnico de prueba", fila.getString(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sincronizacion_meta'").use { fila ->
                assertTrue(fila.moveToFirst())
            }
        }
    }
}
