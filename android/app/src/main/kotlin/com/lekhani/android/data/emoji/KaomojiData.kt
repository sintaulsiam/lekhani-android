package com.lekhani.android.data.emoji

data class KaomojiCategory(
    val name: String,
    val items: List<String>,
)

object KaomojiData {
    val categories: List<KaomojiCategory> = listOf(
        KaomojiCategory(
            name = "আনন্দ (Joy)",
            items = listOf(
                "(◕‿◕)", "(＾▽＾)", "(*^▽^*)", "(✿◠‿◠)", "(≧◡≦)",
                "\\(^Д^)/", "(◠‿◠)", "(o˘◡˘o)", "(´∀｀*)", "(^人^)"
            )
        ),
        KaomojiCategory(
            name = "অসহায় / উদাসীন (Shrug)",
            items = listOf(
                "¯\\_(ツ)_/¯", "¯\\(°_o)/¯", "╮(╯▽╰)╭", "╮(─▽─)╭",
                "┐('～`;)┌", "┐(￣∀￣)┌", "ヽ(ヅ)ノ"
            )
        ),
        KaomojiCategory(
            name = "ভালোবাসা (Love)",
            items = listOf(
                "(♡‿♡)", "(♥ω♥*)", "(づ￣ ³￣)づ", "(｡♥‿♥｡)",
                "(´• ω •`) ♡", "(人´∀｀)", "(/^-^(^ ^*)/ ♡", "(´♡‿♡`)"
            )
        ),
        KaomojiCategory(
            name = "রাগ (Anger)",
            items = listOf(
                "(ノಠ益ಠ)ノ彡┻━┻", "(凸ಠ益ಠ)凸", "(╯°□°)╯︵ ┻━┻",
                "凸( ` ﾛ ´ )凸", "(ง'̀-'́)ง", "(눈_눈)", "٩(ఠ益ఠ)۶"
            )
        ),
        KaomojiCategory(
            name = "কষ্ট ও কান্না (Sad)",
            items = listOf(
                "(╥﹏╥)", "(T_T)", "(；￣Д￣)", "(个_个)",
                "(╯_╰)", "(ノ_<。)", "(っ˘̩╭╮˘̩)っ", "(ಥ﹏ಥ)"
            )
        ),
        KaomojiCategory(
            name = "বিস্ময় (Surprise)",
            items = listOf(
                "(⊙_⊙)", "(°ロ°) !", "( ; ﾟДﾟ)", "(O_O;)",
                "(ﾟωﾟ;)", "( ; ﾛ)", "Σ(°△°|||)︴"
            )
        )
    )
}
