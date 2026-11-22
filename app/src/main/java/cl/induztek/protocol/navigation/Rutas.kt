package cl.induztek.protocol.navigation

import android.net.Uri
import cl.induztek.protocol.viewmodel.Accion

sealed class Ruta(val ruta: String) {
    data object Panel : Ruta("panel")

    data object Equipos : Ruta("equipos")

    data object Equipo : Ruta("equipo/{id}")

    data object Visitas : Ruta("visitas")

    data object Visita : Ruta("visita/{id}")

    data object Protocolos : Ruta("protocolos")

    data object Protocolo : Ruta("protocolo/{id}")

    data object Clientes : Ruta("clientes")

    data object Instrumentos : Ruta("instrumentos")

    data object Formulario : Ruta("form/{accion}/{contexto}?item={item}") {
        fun crear(accion: Accion, contexto: String, item: String) =
            "form/${accion.name}/${Uri.encode(contexto)}?item=${Uri.encode(item)}"
    }
}
