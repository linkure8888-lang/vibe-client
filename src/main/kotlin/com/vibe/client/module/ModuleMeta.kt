package com.vibe.client.module

object ModuleMeta {

    data class Info(val category: Category, val description: String, val descriptionRu: String)

    fun of(module: Module): Info = table[module.name] ?: MISC_INFO
    fun of(name: String): Info = table[name] ?: MISC_INFO
    fun label(category: Category): String =
        when (category) {
            Category.COMBAT   -> "Combat"
            Category.MOVEMENT -> "Movement"
            Category.RENDER   -> "Render"
            Category.HUD      -> "HUD"
            Category.WORLD    -> "World"
            Category.MISC     -> "Misc"
        }

    private val MISC_INFO = Info(Category.MISC, "No description yet.", "Описание пока нет.")

    private val table: Map<String, Info> = mapOf(
        "FPS"               to Info(Category.HUD,      "Live frames per second.",                     "Кадров в секунду."),
        "Coordinates"       to Info(Category.HUD,      "Your XYZ position.",                          "Координаты XYZ."),
        "CPS"               to Info(Category.HUD,      "Clicks per second.",                          "Кликов в секунду."),
        "Health"            to Info(Category.HUD,      "Health and absorption.",                      "Здоровье и поглощение."),
        "Hunger"            to Info(Category.HUD,      "Food and saturation.",                        "Голод и насыщение."),
        "Ping"              to Info(Category.MISC,     "Server latency.",                             "Пинг до сервера."),
        "Memory"            to Info(Category.MISC,     "Java heap usage.",                            "Память Java."),
        "Clock"             to Info(Category.HUD,      "Real-world time.",                            "Реальное время."),
        "Direction"         to Info(Category.HUD,      "Compass direction and yaw.",                  "Направление и угол."),
        "Biome"             to Info(Category.WORLD,    "Current biome.",                              "Текущий биом."),
        "Speed"             to Info(Category.MOVEMENT, "Movement speed in blocks/sec.",               "Скорость движения."),
        "Velocity"          to Info(Category.MOVEMENT, "Raw XYZ velocity vector.",                    "Вектор скорости."),
        "Items"             to Info(Category.MISC,     "Arrows, gapples and pearls.",                 "Стрелы, яблоки, жемчуга."),
        "Server Address"    to Info(Category.WORLD,    "Connected server address.",                   "Адрес сервера."),
        "Reach"             to Info(Category.COMBAT,   "Distance to crosshair target.",               "Дистанция до цели."),
        "Combo"             to Info(Category.COMBAT,   "Consecutive hits.",                           "Серия ударов."),
        "Keystrokes"        to Info(Category.HUD,      "WASD and mouse key states.",                  "Состояние клавиш."),
        "Armor Status"      to Info(Category.HUD,      "Armor slot durabilities.",                    "Прочность брони."),
        "Potion Effects"    to Info(Category.HUD,      "Active effects and timers.",                  "Активные зелья."),
        "Toggle Sprint"     to Info(Category.MISC,     "Keeps sprinting automatically.",              "Авто-спринт."),
        "Toggle Sneak"      to Info(Category.MISC,     "Keeps sneaking automatically.",               "Авто-крадётся."),
        "Dimension"         to Info(Category.WORLD,    "Overworld, Nether or End.",                   "Измерение."),
        "Light Level"       to Info(Category.WORLD,    "Block and sky light at feet.",                "Уровень освещения."),
        "Chunk Pos"         to Info(Category.WORLD,    "Chunk coordinates.",                          "Координаты чанка."),
        "Nether Coords"     to Info(Category.WORLD,    "Portal-matched dimension coords.",            "Координаты для портала."),
        "XP"                to Info(Category.WORLD,    "Experience level and progress.",              "Уровень опыта."),
        "World Day"         to Info(Category.WORLD,    "Day counter and time of day.",                "День и время суток."),
        "Weather"           to Info(Category.WORLD,    "Rain and thunder.",                           "Дождь и гром."),
        "Game Time"         to Info(Category.WORLD,    "In-game clock.",                              "Игровые часы."),
        "Moon Phase"        to Info(Category.WORLD,    "Moon phase tonight.",                         "Фаза луны."),
        "Difficulty"        to Info(Category.WORLD,    "World difficulty.",                           "Сложность."),
        "Spawn Distance"    to Info(Category.WORLD,    "Distance to world spawn.",                    "Расстояние до спавна."),
        "World Border"      to Info(Category.WORLD,    "Distance to world border.",                   "Расстояние до границы."),
        "Server Brand"      to Info(Category.WORLD,    "Server software brand.",                      "Тип сервера."),
        "Slipperiness"      to Info(Category.WORLD,    "Block friction underfoot.",                   "Скользкость блока."),
        "On Solid Ground"   to Info(Category.WORLD,    "Standing on solid block.",                    "Твёрдый блок под ногами."),
        "Climbing"          to Info(Category.WORLD,    "On ladder or vine.",                          "Лезешь по лестнице."),
        "In Water"          to Info(Category.WORLD,    "Swimming or submerged.",                      "В воде."),
        "Portal Cooldown"   to Info(Category.WORLD,    "Ticks before portal reuse.",                  "Кулдаун портала."),
        "Can Sleep"         to Info(Category.WORLD,    "Can you sleep right now.",                    "Можно ли спать."),
        "Sprinting"         to Info(Category.WORLD,    "Sprint state.",                               "Спринт."),
        "Sneaking"          to Info(Category.WORLD,    "Crouch state.",                               "Крадётся."),
        "Regen"             to Info(Category.WORLD,    "Natural regen available.",                    "Доступна регенерация."),
        "Biome Temp"        to Info(Category.WORLD,    "Temperature of current biome.",               "Температура биома."),
        "Render Distance"   to Info(Category.WORLD,    "Render distance in chunks.",                  "Дистанция отрисовки."),
        "GUI Scale"         to Info(Category.MISC,     "Current GUI scale.",                          "Масштаб интерфейса."),
        "Brightness"        to Info(Category.MISC,     "Gamma / brightness setting.",                 "Яркость экрана."),
        "Window Focus"      to Info(Category.MISC,     "Whether the game window is focused.",         "Фокус окна."),
        "Volume"            to Info(Category.MISC,     "Master volume level.",                        "Громкость."),
        "FPS Graph"         to Info(Category.MISC,     "FPS history bar chart.",                      "График FPS."),
        "Ping Graph"        to Info(Category.MISC,     "Ping history bar chart.",                     "График пинга."),
        "Memory Graph"      to Info(Category.MISC,     "Heap usage bar chart.",                       "График памяти."),
        "FPS Stats"         to Info(Category.MISC,     "Min/avg/max FPS.",                            "Мин/ср/макс FPS."),
        "Ping Jitter"       to Info(Category.MISC,     "Ping stability.",                             "Стабильность пинга."),
        "Inventory"         to Info(Category.MISC,     "Free inventory slots.",                       "Свободные слоты."),
        "Entities"          to Info(Category.WORLD,    "Loaded entity count.",                        "Количество сущностей."),
        "Playtime"          to Info(Category.MISC,     "Time in this world session.",                 "Время в мире."),
        "Death Coords"      to Info(Category.MISC,     "Last death location.",                        "Место последней смерти."),
        "Durability"        to Info(Category.MISC,     "Low durability warning.",                     "Предупреждение о прочности."),
        "Strongest Effect"  to Info(Category.MISC,     "Highest amplifier active effect.",            "Самый мощный эффект."),
        "XP To Next"        to Info(Category.WORLD,    "XP needed for next level.",                   "Опыт до следующего уровня."),
        "Waypoint"          to Info(Category.MISC,     "Quick waypoint bearing and distance.",        "Метка: направление и дистанция."),
        "Fall Distance"     to Info(Category.MISC,     "Current fall height.",                        "Высота падения."),
        "Team HP"           to Info(Category.COMBAT,   "Scoreboard team HP.",                         "Здоровье команды."),
        "Totems"            to Info(Category.COMBAT,   "Totems of undying in inventory.",             "Тотемы бессмертия."),
        "TNT Timer"         to Info(Category.COMBAT,   "Nearest TNT fuse countdown.",                 "Фитиль ближайшего TNT."),
        "Mount"             to Info(Category.MISC,     "Mounted entity species and HP.",              "Информация о верховом."),
        "Spawn Check"       to Info(Category.WORLD,    "Can mobs spawn where you stand.",             "Может ли моб заспавниться."),
        "Target Info"       to Info(Category.COMBAT,   "Crosshair block or entity details.",          "Блок или сущность под прицелом."),
        "Target Block"      to Info(Category.COMBAT,   "Block name under crosshair.",                 "Блок под прицелом."),
        "Target Hardness"   to Info(Category.COMBAT,   "Mining hardness estimate.",                   "Твёрдость блока."),
        "Redstone"          to Info(Category.COMBAT,   "Redstone power of looked block.",             "Сила редстоуна."),
        "Look Angles"       to Info(Category.RENDER,   "Exact yaw and pitch.",                        "Рысканье и тангаж."),
        "Armor Points"      to Info(Category.HUD,      "Armor rating and toughness.",                 "Очки брони."),
        "Absorption"        to Info(Category.HUD,      "Absorption hearts.",                          "Поглощение."),
        "Air"               to Info(Category.HUD,      "Breath while underwater.",                    "Запас воздуха."),
        "Fire"              to Info(Category.HUD,      "Burning ticks remaining.",                    "Тиков горения."),
        "Held Item"         to Info(Category.HUD,      "Main hand item and durability.",              "Предмет в руке."),
        "Offhand"           to Info(Category.HUD,      "Off-hand item.",                              "Предмет во второй руке."),
        "Hotbar Durability" to Info(Category.HUD,      "Lowest durability in hotbar.",                "Наихудшая прочность хотбара."),
        "Enchants"          to Info(Category.HUD,      "Enchantments on held item.",                  "Зачарования предмета."),
        "Attack Charge"     to Info(Category.COMBAT,   "Attack cooldown charge %.",                   "Заряд атаки."),
        "Bow Charge"        to Info(Category.COMBAT,   "Bow draw charge %.",                          "Натяжение лука."),
        "Shield"            to Info(Category.COMBAT,   "Shield blocking indicator.",                  "Щит поднят."),
        "Frame Time"        to Info(Category.MISC,     "Average frame render time ms.",               "Время отрисовки кадра."),
        "Hits/min"          to Info(Category.COMBAT,   "Hit rate per minute.",                        "Ударов в минуту."),
        "Damage Taken"      to Info(Category.COMBAT,   "Damage received this session.",               "Урон за сессию."),
        "XP Gained"         to Info(Category.MISC,     "XP gained this session.",                     "Опыт за сессию."),
        "Mined/h"           to Info(Category.MISC,     "Blocks mined per hour.",                      "Блоков в час."),
        "Placed/h"          to Info(Category.MISC,     "Blocks placed per hour.",                     "Поставлено в час."),
        "Deaths"            to Info(Category.MISC,     "Total deaths this session.",                  "Смертей за сессию.")
    )
}
