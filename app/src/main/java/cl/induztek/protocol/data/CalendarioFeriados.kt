package cl.induztek.protocol.data

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

internal data class Feriado(
    val date: String?,
    val name: String?,
    val nationalHoliday: Boolean,
    val holidayTypes: List<String>?,
)

internal interface CalendarioNagerApi {
    @GET("api/v4/Holidays/CL/{anio}")
    suspend fun feriados(@Path("anio") anio: Int): List<Feriado>
}

internal class CalendarioFeriados(private val api: CalendarioNagerApi) {
    suspend fun buscar(fecha: LocalDate): String? =
        api.feriados(fecha.year).firstNotNullOfOrNull {
            it.name?.takeIf { nombre ->
                nombre.isNotBlank() && it.nationalHoliday && it.date == fecha.toString() &&
                    it.holidayTypes.orEmpty().contains("Public")
            }
        }

    companion object {
        val remoto by lazy {
            val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
            val api = Retrofit.Builder()
                .baseUrl("https://nagerholidays.com/")
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CalendarioNagerApi::class.java)
            CalendarioFeriados(api)
        }
    }
}