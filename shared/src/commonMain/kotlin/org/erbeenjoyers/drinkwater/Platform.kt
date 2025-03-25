package org.erbeenjoyers.drinkwater

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform