package org.erbeenjoyers.drinkwater

import platform.Foundation.NSUserDefaults

actual class IosUserIdStorage : UserIdStorage {
    private val defaults = NSUserDefaults.standardUserDefaults()

    override fun save(userId: Int) {
        defaults.setInteger(userId.toLong(), KEY)
    }

    override fun load(): Int? {
        if (!defaults.objectForKey(KEY)) return null
        return defaults.integerForKey(KEY).toInt()
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY)
    }

    private companion object {
        const val KEY = "current_user_id"
    }
}

actual fun createUserIdStorage(): UserIdStorage = IosUserIdStorage()
