package ru.zvonilka.prototype

/** Only exact-number community evidence. Unknown must never mean safe. */
object SpamVerdict {
    fun label(rating:String,votes:Int,archived:Boolean,whiteListed:Boolean):String? {
        if(archived || whiteListed || votes<4) return null
        return when(rating) {
            "B_MISSED", "C_PING" -> "Возможный спам"
            "D_POLL" -> "Возможный опрос"
            "E_ADVERTISING" -> "Возможная реклама"
            "F_GAMBLE" -> "Жалобы на розыгрыши"
            "G_FRAUD" -> "Жалобы на мошенничество"
            else -> null
        }
    }
}
