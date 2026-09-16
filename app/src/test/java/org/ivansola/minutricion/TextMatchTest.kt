package org.ivansola.minutricion

import org.ivansola.minutricion.ui.components.TextMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    private fun has(name: String, key: String) = TextMatch.contains(TextMatch.normalize(name), key)

    @Test
    fun casaPalabrasYPlurales() {
        assertTrue(has("Salmón ahumado", "salmón"))
        assertTrue(has("Lomos de SALMONES", "salmón"))      // plural + sin tilde + mayúsculas
        assertTrue(has("Nueces peladas", "nuez"))           // z -> ces
        assertTrue(has("Tallarines frescos", "tallarín"))   // el plural pierde la tilde
        assertTrue(has("Yogures naturales", "yogur"))
        assertTrue(has("Macarrones gratinados", "macarr*")) // raíz
        assertTrue(has("Tomate frito casero", "tomate frito"))
        assertTrue(has("Maíz dulce", "maiz"))               // tilde indistinta en la clave
    }

    @Test
    fun noCasaDentroDeOtraPalabra() {
        assertFalse(has("Prenatal Care", "nata"))
        assertFalse(has("Defatted Cocoa Powder", "coco"))
        assertFalse(has("Salami palaciego", "sal"))
        assertFalse(has("Maca complex especial mujer", "especia"))
        assertFalse(has("Burratinas (Central Lechera)", "leche"))
        assertFalse(has("Chocolate negro 85%", "cola"))
        assertFalse(has("Coliflor congelada", "col"))
        assertFalse(has("Aguacate Hass", "agua"))
        assertFalse(has("Hamburguesa de ternera", "burn"))
    }
}
