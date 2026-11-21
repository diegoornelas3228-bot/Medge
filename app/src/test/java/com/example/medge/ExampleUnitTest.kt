package com.example.medge // <-- deja el package que ya tenga tu archivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedgeUnitTest {

    private fun isValidHttpsUrl(url: String): Boolean {
        return url.trim().startsWith("https://") && url.trim().length > 8
    }

    private fun normalizeTag(rawTag: String): String {
        return rawTag.trim().lowercase().replaceFirstChar { it.uppercase() }
    }

    @Test
    fun validarUrlSegura_aceptaHttpsYRechazaHttp() {
        assertTrue(isValidHttpsUrl("https://images.unsplash.com/photo-123.jpg"))
        assertFalse(isValidHttpsUrl("http://inseguro.com/foto.jpg"))
        assertFalse(isValidHttpsUrl(""))
    }

    @Test
    fun normalizarEtiqueta_eliminaEspaciosYUnificaMayusculas() {
        assertEquals("Naturaleza", normalizeTag("  naturaleza  "))
        assertEquals("Naturaleza", normalizeTag("NATURALEZA"))
        assertEquals("Paisaje", normalizeTag(" paisaje"))
    }
}