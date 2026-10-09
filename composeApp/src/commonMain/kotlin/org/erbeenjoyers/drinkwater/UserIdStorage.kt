package org.erbeenjoyers.drinkwater

/**
 * Enkelt lagringsgränssnitt för att spara den inloggade användarens id
 * mellan omstarter. Implementeras plattformsspecifikt (SharedPreferences på
 * Android, NSUserDefaults på iOS).
 */
interface UserIdStorage {
    fun save(userId: Int)
    fun load(): Int?
    fun clear()
}

expect fun createUserIdStorage(): UserIdStorage
