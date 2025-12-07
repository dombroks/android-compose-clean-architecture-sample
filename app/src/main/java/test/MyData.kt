package test

import android.content.Context
import android.widget.Button

data class MyData(
    val id: Int,
    val name: String,
    val age: Int
)

interface A { fun show() = println("A") }
interface B : A { override fun show() = println("B") }
interface C : A { override fun show() = println("C") }

class D : B, C {

    val eslam = MyData(1, "Eslam", 25)

    override fun show() {
        super<B>.show() // Explicitly call B's implementation
        // or super<C>.show()
    }
}

// FILE: ApiConstants.kt
object ApiConstants {
    // GOOD: The compiler sees "BASE_URL" and replaces it with the actual string
    // everywhere in your code. 0 overhead at runtime.
    const val BASE_URL = "https://api.myapp.com/"

    // GOOD: A simple integer timeout
    const val TIMEOUT_MS = 5000L
}

// 1. OPEN: We use 'open' so we can inherit from it (Kotlin classes are final by default)
open class BaseButton(context: Context) : Button(context) {

    // --- PUBLIC (Default) ---
    // Anyone can call this from anywhere (MainActivity, other classes, etc.)
    var buttonLabel: String = "Default"

    // --- PRIVATE ---
    // Only 'BaseButton' can see this.
    // Even 'SubmitButton' (the child) cannot access this directly.
    private fun initializeHardware() {
        println("Initializing hardware drivers...")
    }

    // --- PROTECTED ---
    // 'BaseButton' and any child (like 'SubmitButton') can see this.
    // 'MainActivity' or 'TextButton' cannot see this.
    protected fun playClickSound() {
        // We can call private methods internally
        initializeHardware()
        println("Playing *click* sound")
    }

    // --- INTERNAL ---
    // Any class in this same Gradle module (app) can see this.
    // If you built this as a library for others to use, they wouldn't see this.
    internal fun validateTheme() {
        println("Checking theme consistency...")
    }
}

// A specific implementation (Child Class)
class SubmitButton(context: Context) : BaseButton(context) {

    init {
        setOnClickListener {
            // SUCCESS: We can call this because we inherit from BaseButton
            playClickSound()

            // ERROR: We CANNOT call this. It is private to BaseButton.
            // initializeHardware()
        }
    }
}

// An unrelated class (Not a child)
class TextButtonHelper(context: Context) {
    val submitButton: SubmitButton = SubmitButton(context)

    fun tryToAccessMethods() {
        // SUCCESS: Public is accessible everywhere
        submitButton.buttonLabel = "Submit"

        // SUCCESS: Internal is accessible because we are in the same module
        submitButton.validateTheme()

        // ERROR: Protected is NOT accessible here.
        // TextButtonHelper is not a child of BaseButton.
        // submitButton.playClickSound()

        // ERROR: Private is NOT accessible here.
        // submitButton.initializeHardware()
    }
}