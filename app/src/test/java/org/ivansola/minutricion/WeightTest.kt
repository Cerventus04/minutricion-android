package org.ivansola.minutricion

import org.ivansola.minutricion.ui.screens.fmtKg
import org.ivansola.minutricion.ui.screens.kgFilter
import org.ivansola.minutricion.ui.screens.parseKg
import org.ivansola.minutricion.ui.screens.roundKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTest {

    @Test
    fun seMuestranDosDecimales() {
        assertEquals("67.85", fmtKg(67.85))   // antes salía "67.9"
        assertEquals("68.05", fmtKg(68.05))
        assertEquals("67.9", fmtKg(67.9))
        assertEquals("68", fmtKg(68.0))
        assertEquals("0.2", fmtKg(0.2))
    }

    @Test
    fun seEscribeConComaOPuntoYDosDecimales() {
        assertEquals("67.85", kgFilter("67,85"))
        assertEquals("67.85", kgFilter("67.855"))   // un tercer decimal no entra
        assertEquals("67.85", kgFilter("67.8.5"))   // el segundo separador se ignora
        assertEquals("123", kgFilter("1234"))
        assertEquals("5", kgFilter(",5"))           // una coma inicial se descarta
    }

    @Test
    fun seGuardaElValorEscrito() {
        assertEquals(67.85, parseKg("67.85")!!, 0.0)
        assertEquals(67.85, parseKg("67,85")!!, 0.0)
        assertNull(parseKg(""))
        assertNull(parseKg("5"))       // fuera de rango
        // los pasos de 0,05 no dejan restos de coma flotante
        assertEquals(67.9, roundKg(67.85 + 0.05), 0.0)
        assertEquals(67.8, roundKg(67.85 - 0.05), 0.0)
    }
}
