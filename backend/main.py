
from fastapi import FastAPI
from pydantic import BaseModel
import google.generativeai as genai
import os

# Configuración de la API key de Gemini
GEMINI_API_KEY = "AIzaSyCxLZnAJLty4yXKE59ztBm5-TptV2VHlGw"
genai.configure(api_key=GEMINI_API_KEY)

app = FastAPI(
    title="Asistente Personal Inteligente API",
    description="API para generar listas de la compra inteligentes usando un LLM.",
    version="0.1.0",
)

# Modelo de datos para las recetas que la app enviará al backend
class RecetasInput(BaseModel):
    nombres_recetas: list[str]
    ingredientes_disponibles: list[str] = []

# Modelo de datos para la respuesta que el backend enviará a la app
class ListaCompraOutput(BaseModel):
    lista_compra: str

@app.get("/")
def read_root():
    """Endpoint de bienvenida para verificar que el servidor está funcionando."""
    return {"mensaje": "Bienvenido a la API de Asistente Personal Inteligente"}

@app.post("/generar-lista-compra/", response_model=ListaCompraOutput)
async def generar_lista_compra(recetas_input: RecetasInput):
    """
    Endpoint principal que recibe recetas y devuelve una lista de la compra.
    (Actualmente es un placeholder y no llama a la IA)
    """
    
    # 1. Configurar el modelo de IA
    # La API key se configura al inicio de la aplicación
    model = genai.GenerativeModel('models/gemini-pro-latest')

    # 2. Crear el prompt para el modelo
    prompt = f"""
    Eres un asistente de cocina experto. Tu tarea es crear una lista de compras detallada.

    Basado en las siguientes recetas que el usuario quiere preparar:
    - {', '.join(recetas_input.nombres_recetas)}

    Y teniendo en cuenta que el usuario ya tiene los siguientes ingredientes en casa:
    - {', '.join(recetas_input.ingredientes_disponibles) if recetas_input.ingredientes_disponibles else "Ninguno"}

    Por favor, genera una lista de todos los ingredientes necesarios para preparar todas las recetas,
    excluyendo los que el usuario ya tiene. Agrupa los ingredientes por categoría (ej. Verduras, Carnes, Lácteos, Despensa)
    y especifica cantidades aproximadas si es posible. El formato de la respuesta debe ser claro y fácil de leer.
    No incluyas los nombres de las recetas en la lista final, solo los ingredientes a comprar.
    """

    # 3. Llamar a la API de Gemini
    try:
        response = model.generate_content(prompt)
        lista_generada = response.text
    except Exception as e:
        print(f"Error al contactar la API de Gemini: {e}")
        # Devolver un error 500 o un mensaje indicando el problema
        return {"lista_compra": "Error: No se pudo generar la lista de la compra. Verifica la configuración de la API."}


    # 4. Devolver la respuesta generada por la IA
    return {"lista_compra": lista_generada}

# Para ejecutar el servidor, usa el comando en tu terminal:
# uvicorn main:app --reload
