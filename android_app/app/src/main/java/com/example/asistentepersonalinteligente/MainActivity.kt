package com.example.asistentepersonalinteligente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// --- 1. Modelos de Datos (deben coincidir con el backend) ---
// Marcados con @Serializable para que Ktor sepa cómo convertirlos a JSON.

@Serializable
data class RecetasInput(
    val nombres_recetas: List<String>,
    val ingredientes_disponibles: List<String> = emptyList()
)

@Serializable
data class ListaCompraOutput(
    val lista_compra: String
)

// --- 2. ViewModel: Maneja la lógica y el estado de la UI ---

class MainViewModel : ViewModel() {

    // El estado de la UI: el texto a mostrar y si estamos cargando.
    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    // Creamos el cliente HTTP de Ktor. Se reutilizará en toda la app.
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
    }

    // Función para llamar al backend
    fun generarListaCompra(recetasStr: String, ingredientesStr: String) {
        // Ponemos la UI en estado de carga
        uiState = UiState.Loading

        // Lanzamos una corrutina para no bloquear el hilo principal de la UI
        viewModelScope.launch {
            try {
                // Convertimos los strings de entrada en listas, separando por comas y quitando espacios.
                val listaRecetas = recetasStr.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val listaIngredientes = ingredientesStr.split(',').map { it.trim() }.filter { it.isNotBlank() }

                val datosParaElBackend = RecetasInput(
                    nombres_recetas = listaRecetas,
                    ingredientes_disponibles = listaIngredientes
                )

                // Hacemos la petición POST a la IP de tu ordenador
                val response: ListaCompraOutput = client.post("https://asistentepersonalinteligente.onrender.com/generar-lista-compra/") {
                    contentType(ContentType.Application.Json)
                    setBody(datosParaElBackend)
                }.body()

                // Actualizamos la UI con la respuesta del servidor
                uiState = UiState.Success(response.lista_compra)

            } catch (e: Exception) {
                // Si algo falla, actualizamos la UI con el mensaje de error
                uiState = UiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    // Limpiar el cliente cuando el ViewModel se destruye
    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

// --- 3. Estados de la UI ---
// Una clase para representar los diferentes estados posibles de la pantalla

sealed class UiState {
    object Idle : UiState() // Estado inicial
    object Loading : UiState() // Cargando
    data class Success(val data: String) : UiState() // Éxito con datos
    data class Error(val message: String) : UiState() // Error con mensaje
}


// --- 4. Actividad Principal y UI con Jetpack Compose ---

class MainActivity : ComponentActivity() {
    // Obtenemos una instancia de nuestro ViewModel
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Pasamos el estado y la función de click al Composable principal
                    PantallaPrincipal(
                        uiState = viewModel.uiState,
                        onGenerateClick = { recetas, ingredientes ->
                            viewModel.generarListaCompra(recetas, ingredientes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaPrincipal(uiState: UiState, onGenerateClick: (String, String) -> Unit) {
    // Estados para guardar el texto de los campos de entrada
    var recetasInput by remember { mutableStateOf("") }
    var ingredientesInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mostramos el resultado (o el estado actual) en la parte superior
        when (uiState) {
            is UiState.Idle -> {
                Text(text = "Introduce tus recetas e ingredientes.")
            }
            is UiState.Loading -> {
                CircularProgressIndicator()
            }
            is UiState.Success -> {
                // Para mostrar la lista de la compra, que puede ser larga, la ponemos en un scroll
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(text = uiState.data)
                }
            }
            is UiState.Error -> {
                Text(text = "Error: ${uiState.message}", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Campos de texto para la entrada del usuario
        OutlinedTextField(
            value = recetasInput,
            onValueChange = { recetasInput = it },
            label = { Text("Recetas (separadas por comas)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ingredientesInput,
            onValueChange = { ingredientesInput = it },
            label = { Text("Ingredientes que ya tienes (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Botón para enviar los datos al ViewModel
        Button(
            onClick = { onGenerateClick(recetasInput, ingredientesInput) },
            enabled = uiState !is UiState.Loading
        ) {
            Text("Generar Lista")
        }
    }
}