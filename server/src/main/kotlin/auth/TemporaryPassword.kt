package auth

import java.security.SecureRandom

/** Easy to read out or type: no 0/o, 1/l/i, grouped like `abcd-efgh-jkmn`. */
fun temporaryPassword(random: SecureRandom = SecureRandom()): String {
    val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
    return (1..3).joinToString("-") { (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
}
