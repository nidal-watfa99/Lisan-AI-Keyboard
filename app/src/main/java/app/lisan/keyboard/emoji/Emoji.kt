package app.lisan.keyboard.emoji

object Emoji {
    val groups = linkedMapOf(
        "Faces / وجوه" to "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 🤪 🥳 😎 🤓 🧐 😏 😌 😔 😴 😮 😳 🥺 😢 😭 😱 😡 🤔 🤫 🫡 🫠",
        "Hands / أيدٍ" to "👍 👎 👋 ✋ 🤚 🖐️ 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 🙌 👏 🤝 🙏 💪 🫶 🤍 ❤️ ♥️ 💛 💚 💙 💜 🖤 💔",
        "Animals / حيوانات" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦉 🐝 🦋 🐢 🐍 🦎 🐙 🐬 🐳",
        "Food / طعام" to "🍏 🍎 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🍌 🥑 🍆 🥕 🌽 🥐 🍞 🥨 🧀 🍔 🍟 🍕 🌮 🍜 🍣 🍰 🍫 ☕ 🍵",
        "Nature / طبيعة" to "🌱 🌲 🌳 🌴 🌵 🍀 🍁 🍂 🍃 🍄 🌹 🌷 🌼 🌻 💐 🌊 ☀️ 🌙 ⭐ ✨ 🌈 ☁️ 🌧️ ⛈️ ❄️ ⛄ 🔥",
        "Travel / سفر" to "🚗 🚕 🚌 🚎 🚓 🚑 🚒 🚲 🛴 🚂 🚄 ✈️ 🚀 🛸 🚁 🚢 ⚓ 🗺️ 🗽 🗼 🏔️ 🏖️ 🏕️ 🏙️ 🏡 🕌",
        "Activities / أنشطة" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏓 🏸 🥊 🥋 🛹 🛼 🎯 🏆 🎮 🧩 🎭 🎨 🎬 🎹 🎸 🎤 🎧 🏊‍♀️",
        "Objects / أشياء" to "📱 💻 ⌨️ 🖥️ 🖨️ 📷 🎥 📺 ⏰ 🔋 💡 🔑 🧰 🛠️ 🧲 💎 🎁 🎈 📚 📝 ✏️ ✉️ 🗓️ 💼 🔒",
        "Symbols / رموز" to "✅ ❌ ❗ ❓ 💯 ♻️ ⚠️ 🔔 🔕 ©️ ®️ ™️ ➕ ➖ ✖️ ➗ ♾️ 🔁 ▶️ ⏸️ ⏹️ 🔴 🔵 🟢 ⬛ ⬜",
        "Flags / أعلام" to "🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🇸🇾 🇵🇸 🇱🇧 🇯🇴 🇮🇶 🇪🇬 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇲🇦 🇩🇿 🇹🇳 🇳🇱 🇩🇪 🇫🇷 🇹🇷 🇬🇧 🇺🇸 🇨🇦 🇪🇺",
        "People / أشخاص" to "👶 🧒 👦 👧 👨 👩 🧑 👴 👵 👩‍💻 👨‍⚕️ 🧑‍🚀 👩‍🎨 👨‍🍳 👩‍🦱 👨‍🦽 👩‍❤️‍👩 👨‍👩‍👧‍👦"
    ).mapValues { it.value.split(' ') }
    private val aliases = mapOf("heart love قلب حب" to "❤️ 🤍 💛 💚 💙 💜 💔", "smile happy ابتسامة سعيد" to "😀 😃 😄 😁 😊 🙂",
        "laugh ضحك" to "😂 🤣 😅", "sad cry حزين بكاء" to "😔 😢 😭 🥺", "dog كلب" to "🐶", "cat قط" to "🐱",
        "flag علم" to groups.values.elementAt(9).joinToString(" "), "fire نار" to "🔥", "flower ورد" to "🌹 🌷 💐 🌼")
    fun search(query: String) = groups.entries.filter { it.key.contains(query,true) }.flatMap { it.value }
        .plus(aliases.filterKeys { it.contains(query,true) }.values.flatMap { it.split(' ') })
        .plus(groups.values.flatten().filter { it.contains(query) }).distinct()
    fun variants(emoji: String): List<String> =
        if (emoji in groups.values.elementAt(1).take(23) && emoji !in listOf("🤝", "🤞").take(1))
            listOf(emoji) + listOf("🏻","🏼","🏽","🏾","🏿").map { emoji.replace("\uFE0F","") + it }
        else listOf(emoji)
}
