package cl.induztek.protocol.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import cl.induztek.protocol.domain.ErrorFormulario
import kotlinx.coroutines.flow.*

data class FormularioUiState(
    val valores: Map<String, String> = emptyMap(),
    val errores: Map<String, String> = emptyMap(),
)

class FormularioViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val _estado =
        MutableStateFlow(
            FormularioUiState(saved.get<HashMap<String, String>>("valores") ?: emptyMap())
        )
    val estado = _estado.asStateFlow()

    fun iniciar(iniciales: Map<String, String>) {
        if (saved.get<Boolean>("iniciado") == true) return
        _estado.update { it.copy(valores = iniciales) }
        saved["iniciado"] = true
        guardar()
    }

    fun cambiar(clave: String, valor: String) {
        _estado.update {
            it.copy(valores = it.valores + (clave to valor), errores = it.errores - clave)
        }
        guardar()
    }

    fun error(error: Throwable) {
        _estado.update {
            it.copy(
                errores =
                    if (error is ErrorFormulario) error.campos
                    else mapOf("general" to (error.message ?: "No fue posible guardar"))
            )
        }
    }

    fun limpiarClave() {
        _estado.update { it.copy(valores = it.valores + ("clave" to "")) }
        guardar()
    }

    private fun guardar() {
        saved["valores"] = HashMap(_estado.value.valores.filterKeys { it != "clave" })
    }
}
