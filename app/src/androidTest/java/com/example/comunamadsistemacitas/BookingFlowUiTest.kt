package com.example.comunamadsistemacitas

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.comunamadsistemacitas.view.MarketplaceRemotoActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica la vista base del flujo de citas y que la navegación siga presente tras rotación. */
@RunWith(AndroidJUnit4::class)
class BookingFlowUiTest {
    @Test
    fun catalogoMantieneContextoDeReservaYBarraInferiorTrasRecreacion() {
        ActivityScenario.launch(MarketplaceRemotoActivity::class.java).use { scenario ->
            onView(withText(containsString("Elige dónde quieres reservar"))).check(matches(isDisplayed()))
            onView(withText(containsString("Mis citas"))).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText(containsString("Elige dónde quieres reservar"))).check(matches(isDisplayed()))
            onView(withText(containsString("Reservar"))).check(matches(isDisplayed()))
        }
    }
}
