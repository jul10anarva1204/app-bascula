package com.example.basculaserial.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DB_NAME    = "bascula.db"
private const val DB_VERSION = 4          // v4: agrega codigo_barras
private const val TABLE      = "registros_peso"
private const val COL_ID        = "id"
private const val COL_PESO      = "peso"        // peso neto (columna original)
private const val COL_UNIDAD    = "unidad"
private const val COL_TS        = "timestamp"
private const val COL_FOTO      = "foto_ruta"
private const val COL_BRUTO     = "peso_bruto"  // nuevo v3
private const val COL_TARA      = "tara"        // nuevo v3
private const val COL_NETO      = "peso_neto"   // nuevo v3
private const val COL_CODIGO    = "codigo_barras"  // nuevo v4

/**
 * Base de datos SQLite local.
 * v2: columna foto_ruta.
 * v3: columnas peso_bruto, tara, peso_neto para desglose de tara.
 * v4: columna codigo_barras.
 */
class BasculaDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val _registros = MutableStateFlow<List<RegistroPeso>>(emptyList())
    val registros: StateFlow<List<RegistroPeso>> = _registros.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PESO    TEXT    NOT NULL,
                $COL_UNIDAD  TEXT    NOT NULL,
                $COL_TS      INTEGER NOT NULL,
                $COL_FOTO    TEXT,
                $COL_BRUTO   TEXT,
                $COL_TARA    TEXT,
                $COL_NETO    TEXT,
                $COL_CODIGO  TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_FOTO TEXT") } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_BRUTO TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_TARA  TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_NETO  TEXT") } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_CODIGO TEXT") } catch (_: Exception) {}
        }
    }

    /**
     * Inserta un registro con desglose de tara.
     * @param pesoNeto   Peso neto (bruto − tara). Si no hay tara = peso bruto.
     * @param unidad     Unidad de medida (kg, lb…)
     * @param pesoBruto  Peso bruto (null si no hay tara activa)
     * @param tara       Valor de tara (null si no hay tara activa)
     * @param fotoRuta   Ruta local de la foto (null si no se capturó)
     * @param codigoBarras Código de barras opcional
     */
    fun insertar(
        pesoNeto: String,
        unidad: String,
        pesoBruto: String? = null,
        tara: String? = null,
        fotoRuta: String? = null,
        codigoBarras: String? = null
    ): Long {
        val cv = ContentValues().apply {
            put(COL_PESO,   pesoNeto)   // columna original = neto
            put(COL_UNIDAD, unidad)
            put(COL_TS,     System.currentTimeMillis())
            if (fotoRuta     != null) put(COL_FOTO,   fotoRuta)    else putNull(COL_FOTO)
            if (pesoBruto    != null) put(COL_BRUTO,  pesoBruto)   else putNull(COL_BRUTO)
            if (tara         != null) put(COL_TARA,   tara)        else putNull(COL_TARA)
            if (pesoNeto     != null) put(COL_NETO,   pesoNeto)    else putNull(COL_NETO)
            if (codigoBarras != null) put(COL_CODIGO, codigoBarras) else putNull(COL_CODIGO)
        }
        val id = writableDatabase.insert(TABLE, null, cv)
        refrescar()
        return id
    }

    /** Elimina un registro por ID (y borra su foto si existe) */
    fun eliminarPorId(id: Long) {
        readableDatabase.query(
            TABLE, arrayOf(COL_FOTO), "$COL_ID = ?", arrayOf(id.toString()), null, null, null
        ).use { c ->
            if (c.moveToFirst()) {
                val ruta = c.getString(c.getColumnIndexOrThrow(COL_FOTO))
                if (ruta != null) java.io.File(ruta).delete()
            }
        }
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        refrescar()
    }

    /** Elimina todos los registros (y sus fotos) */
    fun eliminarTodos() {
        readableDatabase.query(TABLE, arrayOf(COL_FOTO), null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val ruta = c.getString(c.getColumnIndexOrThrow(COL_FOTO))
                if (ruta != null) java.io.File(ruta).delete()
            }
        }
        writableDatabase.delete(TABLE, null, null)
        refrescar()
    }

    /** Lee todos los registros y actualiza el StateFlow */
    fun refrescar() {
        val lista = mutableListOf<RegistroPeso>()
        readableDatabase.query(TABLE, null, null, null, null, null, "$COL_TS DESC").use { c ->
            while (c.moveToNext()) {
                fun str(col: String) = try { c.getString(c.getColumnIndexOrThrow(col)) } catch (_: Exception) { null }
                lista.add(
                    RegistroPeso(
                        id           = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                        peso         = c.getString(c.getColumnIndexOrThrow(COL_PESO)),
                        unidad       = c.getString(c.getColumnIndexOrThrow(COL_UNIDAD)),
                        timestamp    = c.getLong(c.getColumnIndexOrThrow(COL_TS)),
                        fotoRuta     = str(COL_FOTO),
                        pesoBruto    = str(COL_BRUTO),
                        tara         = str(COL_TARA),
                        pesoNeto     = str(COL_NETO),
                        codigoBarras = str(COL_CODIGO)
                    )
                )
            }
        }
        _registros.value = lista
    }

    companion object {
        @Volatile private var INSTANCE: BasculaDatabase? = null

        fun getInstance(context: Context): BasculaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BasculaDatabase(context).also {
                    INSTANCE = it
                    it.refrescar()
                }
            }
    }
}
