package cl.induztek.protocol.data

import androidx.room.withTransaction
import cl.induztek.protocol.domain.Claves
import cl.induztek.protocol.model.*

object DatosSemilla {
    suspend fun preparar(db: InduztekDatabase) =
        db.withTransaction {
            val dao = db.dao()
            if (dao.rols().isNotEmpty()) return@withTransaction
            Perfil.entries.forEach { dao.insertar(Rol(it.name, it.titulo)) }


            suspend fun usuario(
                id: String,
                rol: Perfil,
                nombre: String,
                rut: String,
                correo: String,
                contacto: String = "",
                cargo: String = "",
            ) {
                dao.insertar(
                    Usuario(
                        id,
                        rol.name,
                        nombre,
                        rut,
                        correo,
                        Claves.crear("Induztek2026!"),
                        contacto = contacto,
                        cargo = cargo,
                    )
                )
            }
            usuario(
                "mgodoy",
                Perfil.TECNICO,
                "Matías Godoy Herrera",
                "17.284.519-3",
                "mgodoy@induztek.ejemplo",
            )
            usuario(
                "jtapia",
                Perfil.TECNICO,
                "Javiera Tapia Muñoz",
                "19.412.087-7",
                "jtapia@induztek.ejemplo",
            )
            usuario(
                "crondon",
                Perfil.TECNICO,
                "Carlos Rondón Pérez",
                "26.104.583-4",
                "crondon@induztek.ejemplo",
            )
            usuario(
                "dvalenzuela",
                Perfil.TECNICO,
                "Diego Valenzuela Ríos",
                "20.137.648-3",
                "dvalenzuela@induztek.ejemplo",
            )
            usuario(
                "acontreras",
                Perfil.INGENIERO,
                "Andrea Contreras Silva",
                "15.906.334-8",
                "acontreras@induztek.ejemplo",
            )
            usuario(
                "smorales",
                Perfil.INGENIERO,
                "Sebastián Morales Gutiérrez",
                "16.553.270-8",
                "smorales@induztek.ejemplo",
            )
            usuario(
                "respinoza",
                Perfil.JEFE_TECNICO,
                "Ricardo Espinoza Lagos",
                "13.871.905-7",
                "respinoza@induztek.ejemplo",
            )
            usuario(
                "pacifico",
                Perfil.CLIENTE,
                "Envasadora del Pacífico S.A.",
                "76.482.915-8",
                "mfuentes@envpacifico.ejemplo",
                "Marcela Fuentes Rojas",
                "Jefa de Mantenimiento",
            )
            usuario(
                "sanitaria",
                Perfil.CLIENTE,
                "Sanitaria Valle Central S.A.",
                "96.731.540-0",
                "jarancibia@svcentral.ejemplo",
                "Jorge Arancibia Leiva",
                "Contacto principal",
            )
            usuario(
                "minera",
                Perfil.CLIENTE,
                "Minera Cerro Alto SpA",
                "77.105.362-9",
                "pnavarro@cerroalto.ejemplo",
                "Patricio Navarro Soto",
                "Jefe de Mantenimiento Eléctrico",
            )
            usuario(
                "clinica",
                Perfil.CLIENTE,
                "Clínica Los Aromos Ltda.",
                "76.903.218-5",
                "tleiva@losaromos.ejemplo",
                "Tomás Leiva Araya",
                "Supervisor Eléctrico",
            )
            usuario(
                "cementos",
                Perfil.CLIENTE,
                "Cementos Lomas Blancas S.A.",
                "99.524.170-6",
                "lbravo@lomasblancas.ejemplo",
                "Luis Bravo Castillo",
                "Contacto principal",
            )
            usuario(
                "datacenter",
                Perfil.CLIENTE,
                "Data Center Andes Norte SpA",
                "78.214.635-1",
                "fordenes@andesnorte.ejemplo",
                "Felipe Órdenes Vera",
                "Contacto principal",
            )
            dao.insertar(Region("RM", "Región Metropolitana"))
            listOf("Lampa", "San Bernardo", "Til Til", "La Florida", "Puente Alto", "Huechuraba")
                .forEach { dao.insertar(Comuna(it, "RM")) }
            val lugares =
                listOf(
                    Instalacion(
                        "lampa",
                        "pacifico",
                        "Planta Lampa",
                        "Sector industrial",
                        "Lampa",
                        "Inducción obligatoria 30 min",
                    ),
                    Instalacion(
                        "sur",
                        "sanitaria",
                        "Planta Elevadora Sur",
                        "Sector rural poniente",
                        "San Bernardo",
                        "Sala subterránea sin señal",
                    ),
                    Instalacion(
                        "tiltil",
                        "minera",
                        "Faena Til Til",
                        "Ruta 5 Norte",
                        "Til Til",
                        "Sin señal",
                    ),
                    Instalacion(
                        "edificio",
                        "clinica",
                        "Edificio Clínico",
                        "Zona urbana",
                        "La Florida",
                        "Cortes domingos 06:00–10:00",
                    ),
                    Instalacion(
                        "molienda",
                        "cementos",
                        "Planta Molienda",
                        "Sector precordillera",
                        "Puente Alto",
                        "Respirador obligatorio",
                    ),
                    Instalacion(
                        "site",
                        "datacenter",
                        "Site Huechuraba",
                        "Parque empresarial",
                        "Huechuraba",
                        "Acompañamiento del cliente",
                    ),
                )
            lugares.forEach { dao.insertar(it) }
            Catalogo.equipos.forEach { (id, nombre) ->
                dao.insertar(TipoEquipo(id, nombre, if (id == "RELE") "SECUNDARIO" else "PRIMARIO"))
            }
            Catalogo.pruebas.forEach { (id, nombre) ->
                dao.insertar(TipoPrueba(id, nombre))
                Catalogo.equipos.keys
                    .flatMap { Catalogo.parametros(it, id) }
                    .distinct()
                    .forEach { codigo ->
                        dao.insertar(
                            Parametro(
                                "$id:$codigo",
                                id,
                                codigo,
                                Catalogo.etiquetas.getValue(codigo),
                                Catalogo.unidades.getValue(codigo),
                            )
                        )
                    }
            }
            suspend fun equipo(
                id: String,
                tag: String,
                tipo: String,
                instalacion: String,
                marca: String,
                serie: String,
                anio: Int,
                potencia: Double? = null,
                tensiones: String = "",
                corriente: Double? = null,
                max: Double? = null,
            ) {
                dao.insertar(
                    Equipo(
                        id,
                        instalacion,
                        tipo,
                        tag,
                        marca,
                        "Referencia Anexo 9",
                        serie,
                        anio,
                        potencia,
                        tensiones,
                        corriente,
                        max,
                    )
                )
            }
            equipo(
                "tr01",
                "TR-01",
                "TRANSFORMADOR",
                "lampa",
                "Rhona",
                "RH-18733",
                2012,
                1500.0,
                "12 / 0,4 kV",
            )
            equipo(
                "tr02",
                "TR-02",
                "TRANSFORMADOR",
                "lampa",
                "Rhona",
                "RH-21904",
                2017,
                1000.0,
                "12 / 0,4 kV",
            )
            equipo(
                "tra",
                "TR-A",
                "TRANSFORMADOR",
                "sur",
                "Tusan",
                "TS-5521",
                2009,
                2500.0,
                "23 / 0,4 kV",
            )
            equipo(
                "cel",
                "CEL-MT-03",
                "CELDA",
                "edificio",
                "Ormazabal",
                "OR-77812",
                2018,
                tensiones = "12 kV",
                corriente = 630.0,
                max = 60.0,
            )
            equipo(
                "int",
                "INT-52-1",
                "INTERRUPTOR",
                "tiltil",
                "Schneider",
                "SE-449021",
                2015,
                tensiones = "23 kV",
                corriente = 1250.0,
                max = 45.0,
            )
            equipo(
                "rel",
                "REL-50/51-A",
                "RELE",
                "molienda",
                "SEL",
                "SEL-751-30219",
                2020,
                corriente = 5.0,
            )
            equipo(
                "cab",
                "CAB-MT-02",
                "CABLE",
                "site",
                "Nexans",
                "NX-2201-B",
                2019,
                tensiones = "15 kV; 3×240 mm²; 180 m",
            )
            equipo(
                "trss",
                "TR-SS-01",
                "TRANSFORMADOR",
                "tiltil",
                "ABB",
                "ABB-1180442",
                2011,
                5000.0,
                "23 / 6,6 kV",
            )
            suspend fun instrumento(
                id: String,
                tipo: String,
                nombre: String,
                modelo: String,
                serie: String,
                desde: String,
                hasta: String,
            ) {
                dao.insertar(Instrumento(id, tipo, nombre, modelo, serie))
                dao.insertar(Calibracion("cal-$id", id, desde, hasta))
            }
            instrumento(
                "meg5",
                "AISLACION",
                "Megóhmetro 5 kV",
                "Megger MIT525",
                "MIT-0045821",
                "2026-03-15",
                "2027-03-15",
            )
            instrumento(
                "meg10",
                "AISLACION",
                "Megóhmetro 10 kV",
                "Megger MIT1025",
                "MIT-0071233",
                "2025-10-10",
                "2026-10-10",
            )
            instrumento(
                "ttr",
                "TTR",
                "Medidor TTR",
                "Megger TTR300",
                "TTR-30044",
                "2026-06-02",
                "2027-06-02",
            )
            instrumento(
                "micro",
                "CONTACTOS",
                "Micro-ohmímetro (vencido)",
                "Megger DLRO10X",
                "DLR-10882",
                "2025-08-20",
                "2026-08-20",
            )
            instrumento(
                "inyeccion",
                "OPERACION_RELE",
                "Inyección secundaria",
                "Omicron CMC 356",
                "CMC-HB411",
                "2026-01-05",
                "2027-01-05",
            )


            instrumento(
                "dev-demo",
                "DEVANADOS",
                "Medidor devanados · sintético",
                "Demo académica",
                "DEMO-DEV-01",
                "2026-01-01",
                "2027-12-31",
            )
            instrumento(
                "contacto-demo",
                "CONTACTOS",
                "Micro-ohmímetro · sintético vigente",
                "Demo académica",
                "DEMO-CON-01",
                "2026-01-01",
                "2027-12-31",
            )

            dao.insertar(Calibracion("cal-meg5-historica-demo", "meg5", "2024-01-01", "2025-12-31"))
            val historial =
                listOf(
                    Triple("2024", 12500.0, 31250.0),
                    Triple("2025", 9800.0, 21560.0),
                    Triple("2026", 6100.0, 10980.0),
                )
            historial.forEach { (anio, uno, diez) ->
                val fecha =
                    when (anio) {
                        "2024" -> "2024-09-14"
                        "2025" -> "2025-09-13"
                        else -> "2026-09-26"
                    }
                val numero =
                    when (anio) {
                        "2024" -> "PT-2024-0312"
                        "2025" -> "PT-2025-0288"
                        else -> "PT-2026-0401"
                    }
                val estado = if (anio == "2026") Estado.REGISTRADO_TERRENO else Estado.FORMALIZADO
                val autor = if (anio == "2025") "jtapia" else "mgodoy"
                val visita =
                    Visita(
                        "vis-$anio",
                        "lampa",
                        "acontreras",
                        if (anio == "2026") "OT-2026-1187" else "OT-DEMO-$anio",
                        fecha,
                        TipoVisita.SOLO_PRUEBAS,
                        "${fecha}T13:42:00",
                    )
                dao.insertar(visita)
                dao.insertar(Participante("part-$anio", visita.id, autor))
                val p =
                    Protocolo(
                        "p-$anio",
                        numero,
                        visita.id,
                        "tr01",
                        autor,
                        "${fecha}T10:00:00",
                        if (anio == "2025") 21.0 else if (anio == "2024") 18.0 else 19.0,
                        if (anio == "2025") 48.0 else if (anio == "2024") 55.0 else 60.0,
                        estado = estado,
                    )
                dao.insertar(p)
                val pr =
                    Prueba(
                        "ais-$anio",
                        p.id,
                        "AISLACION",
                        "AT-T",
                        Momento.UNICA,
                        true,
                        instrumentoId = "meg5",
                        resultado = if (anio == "2026") Resultado.OBSERVADO else Resultado.APROBADO,
                        comentario = "Medición histórica del Anexo 9",
                    )
                dao.insertar(pr)
                mapOf("TENSION_PRUEBA" to 5.0, "LECTURA_1MIN" to uno, "LECTURA_10MIN" to diez)
                    .forEach { (k, v) ->
                        dao.insertar(Medicion("$anio-$k", pr.id, "AISLACION:$k", v))
                    }
                Catalogo.tipos("TRANSFORMADOR").forEach { tipo ->
                    Catalogo.elementos("TRANSFORMADOR", tipo).forEach { elemento ->
                        if (tipo != "AISLACION" || elemento != "AT-T")
                            dao.insertar(
                                Prueba(
                                    "$anio-$tipo-$elemento",
                                    p.id,
                                    tipo,
                                    elemento,
                                    Momento.UNICA,
                                    false,
                                    "Demo parcial: medición no suministrada en Anexo 9",
                                    null,
                                    null,
                                )
                            )
                    }
                }
                dao.insertar(
                    HistorialEstado(
                        "hist-$anio",
                        p.id,
                        if (estado == Estado.FORMALIZADO) "acontreras" else autor,
                        Estado.BORRADOR,
                        estado,
                        "${fecha}T13:00:00",
                        "Precarga histórica sintética; no representa una acción efectuada en la app",
                    )
                )
                dao.insertar(
                    Recepcion(
                        "rec-$anio",
                        visita.id,
                        "Marcela Fuentes Rojas",
                        "14.226.791-8",
                        "Jefa de Mantenimiento",
                        "${fecha}T13:42:00",
                        "INDUZTEK INGENIERÍA SpA\nACTA DE PRUEBA EN TERRENO · DATOS FICTICIOS\nPendiente de formalización\n$numero · TR-01 · Planta Lampa · Envasadora del Pacífico S.A.\nFecha: $fecha · Técnico: $autor\nAT-T · 5 kV · 1 min: $uno MΩ · 10 min: $diez MΩ\nResultado: ${pr.resultado?.titulo}\nLas demás pruebas no se realizaron en esta precarga parcial (datos no suministrados).\nRecibe: Marcela Fuentes Rojas · 14.226.791-8 · Jefa de Mantenimiento · ${fecha}T13:42:00\nEsta precarga histórica es ilustrativa; no es un certificado real.",
                    )
                )
            }


            data class Ejemplo(
                val id: String,
                val numero: String,
                val equipo: String,
                val tipo: String,
                val fecha: String,
                val autor: String,
                val instalacion: String,
                val ingeniero: String,
                val estado: Estado,
            )
            listOf(
                    Ejemplo(
                        "p-tr02",
                        "PT-2026-0402",
                        "tr02",
                        "TRANSFORMADOR",
                        "2026-09-26",
                        "mgodoy",
                        "lampa",
                        "acontreras",
                        Estado.REGISTRADO_TERRENO,
                    ),
                    Ejemplo(
                        "p-cel",
                        "PT-2026-0389",
                        "cel",
                        "CELDA",
                        "2026-09-20",
                        "crondon",
                        "edificio",
                        "smorales",
                        Estado.OBSERVADO,
                    ),
                    Ejemplo(
                        "p-rel",
                        "PT-2026-0377",
                        "rel",
                        "RELE",
                        "2026-09-15",
                        "dvalenzuela",
                        "molienda",
                        "acontreras",
                        Estado.FORMALIZADO,
                    ),
                    Ejemplo(
                        "p-int",
                        "PT-2026-0360",
                        "int",
                        "INTERRUPTOR",
                        "2026-09-08",
                        "jtapia",
                        "tiltil",
                        "smorales",
                        Estado.FORMALIZADO,
                    ),
                )
                .forEach { ej ->
                    val visitaId = if (ej.equipo == "tr02") "vis-2026" else "vis-${ej.id}"
                    if (ej.equipo != "tr02") {
                        dao.insertar(
                            Visita(
                                visitaId,
                                ej.instalacion,
                                ej.ingeniero,
                                if (ej.equipo == "cel") "OT-2026-1154" else "OT-DEMO-${ej.equipo}",
                                ej.fecha,
                                TipoVisita.SOLO_PRUEBAS,
                                "${ej.fecha}T13:00:00",
                            )
                        )
                        dao.insertar(Participante("part-${ej.id}", visitaId, ej.autor))
                    }
                    val p =
                        Protocolo(
                            ej.id,
                            ej.numero,
                            visitaId,
                            ej.equipo,
                            ej.autor,
                            "${ej.fecha}T08:30:00",
                            when (ej.equipo) {
                                "tr02" -> 19.0
                                "cel" -> 22.0
                                "rel" -> 24.0
                                else -> 27.0
                            },
                            when (ej.equipo) {
                                "tr02" -> 60.0
                                "cel" -> 45.0
                                "rel" -> 35.0
                                else -> 20.0
                            },
                            estado = ej.estado,
                            motivo =
                                if (ej.equipo == "cel")
                                    "Precarga histórica: falta número de serie del instrumento en formulario original"
                                else "",
                        )
                    dao.insertar(p)
                    val tipo =
                        when (ej.equipo) {
                            "rel" -> "OPERACION_RELE"
                            "int",
                            "cel" -> "CONTACTOS"
                            else -> "AISLACION"
                        }
                    val elemento =
                        when (tipo) {
                            "CONTACTOS" -> "Polo 1"
                            "AISLACION" -> "AT-T"
                            else -> "General"
                        }
                    val valores =
                        when (tipo) {
                            "OPERACION_RELE" ->
                                mapOf(
                                    "CORRIENTE_AJUSTE" to 5.0,
                                    "CORRIENTE_OPERACION" to 5.0,
                                    "TIEMPO_ESPERADO" to 100.0,
                                    "TIEMPO_MEDIDO" to 100.0,
                                )
                            "CONTACTOS" ->
                                mapOf(
                                    "RESISTENCIA_CONTACTO" to if (ej.equipo == "int") 60.0 else 66.0
                                )
                            else ->
                                mapOf(
                                    "TENSION_PRUEBA" to 5.0,
                                    "LECTURA_1MIN" to 8000.0,
                                    "LECTURA_10MIN" to 20000.0,
                                )
                        }
                    val resultado =
                        when (ej.equipo) {
                            "int" -> Resultado.RECHAZADO
                            "cel" -> Resultado.OBSERVADO
                            else -> Resultado.APROBADO
                        }
                    val instrumento =
                        when (tipo) {
                            "CONTACTOS" -> "contacto-demo"
                            "OPERACION_RELE" -> "inyeccion"
                            else -> "meg5"
                        }
                    val prueba =
                        Prueba(
                            "pr-${ej.id}",
                            p.id,
                            tipo,
                            elemento,
                            Momento.UNICA,
                            true,
                            instrumentoId = instrumento,
                            resultado = resultado,
                            comentario =
                                "Valores sintéticos adicionales para representar el resultado del Anexo 9; no son datos originales",
                        )
                    dao.insertar(prueba)
                    valores.forEach { (k, v) ->
                        dao.insertar(Medicion("${ej.id}-$k", prueba.id, "$tipo:$k", v))
                    }
                    Catalogo.tipos(ej.tipo).forEach { t ->
                        Catalogo.elementos(ej.tipo, t).forEach { el ->
                            if (t != tipo || el != elemento)
                                dao.insertar(
                                    Prueba(
                                        "${ej.id}-$t-$el",
                                        p.id,
                                        t,
                                        el,
                                        Momento.UNICA,
                                        false,
                                        "Demo parcial: valores no aportados por el anexo",
                                        null,
                                        null,
                                    )
                                )
                        }
                    }
                    dao.insertar(
                        HistorialEstado(
                            "h-${ej.id}",
                            p.id,
                            if (ej.estado == Estado.REGISTRADO_TERRENO) ej.autor else ej.ingeniero,
                            Estado.REGISTRADO_TERRENO,
                            ej.estado,
                            "${ej.fecha}T12:00:00",
                            "Precarga histórica sintética; no es actividad real de usuarios",
                        )
                    )
                    if (ej.equipo == "cel")
                        dao.insertar(
                            Recepcion(
                                "rec-cel",
                                visitaId,
                                "Tomás Leiva Araya",
                                "18.390.452-3",
                                "Supervisor Eléctrico",
                                "2026-09-20T09:55:00",
                                "INDUZTEK · ACTA HISTÓRICA DE EJEMPLO\nPendiente de formalización\nOT-2026-1154 · Clínica Los Aromos · Edificio Clínico\nPT-2026-0389 · CEL-MT-03 · Carlos Rondón\nContacto Polo 1: 66 µΩ, máximo de placa 60 µΩ · Observado\nOtras pruebas: no realizadas (datos no aportados en precarga).\nInstrumento sintético contacto-demo; vence 2027-12-31.\nRecibe Tomás Leiva Araya · 18.390.452-3 · Supervisor Eléctrico · 2026-09-20T09:55:00\nDatos de medición sintéticos, no reproducen un acta real.",
                            )
                        )
                }

            val lector = InduztekRepository(db)
            lector.restaurar("mgodoy")
            val vista = lector.datos()
            dao.recepcions().forEach { r ->
                dao.actualizar(
                    r.copy(
                        documento =
                            "PRECARGA HISTÓRICA ILUSTRATIVA · DATOS FICTICIOS\n" +
                                cl.induztek.protocol.domain.Documentos.acta(
                                    vista,
                                    r.visitaId,
                                    r.nombre,
                                    r.rut,
                                    r.cargo,
                                    r.fechaHora,
                                )
                    )
                )
            }

            dao.insertar(
                Visita(
                    "vis-demo",
                    "lampa",
                    "acontreras",
                    "OT-DEMO-ABIERTA",
                    "2026-10-18",
                    TipoVisita.PRUEBAS_Y_MANT,
                )
            )
            dao.insertar(Participante("part-demo", "vis-demo", "mgodoy"))
        }
}
