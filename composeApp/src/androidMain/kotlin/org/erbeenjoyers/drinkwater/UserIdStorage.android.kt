package org.erbeenjoyers.drinkwater

import android.content.Context

actual class AndroidUserIdStorage(private val context: Context) : UserIdStorage {
    private val prefs by lazy {
        context.getSharedPreferences("drinkwater_prefs", Context.MODE_PRIVATE)
    }

    override fun save(userId: Int) {
        prefs.edit().putInt(KEY, userId).apply()
    }

    override fun load(): Int? {
        val value = prefs.getInt(KEY, -1)
        return if (value == -1) null else value
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "current_user_id"
    }
}

private lateinit var appContext: Context

fun initAppContext(context: Context) {
    appContext = context
}

actual fun createUserIdStorage(): UserIdStorage = AndroidUserIdStorage(appContext)
