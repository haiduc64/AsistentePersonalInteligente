import os
from dotenv import load_dotenv
import databases
import sqlalchemy
from fastapi import FastAPI
from pydantic import BaseModel
import google.generativeai as genai

# Cargar variables de entorno desde un archivo .env (para desarrollo local)
load_dotenv()

# --- Configuración de API y Base de Datos ---

# Lee la clave de API de Gemini desde las variables de entorno
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
genai.configure(api_key=GEMINI_API_KEY)

# Lee la URL de la base de datos desde las variables de entorno
# Render proporciona esta URL automáticamente
DATABASE_URL = os.getenv("DATABASE_URL")

# Crea la instancia de la base de datos y el motor de SQLAlchemy
database = databases.Database(DATABASE_URL)
metadata = sqlalchemy.MetaData()

# Define la tabla para guardar las listas de la compra
listas_compra = sqlalchemy.Table(
    "listas_compra",
    metadata,
    sqlalchemy.Column("id", sqlalchemy.Integer, primary_key=True),
    sqlalchemy.Column("recetas", sqlalchemy.String),
    sqlalchemy.Column("ingredientes_disponibles", sqlalchemy.String),
    sqlalchemy.Column("lista_generada", sqlalchemy.Text),
)

engine = sqlalchemy.create_engine(DATABASE_URL)
metadata.create_all(engine)

# --- Modelos de Datos Pydantic (para la API) ---

class RecetasInput(BaseModel):
    nombres_recetas: list[str]
    ingredientes_disponibles: list[str] = []

class ListaCompraOutput(BaseModel):
    lista_compra: str

# --- Aplicación FastAPI ---

app = FastAPI(
    title="Asistente Personal Inteligente API",
    description="API para generar y guardar listas de la compra inteligentes.",
    version="1.0.0",
)

# Eventos de inicio y fin de la aplicación
@app.on_event("startup")
async def startup():
    await database.connect()

@app.on_event("shutdown")
async def shutdown():
    await database.disconnect()

# --- Endpoints de la API ---

@app.get("/")
def read_root():
    return {"mensaje": "Bienvenido a la API de Asistente Personal Inteligente"}

@app.post("/generar-lista-compra/", response_model=ListaCompraOutput)
async def generar_lista_compra(recetas_input: RecetasInput):
    model = genai.GenerativeModel('models/gemini-pro-latest')

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

    try:
        response = model.generate_content(prompt)
        lista_generada = response.text

        # Guardar en la base de datos
        query = listas_compra.insert().values(
            recetas=", ".join(recetas_input.nombres_recetas),
            ingredientes_disponibles=", ".join(recetas_input.ingredientes_disponibles),
            lista_generada=lista_generada
        )
        await database.execute(query)

    except Exception as e:
        print(f"Error al contactar la API de Gemini o la base de datos: {e}")
        return {"lista_compra": "Error: No se pudo generar la lista de la compra."}

    return {"lista_compra": lista_generada}


class SugerenciaInput(BaseModel):
    ingredientes_disponibles: list[str] = []

class SugerenciaOutput(BaseModel):
    recetas_sugeridas: str

@app.post("/sugerir-receta/", response_model=SugerenciaOutput)
async def sugerir_receta(sugerencia_input: SugerenciaInput):
    model = genai.GenerativeModel('models/gemini-pro-latest')

    ingredientes_texto = ", ".join(sugerencia_input.ingredientes_disponibles) if sugerencia_input.ingredientes_disponibles else "ninguno"

    prompt = f"""
    Eres un asistente de cocina creativo.
    Basado en los siguientes ingredientes que el usuario ya tiene: {ingredientes_texto}.

    Sugiere una o dos recetas sencillas y deliciosas que se puedan preparar con esos ingredientes (se pueden añadir otros ingredientes comunes).
    Devuelve únicamente los nombres de las recetas, separados por comas. Por ejemplo: "Tortilla de patatas, Pollo al ajillo".
    No añadas explicaciones ni texto adicional, solo los nombres.
    """

    try:
        response = model.generate_content(prompt)
        return {"recetas_sugeridas": response.text}
    except Exception as e:
        print(f"Error al contactar la API de Gemini: {e}")
        return {"recetas_sugeridas": "Error al generar sugerencia."}


@app.get("/listas")
async def obtener_listas_guardadas():
    """Endpoint para ver todas las listas de la compra guardadas."""
    query = listas_compra.select()
    return await database.fetch_all(query)