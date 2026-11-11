package com.example.comunamadsistemacitas

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.comunamadsistemacitas.view.AccesoRemotoActivity
import com.example.comunamadsistemacitas.view.PortalMarketplaceActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas instrumentadas de navegación visible, recreación y validación de acceso. */
@RunWith(AndroidJUnit4::class)
class PortalAndLoginUiTest {
    @Test
    fun portalConservaAccionesPrincipalesTrasRecreacion() {
        ActivityScenario.launch(PortalMarketplaceActivity::class.java).use { scenario ->
            onView(withText(containsString("Reservar una cita"))).check(matches(isDisplayed()))
            onView(withText("Consulta tus citas")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText(containsString("Reservar una cita"))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun accesoValidaCorreoAntesDeIntentarConectar() {
        ActivityScenario.launch(AccesoRemotoActivity::class.java).use {
            onView(withId(R.id.login_email)).perform(typeText("correo-invalido"), closeSoftKeyboard())
            onView(withId(R.id.login_submit)).perform(click())
            onView(withId(R.id.login_email)).check(matches(hasErrorText("Escribe un correo válido")))
        }
    }
    @Test
    fun portalMuestraLosCuatroDestinosDeNavegacion() {
        ActivityScenario.launch(PortalMarketplaceActivity::class.java).use {
            onView(withText(containsString("Inicio"))).check(matches(isDisplayed()))
            onView(withText(containsString("Reservar"))).check(matches(isDisplayed()))
            onView(withText(containsString("Mis citas"))).check(matches(isDisplayed()))
            onView(withText(containsString("Negocio"))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun accesoConservaLosCamposAlRecrearLaActivity() {
        ActivityScenario.launch(AccesoRemotoActivity::class.java).use { scenario ->
            onView(withId(R.id.login_email)).perform(typeText("ana@unamad.edu.pe"))
            onView(withId(R.id.login_password)).perform(typeText("ClaveSegura123"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.login_email)).check(matches(withText("ana@unamad.edu.pe")))
            onView(withId(R.id.login_password)).check(matches(withText("ClaveSegura123")))
        }
    }

}
