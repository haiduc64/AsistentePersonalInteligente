package com.example.asistentepersonalinteligente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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

@Serializable
data class SugerenciaInput(
    val ingredientes_disponibles: List<String> = emptyList()
)

@Serializable
data class SugerenciaOutput(
    val recetas_sugeridas: List<String> // Cambiado a Lista de Strings
)

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

    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000 // Timeout de 120 segundos
        }
    }

    fun sugerirRecetas(ingredientesStr: String) {
        uiState = UiState.LoadingSugerencias
        viewModelScope.launch {
            try {
                val listaIngredientes = ingredientesStr.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val datosParaElBackend = SugerenciaInput(ingredientes_disponibles = listaIngredientes)

                val response: SugerenciaOutput = client.post("https://asistentepersonalinteligente.onrender.com/sugerir-receta/") {
                    contentType(ContentType.Application.Json)
                    setBody(datosParaElBackend)
                }.body()

                uiState = UiState.SugerenciasSuccess(response.recetas_sugeridas)
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Error desconocido al sugerir recetas")
            }
        }
    }

    fun generarListaCompra(recetasStr: String, ingredientesStr: String) {
        val sugerenciasAnteriores = (uiState as? UiState.SugerenciasSuccess)?.sugerencias
            ?: (uiState as? UiState.LoadingLista)?.sugerenciasAnteriores
            ?: (uiState as? UiState.ListaSuccess)?.sugerencias
            ?: emptyList()

        uiState = UiState.LoadingLista(sugerenciasAnteriores)

        viewModelScope.launch {
            try {
                val listaRecetas = recetasStr.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val listaIngredientes = ingredientesStr.split(',').map { it.trim() }.filter { it.isNotBlank() }

                val datosParaElBackend = RecetasInput(
                    nombres_recetas = listaRecetas,
                    ingredientes_disponibles = listaIngredientes
                )

                val response: ListaCompraOutput = client.post("https://asistentepersonalinteligente.onrender.com/generar-lista-compra/") {
                    contentType(ContentType.Application.Json)
                    setBody(datosParaElBackend)
                }.body()

                uiState = UiState.ListaSuccess(sugerenciasAnteriores, response.lista_compra)
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Error desconocido al generar la lista")
            }
        }
    }

    fun resetUiState() {
        uiState = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

// --- 3. Estados de la UI ---
sealed class UiState {
    object Idle : UiState()
    object LoadingSugerencias : UiState()
    data class SugerenciasSuccess(val sugerencias: List<String>) : UiState() // Cambiado a Lista
    data class LoadingLista(val sugerenciasAnteriores: List<String>) : UiState() // Cambiado a Lista
    data class ListaSuccess(val sugerencias: List<String>, val listaCompra: String) : UiState() // Cambiado a Lista
    data class Error(val message: String) : UiState()
}

// --- 4. Actividad Principal y UI con Jetpack Compose ---

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PantallaPrincipal(
                        uiState = viewModel.uiState,
                        onSuggestClick = { ingredientes -> viewModel.sugerirRecetas(ingredientes) },
                        onGenerateClick = { recetas, ingredientes -> viewModel.generarListaCompra(recetas, ingredientes) },
                        onResetClick = { viewModel.resetUiState() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // Anotación para FlowRow
@Composable
fun PantallaPrincipal(
    uiState: UiState,
    onSuggestClick: (String) -> Unit,
    onGenerateClick: (String, String) -> Unit,
    onResetClick: () -> Unit
) {
    var ingredientesInput by remember { mutableStateOf("") }
    var recetasInput by remember { mutableStateOf("") }

    val isLoading = uiState is UiState.LoadingSugerencias || uiState is UiState.LoadingLista

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Asistente de Compra Inteligente", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = ingredientesInput,
            onValueChange = { ingredientesInput = it },
            label = { Text("Ingredientes que ya tienes (separados por comas)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onSuggestClick(ingredientesInput) },
            enabled = !isLoading
        ) {
            Text("1. Sugerir Recetas")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // --- Área de Resultados ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is UiState.Idle -> {
                    Text("Introduce tus ingredientes para empezar.", textAlign = TextAlign.Center)
                }
                is UiState.LoadingSugerencias -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text("Buscando recetas...", modifier = Modifier.padding(top = 64.dp))
                }
                is UiState.SugerenciasSuccess -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Recetas Sugeridas:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Usamos FlowRow para que los botones se ajusten y pasen a la siguiente línea si no caben
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.sugerencias.forEach { receta ->
                                TextButton(onClick = { recetasInput = receta }) {
                                    Text(receta)
                                }
                            }
                        }
                    }
                }
                is UiState.LoadingLista -> {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Recetas Sugeridas:", style = MaterialTheme.typography.titleMedium)
                        Text(uiState.sugerenciasAnteriores.joinToString(", "))
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Generando lista de la compra...", modifier = Modifier.padding(top = 64.dp))
                    }
                }
                is UiState.ListaSuccess -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Recetas Sugeridas:", style = MaterialTheme.typography.titleMedium)
                            Text(uiState.sugerencias.joinToString(", "))
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Lista de la Compra:", style = MaterialTheme.typography.titleMedium)
                            Text(uiState.listaCompra)
                        }
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "¡Ha Ocurrido un Error!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detalles del error:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.message)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = recetasInput,
            onValueChange = { recetasInput = it },
            label = { Text("Escribe la receta elegida") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onGenerateClick(recetasInput, ingredientesInput) },
            enabled = !isLoading && recetasInput.isNotBlank()
        ) {
            Text("2. Generar Lista de la Compra")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onResetClick,
            enabled = !isLoading && uiState !is UiState.Idle
        ) {
            Text("Empezar de Nuevo")
        }
    }
}
