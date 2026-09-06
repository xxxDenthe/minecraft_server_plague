package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Баланс классов в `config/lmpc_classes-common.toml`. Тот же приём,
 * что в `plaguecore`: числа, которые придётся крутить по ощущениям,
 * живут в файле, а не в коде.
 *
 * Пути ключей нарочно плоские, без секций {@code push/pop}: у владельца
 * уже лежит настроенный файл, а смена пути ключа молча сбросила бы
 * значение к умолчанию.
 *
 * **Тип COMMON, а не SERVER.** Значит, клиент читает свой файл, а не
 * серверный: гримуар покажет чужой порог тира, если у игрока пак другой
 * версии. Мирюсь сознательно — пак раздаётся лаунчером одним куском,
 * а SERVER-конфиг переехал бы в `world/serverconfig/` и сломал бы уже
 * настроенный файл владельца.
 */
public final class ClassesConfig {
    private ClassesConfig() {}

    private static final ModConfigSpec.Builder СТРОИТЕЛЬ = new ModConfigSpec.Builder();

    // ── смена класса ──────────────────────────────────────────────────

    private static final ModConfigSpec.IntValue КУЛДАУН_СМЕНЫ_КЛАССА = СТРОИТЕЛЬ
        .comment("Минимальный перерыв между сменами класса, в минутах.")
        .defineInRange("classSwitchCooldownMinutes", 30, 0, 1440);

    private static final ModConfigSpec.DoubleValue ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ = СТРОИТЕЛЬ
        .comment("Какая доля мастерства старого класса остаётся при смене.",
                 "0.3 — треть сохраняется, остальное срезается.")
        .defineInRange("masteryKeepFraction", 0.3, 0.0, 1.0);

    // ── мастерство и тиры, общие для всех классов (спек 2.1 и 11) ─────

    private static final ModConfigSpec.IntValue ПОРОГ_ТИРА_2 = СТРОИТЕЛЬ
        .comment("Мастерство, с которого начинается второй тир класса (из 100).")
        .defineInRange("masteryTier2At", 25, 1, ClassMastery.МАКСИМУМ);

    private static final ModConfigSpec.IntValue ПОРОГ_ТИРА_3 = СТРОИТЕЛЬ
        .comment("Мастерство, с которого начинается третий тир класса (из 100).")
        .defineInRange("masteryTier3At", 60, 1, ClassMastery.МАКСИМУМ);

    private static final ModConfigSpec.DoubleValue СИЛА_ЗА_ТИР = СТРОИТЕЛЬ
        .comment("Насколько сильнее пассивка за каждый тир выше первого.",
                 "0.15 — второй тир +15%, третий +30%. Общий множитель для всех классов.")
        .defineInRange("masteryPowerPerTier", 0.15, 0.0, 2.0);

    private static final ModConfigSpec.DoubleValue КУЛДАУН_ЗА_ТИР = СТРОИТЕЛЬ
        .comment("Насколько короче кулдаун активок за каждый тир выше первого.",
                 "0.2 — второй тир −20%, третий −40%. Ниже 10% от исходного не опускается.")
        .defineInRange("masteryCooldownCutPerTier", 0.2, 0.0, 0.9);

    // ── Клирик (спек, раздел 4) ───────────────────────────────────────

    private static final ModConfigSpec.DoubleValue КУЛОН_БАЗОВАЯ_ЗАЩИТА = СТРОИТЕЛЬ
        .comment("Доп. защита от кулона Клирика (0..1), складывается с бронёй в plaguecore.",
                 "Полная — только у Клирика, у остальных классов — доля clericPendantOtherClassFraction.",
                 "У Клирика растёт с тиром мастерства, но выше 0.9 не поднимается.",
                 "0.25 с 2026-09-06, было 0.15: в plaguecore 0.2.0 очко брони стало гасить",
                 "два процента вместо одного, полный алмаз вырос до 0.40, и кулон на его",
                 "фоне перестал быть заметной вещью класса. Решение владельца.")
        .defineInRange("clericPendantProtection", 0.25, 0.0, 0.9);

    private static final ModConfigSpec.DoubleValue КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ = СТРОИТЕЛЬ
        .comment("Доля clericPendantProtection, которую кулон даёт не-Клирику.")
        .defineInRange("clericPendantOtherClassFraction", 0.5, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ОТВАР_КУЛДАУН = СТРОИТЕЛЬ
        .comment("Кулдаун улучшенного отвара Клирика на одного игрока, в минутах.",
                 "Сокращается тиром мастерства (masteryCooldownCutPerTier).")
        .defineInRange("clericBrewCooldownMinutes", 10, 0, 120);

    private static final ModConfigSpec.DoubleValue ОТВАР_ЛЕЧЕНИЕ = СТРОИТЕЛЬ
        .comment("Сколько очков заражённости снимает улучшенный отвар за раз.",
                 "Растёт с тиром мастерства (masteryPowerPerTier).")
        .defineInRange("clericBrewCureAmount", 40.0, 0.0, 100.0);

    private static final ModConfigSpec.IntValue ОТВАР_ИММУНИТЕТ = СТРОИТЕЛЬ
        .comment("Иммунитет от улучшенного отвара, в минутах.")
        .defineInRange("clericBrewImmunityMinutes", 5, 0, 60);

    private static final ModConfigSpec.IntValue СКОРМИТЬ_ДЛИТЕЛЬНОСТЬ = СТРОИТЕЛЬ
        .comment("Сколько тиков держать ПКМ на союзнике, чтобы напоить его отваром. 60 — три секунды.")
        .defineInRange("clericFeedChannelTicks", 60, 10, 600);

    private static final ModConfigSpec.DoubleValue СКОРМИТЬ_ДИСТАНЦИЯ = СТРОИТЕЛЬ
        .comment("Максимальное расстояние до союзника, чтобы его напоить, в блоках.",
                 "Не магия — Клирик должен стоять вплотную. 1.5 — чуть больше одного блока.")
        .defineInRange("clericFeedMaxDistance", 1.5, 0.5, 4.0);

    private static final ModConfigSpec.IntValue КЛИРИК_ИНТЕРВАЛ_РЕГЕНЕРАЦИИ = СТРОИТЕЛЬ
        .comment("Раз во сколько тиков Клирику само добавляется полсердца. Делится на тир:",
                 "600 — раз в 30 секунд на первом тире, раз в 15 на втором, раз в 10 на третьем.",
                 "Работает и на голодный желудок — в этом весь смысл при чуме,",
                 "которая режет сытость. 0 — выключить.")
        .defineInRange("clericRegenIntervalTicks", 600, 0, 24000);

    private static final ModConfigSpec.IntValue КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ = СТРОИТЕЛЬ
        .comment("Мастерство Клирика за одно удачное лечение отваром (из 100).",
                 "Лечение союзника даёт вдвое больше, чем лечение себя.")
        .defineInRange("clericMasteryPerCure", 4, 0, 100);

    // ── Кузнец (спек, раздел 5) ───────────────────────────────────────

    private static final ModConfigSpec.IntValue КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА = СТРОИТЕЛЬ
        .comment("Раз во сколько тиков у Кузнеца само чинится одно очко прочности.",
                 "100 — раз в пять секунд, чинится самая побитая вещь в руках или на теле.",
                 "Стоит вместо бонуса на машинах Create: Create — зависимость пака, а не мода,",
                 "и дотянуться до неё из lmpc_classes нечем. С тиром интервал короче.",
                 "0 — выключить пассивный ремонт совсем.")
        .defineInRange("smithRepairIntervalTicks", 100, 0, 12000);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_МАСТЕРСТВО_ЗА_КРАФТ = СТРОИТЕЛЬ
        .comment("Мастерство Кузнеца за один скованный предмет снаряжения (из 100).",
                 "Считается всё, что имеет прочность: оружие, инструмент, броня, щит.")
        .defineInRange("smithMasteryPerCraft", 2, 0, 100);

    private static final ModConfigSpec.DoubleValue КУЗНЕЦ_ШАНС_ЗАЧАРОВАНИЯ = СТРОИТЕЛЬ
        .comment("Шанс на тир, что скованная Кузнецом вещь выйдет уже зачарованной.",
                 "Итог = число * тир: 0.08 даёт 8 % на первом тире, 16 % на втором, 24 % на третьем.",
                 "Умножаем на тир, а не на masteryPowerPerTier: тот даёт +15 % и на глаз незаметен.")
        .defineInRange("smithEnchantChancePerTier", 0.08, 0.0, 1.0);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_СИЛА_ЗАЧАРОВАНИЯ = СТРОИТЕЛЬ
        .comment("Сила случайного зачарования на тир, в «уровнях стола».",
                 "Итог = число * тир: 5 — как стол на 5, 10 и 15 уровней.",
                 "Сокровищ и проклятий нет: берём только тег in_enchanting_table.")
        .defineInRange("smithEnchantPowerPerTier", 5, 1, 30);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_ОПЫТ_ЗА_КРАФТ = СТРОИТЕЛЬ
        .comment("Опыт Кузнецу за скованное снаряжение, на тир выше первого.",
                 "Итог = число * (тир − 1): на первом тире ноль, дальше 1 и 2 очка.")
        .defineInRange("smithCraftXpPerTier", 1, 0, 100);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_ОПЫТ_ЗА_ПЛАВКУ = СТРОИТЕЛЬ
        .comment("Доп. опыт Кузнецу за плавку, на тир выше первого и на очко мастерства.",
                 "Итог = число * (тир − 1) * (выплавлено / smithSmeltsPerMastery).",
                 "Считается той же порцией, что и мастерство, поэтому вынос по одному",
                 "не даёт ни опыта, ни мастерства — печь не становится фермой опыта.")
        .defineInRange("smithSmeltBonusXpPerTier", 2, 0, 100);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_ПЛАВОК_НА_ОЧКО = СТРОИТЕЛЬ
        .comment("Сколько предметов надо выплавить в печи на одно очко мастерства.",
                 "Считается за один вынос из печи, с округлением вниз: вынести стопку",
                 "из 64 при значении 8 — восемь очков, вынести один слиток — ноль.",
                 "Округление вниз и есть защита от накрутки поштучным выносом.")
        .defineInRange("smithSmeltsPerMastery", 8, 1, 256);

    private static final ModConfigSpec.DoubleValue ОЧИСТИТЕЛЬ_СИЛА = СТРОИТЕЛЬ
        .comment("Вероятность снять один уровень заражения за одну попытку, для партии без Кузнеца.",
                 "Это cleansePower из спека ядра 10.1. С Кузнецом растёт по тиру",
                 "его мастерства (masteryPowerPerTier).",
                 "Попыток за ночь — purifierLevelsPerNight, поэтому за ночь снимается",
                 "в среднем purifierCleansePower * purifierLevelsPerNight уровней.",
                 "0.85, а не спековые 0.30: ночное распространение поднимает каждый",
                 "заражённый чанк на +1 (фазы 3+ — каждую ночь), и при 0.30 × 1 попытке",
                 "очиститель ровно отыгрывал этот рост назад — уровень застывал на месте.",
                 "Разбор: docs/superpowers/notes/2026-09-06-ochistitel-oblast-i-skorost.md")
        .defineInRange("purifierCleansePower", 0.85, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ОЧИСТИТЕЛЬ_ПОПЫТОК = СТРОИТЕЛЬ
        .comment("Сколько уровней очиститель пытается снять с каждого чанка за ночь.",
                 "Каждая попытка — отдельный бросок на purifierCleansePower.",
                 "4 попытки по 0.85 дают в среднем 3.4 уровня за ночь; ночной рост",
                 "съедает один, поэтому область светлеет на два-три деления за сутки.",
                 "1 — поведение до 0.8.0: одна попытка за ночь.")
        .defineInRange("purifierLevelsPerNight", 4, 1, 16);

    private static final ModConfigSpec.IntValue ОЧИСТИТЕЛЬ_РАДИУС = СТРОИТЕЛЬ
        .comment("Радиус работы очистителя в чанках вокруг своего.",
                 "0 — только свой чанк, 16 × 16 блоков (поведение до 0.8.0).",
                 "1 — квадрат 3 × 3 чанка, 48 × 48 блоков вокруг блока.",
                 "Радиус важен не только ради площади: вид чанка берётся по максимуму",
                 "соседей, поэтому одиночный вычищенный чанк посреди гнили так и",
                 "выглядит гнилым. Плюс правило 10.2 («нельзя окопаться») держит",
                 "на единице любой чанк, у которого сосед на 3 и выше — при радиусе 0",
                 "очиститель не мог опустить свой чанк ниже единицы вообще никогда.")
        .defineInRange("purifierRadiusChunks", 1, 0, 8);

    private static final ModConfigSpec.DoubleValue ОЧИСТИТЕЛЬ_СОПРОТИВЛЕНИЕ = СТРОИТЕЛЬ
        .comment("На сколько очиститель поднимает сопротивление чанка за ночь (0..1).",
                 "0.25 — число из спека ядра 10.1. Сопротивление само тает без работы.")
        .defineInRange("purifierResistanceGain", 0.25, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue ОЧИСТИТЕЛЬ_МИН_СКОРОСТЬ = СТРОИТЕЛЬ
        .comment("Минимальная скорость вращения соседнего механизма Create, об/мин.",
                 "8 — то, что даёт одно водяное колесо через вал: спек требует,",
                 "чтобы первый тир собирался без понимания передач и стресса.",
                 "0 — очиститель работает вообще без питания (для проверки).")
        .defineInRange("purifierMinSpeed", 8.0, 0.0, 256.0);

    private static final ModConfigSpec.DoubleValue ОЧИСТИТЕЛЬ_ПРИБАВКА_ЗА_СКОРОСТЬ = СТРОИТЕЛЬ
        .comment("Насколько сильнее очиститель чистит на полном разгоне, долей от базы.",
                 "0.5 — на purifierSpeedForMax оборотах сила в полтора раза выше, чем на пороге.",
                 "До 0.12.0 скорость была только порогом: вал на 8 и на 256 об/мин работали",
                 "одинаково, и передачи Create в базе стояли для красоты. Заодно это",
                 "частичная замена непотреблённому стрессу: разгон в Create сам по себе",
                 "режет запас сети, поэтому быстрый очиститель обходится дороже.",
                 "0 — вернуть поведение до 0.12.0.")
        .defineInRange("purifierSpeedBonus", 0.5, 0.0, 4.0);

    private static final ModConfigSpec.DoubleValue ОЧИСТИТЕЛЬ_СКОРОСТЬ_ПОЛНАЯ = СТРОИТЕЛЬ
        .comment("Скорость вращения, на которой прибавка purifierSpeedBonus выходит целиком.",
                 "128 об/мин — вдвое ниже потолка Create: разогнать очиститель до предела",
                 "не должно быть обязательным, это должно быть решением.")
        .defineInRange("purifierSpeedForMax", 128.0, 1.0, 256.0);

    private static final ModConfigSpec.IntValue ЛАТУННЫЙ_РАДИУС = СТРОИТЕЛЬ
        .comment("Радиус латунного очистителя в чанках вокруг своего.",
                 "3 — квадрат 7 × 7 чанков, 112 × 112 блоков.",
                 "Спек ядра 10.1 пишет андезитовому 1 чанк, а латунному 3 × 3, то есть",
                 "втрое шире по стороне. У нас андезитовый уже работает по 3 × 3 (радиус 1,",
                 "правка 0.8.0), поэтому втрое по стороне — это 9 × 9, слишком много",
                 "на один блок. 7 × 7 — компромисс: заметно шире андезитового,",
                 "но город всё ещё не закрывается двумя штуками.")
        .defineInRange("brassPurifierRadiusChunks", 3, 0, 12);

    private static final ModConfigSpec.IntValue ЛАТУННЫЙ_РАСХОД = СТРОИТЕЛЬ
        .comment("Сколько реагента латунный очиститель тратит за ночь.",
                 "4 — число из спека ядра 10.1. Андезитовый тратит один.",
                 "Если внутри меньше, тратит сколько есть и всё равно работает:",
                 "иначе половина заряда лежала бы мёртвым грузом.")
        .defineInRange("brassPurifierReagentPerNight", 4, 1, 64);

    private static final ModConfigSpec.DoubleValue ЛАТУННЫЙ_МИН_СКОРОСТЬ = СТРОИТЕЛЬ
        .comment("Минимальная скорость вращения для латунного очистителя, об/мин.",
                 "32 против восьми у андезитового: одного водяного колеса напрямую",
                 "уже не хватает, нужны передачи или источник побыстрее. Это наша",
                 "замена спековым 256 SU по части ПОРОГА: сам стресс теперь",
                 "потребляется по-настоящему, см. purifierStressImpact.")
        .defineInRange("brassPurifierMinSpeed", 32.0, 0.0, 256.0);

    private static final ModConfigSpec.DoubleValue СТРЕСС_АНДЕЗИТОВЫЙ = СТРОИТЕЛЬ
        .comment("Нагрузка андезитового очистителя на сеть Create (impact).",
                 "8 — столько же, сколько у механического пресса. На восьми",
                 "оборотах это ровно 64 SU, то есть цифра спека ядра 10.1",
                 "сходится буквально: одно водяное колесо (256 SU) тянет",
                 "четыре очистителя, большое (512 SU) — восемь.")
        .defineInRange("purifierStressImpact", 8.0, 0.0, 256.0);

    private static final ModConfigSpec.DoubleValue СТРЕСС_ЛАТУННЫЙ = СТРОИТЕЛЬ
        .comment("Нагрузка латунного очистителя на сеть Create (impact).",
                 "16 — вдвое дороже андезитового, а не вчетверо: латунь и так",
                 "платит розовым кварцем, механическими сборщиками",
                 "и четырёхкратным реагентом.")
        .defineInRange("brassPurifierStressImpact", 16.0, 0.0, 256.0);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_МАСТЕРСТВО_ЗА_ОЧИСТКУ = СТРОИТЕЛЬ
        .comment("Мастерство лучшего Кузнеца партии за удачную ночную очистку чанка (из 100).")
        .defineInRange("smithMasteryPerCleanse", 3, 0, 100);

    // ── Фермер (спек, раздел 6) ───────────────────────────────────────

    private static final ModConfigSpec.DoubleValue ФЕРМЕР_БОНУС_ЕДЫ = СТРОИТЕЛЬ
        .comment("Доп. насыщение Фермера от съеденного блюда, долей от его сытности.",
                 "0.5 — плюс половина. Растёт с тиром мастерства.")
        .defineInRange("farmerFoodBonus", 0.5, 0.0, 3.0);

    private static final ModConfigSpec.IntValue ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ = СТРОИТЕЛЬ
        .comment("Мастерство Фермера за одну собранную созревшую культуру (из 100).")
        .defineInRange("farmerMasteryPerHarvest", 1, 0, 100);

    private static final ModConfigSpec.DoubleValue ФЕРМЕР_ЩЕДРЫЙ_УРОЖАЙ = СТРОИТЕЛЬ
        .comment("Шанс на тир, что созревшая культура даст на один предмет больше.",
                 "Итог = число * тир: 0.10 даёт 10 % на первом тире, 20 % на втором, 30 % на третьем.",
                 "Прибавка идёт к каждой стопке дропа — и к зерну, и к семенам.")
        .defineInRange("farmerExtraDropChancePerTier", 0.10, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА = СТРОИТЕЛЬ
        .comment("Во сколько раз грядка бутона чумы растёт медленнее ванильных культур.",
                 "2 — вдвое медленнее. 1 — наравне с пшеницей.",
                 "Смысл замедления: грядка не должна обнулять риск похода в Гниль.",
                 "Правится прямо в игре: /lmpcclasses tune farmerBloomGrowthDivisor <число>")
        .defineInRange("farmerBloomGrowthDivisor", 2, 1, 20);

    private static final ModConfigSpec.DoubleValue ФЕРМЕР_ДИКИЙ_ШАНС = СТРОИТЕЛЬ
        .comment("Вероятность, что заражённая трава в Гнили обронит бутон чумы.",
                 "Это дикий сбор из спека — источник бутона до первой грядки.",
                 "Правится прямо в игре: /lmpcclasses tune farmerBloomWildChance <число>")
        .defineInRange("farmerBloomWildChance", 0.25, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ФЕРМЕР_ДИКИЙ_УРОВЕНЬ = СТРОИТЕЛЬ
        .comment("С какого уровня заражения чанка трава начинает ронять бутон (1..5).",
                 "2 и выше — то, что спек называет Гнилью и её подступами.")
        .defineInRange("farmerBloomWildMinLevel", 2, 1, 5);

    // ── Летописец (спек, раздел 7) ────────────────────────────────────

    private static final ModConfigSpec.DoubleValue ЛЕТОПИСЕЦ_РАДИУС = СТРОИТЕЛЬ
        .comment("Радиус, в котором Летописец видит точную заражённость игроков, в блоках.",
                 "Растёт с тиром мастерства. 0 — только собственная заражённость.")
        .defineInRange("chroniclerInsightRadius", 16.0, 0.0, 128.0);

    private static final ModConfigSpec.DoubleValue ЛЕТОПИСЕЦ_СКОРОСТЬ_1 = СТРОИТЕЛЬ
        .comment("Прибавка к скорости бега Летописца на первом тире, долей (0.05 — плюс 5 %).")
        .defineInRange("chroniclerSpeedTier1", 0.05, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue ЛЕТОПИСЕЦ_СКОРОСТЬ_2 = СТРОИТЕЛЬ
        .comment("То же на втором тире.")
        .defineInRange("chroniclerSpeedTier2", 0.08, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue ЛЕТОПИСЕЦ_СКОРОСТЬ_3 = СТРОИТЕЛЬ
        .comment("То же на третьем тире. Три отдельных числа, а не формула:",
                 "владелец задал 5 / 8 / 15 %, и это не арифметическая прогрессия.")
        .defineInRange("chroniclerSpeedTier3", 0.15, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ = СТРОИТЕЛЬ
        .comment("Мастерство Летописца за минуту рядом хотя бы с одним заражённым (из 100).")
        .defineInRange("chroniclerMasteryPerMinute", 1, 0, 100);

    private static final ModConfigSpec.IntValue СНИМОК_ДЛИТЕЛЬНОСТЬ = СТРОИТЕЛЬ
        .comment("Сколько минут снимок Летописца держится на экране у всей партии.")
        .defineInRange("chroniclerSnapshotMinutes", 5, 1, 60);

    private static final ModConfigSpec.IntValue СНИМОК_КУЛДАУН = СТРОИТЕЛЬ
        .comment("Кулдаун снимка Летописца, в минутах. Сокращается тиром мастерства.")
        .defineInRange("chroniclerSnapshotCooldownMinutes", 15, 0, 240);

    private static final ModConfigSpec.IntValue СНИМОК_РАДИУС = СТРОИТЕЛЬ
        .comment("Радиус снимка в чанках у первого тира. Растёт с тиром мастерства.",
                 "6 — квадрат 13 на 13 чанков вокруг Летописца.",
                 "6, а не 4: на четвёрке округление давало 4/5/5 — тир III не отличался",
                 "от тира II, и докачавшийся игрок не видел разницы. На шести — 6/7/8.")
        .defineInRange("chroniclerSnapshotRadiusChunks", 6, 1, 16);

    private static final ModConfigSpec.IntValue ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_СНИМОК = СТРОИТЕЛЬ
        .comment("Мастерство Летописца за один снимок (из 100).")
        .defineInRange("chroniclerMasteryPerSnapshot", 3, 0, 100);

    private static final ModConfigSpec.IntValue ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_ШИФР = СТРОИТЕЛЬ
        .comment("Мастерство Летописца за одно разгаданное слово тайнописи (из 100).",
                 "Слово раскрывается один раз на весь сервер, накрутить его нельзя,",
                 "поэтому число крупнее прочих.")
        .defineInRange("chroniclerMasteryPerCipher", 8, 0, 100);

    private static final ModConfigSpec.IntValue ФОРСАЖ_РАДИУС = СТРОИТЕЛЬ
        .comment("На сколько чанков форсаж Кузнеца расширяет очиститель на одну ночь.",
                 "2 — андезитовый на эту ночь работает по площади латунного.")
        .defineInRange("smithOverdriveRadiusBonus", 2, 0, 8);

    private static final ModConfigSpec.IntValue ФОРСАЖ_РАСХОД = СТРОИТЕЛЬ
        .comment("Во сколько раз форсаж дороже обычной ночи по реагенту.",
                 "4 — андезитовый съест за ночь форсажа четыре реагента вместо одного.",
                 "Цена и есть смысл активки: разово выжать очиститель, а не держать так всегда.")
        .defineInRange("smithOverdriveReagentFactor", 4, 1, 16);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> КЛЮЧИ_КУЗНЕЦА = СТРОИТЕЛЬ
        .comment("Предметы, которыми Кузнец включает форсаж очистителя.",
                 "По умолчанию — гаечный ключ Create. Жёсткой зависимости на Create нет,",
                 "поэтому список правится файлом, как и chroniclerCameraItems.")
        .defineListAllowEmpty("smithOverdriveItems",
            List.of("create:wrench"), () -> "", о -> о instanceof String);

    private static final ModConfigSpec.DoubleValue КУРИЛЬНИЦА_СИЛА = СТРОИТЕЛЬ
        .comment("Вероятность, что одно распыление курильницы снимет уровень заражения.",
                 "0.15 — вшестеро слабее ночной работы очистителя. Курильница задумана",
                 "как ручная и временная защита шахты, а не как замена машине.")
        .defineInRange("censerCleansePower", 0.15, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue КУРИЛЬНИЦА_СОПРОТИВЛЕНИЕ = СТРОИТЕЛЬ
        .comment("На сколько одно распыление поднимает сопротивление чанка (0..1).",
                 "Главная польза курильницы именно здесь: сопротивление тает само,",
                 "поэтому защита шахты держится, только пока в неё ходят.")
        .defineInRange("censerResistanceGain", 0.10, 0.0, 1.0);

    private static final ModConfigSpec.IntValue КУРИЛЬНИЦА_ПЕРЕЗАРЯДКА = СТРОИТЕЛЬ
        .comment("Пауза между распылениями курильницы, в тиках. 100 — пять секунд.",
                 "Без паузы вся стопка реагента ушла бы в один чанк за минуту.")
        .defineInRange("censerCooldownTicks", 100, 0, 12000);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> КАМЕРЫ = СТРОИТЕЛЬ
        .comment("Предметы, щелчок которыми Летописец считает снимком.",
                 "По умолчанию — камера мода exposure. Жёсткой зависимости на него нет,",
                 "поэтому список правится файлом, а не пересборкой мода.")
        .defineListAllowEmpty("chroniclerCameraItems",
            List.of("exposure:camera"), () -> "", о -> о instanceof String);

    public static final ModConfigSpec SPEC = СТРОИТЕЛЬ.build();

    // ── чтение ────────────────────────────────────────────────────────

    /** Минимальный перерыв между сменами класса, в тиках (20 в секунде). */
    public static long кулдаунСменыТики() {
        return КУЛДАУН_СМЕНЫ_КЛАССА.get() * 1200L;
    }

    /** Доля мастерства старого класса, остающаяся при смене. */
    public static double доляМастерстваПриСмене() {
        return ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ.get();
    }

    public static int порогТира2() { return ПОРОГ_ТИРА_2.get(); }

    public static int порогТира3() { return ПОРОГ_ТИРА_3.get(); }

    /** Множитель силы пассивки для тира. */
    public static float силаТира(int тир) {
        return ClassMastery.множительСилы(тир, СИЛА_ЗА_ТИР.get());
    }

    /** Множитель кулдауна для тира. */
    public static float кулдаунТира(int тир) {
        return ClassMastery.множительКулдауна(тир, КУЛДАУН_ЗА_ТИР.get());
    }

    /** Базовая защита от кулона (для Клирика первого тира — целиком). */
    public static float кулонБазоваяЗащита() {
        return КУЛОН_БАЗОВАЯ_ЗАЩИТА.get().floatValue();
    }

    /** Доля базовой защиты кулона для не-Клирика. */
    public static float кулонДоляНеКлирику() {
        return КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ.get().floatValue();
    }

    /** Кулдаун улучшенного отвара для тира Клирика, в тиках. */
    public static long отварКулдаунТики(int тир) {
        return Math.round(ОТВАР_КУЛДАУН.get() * 1200L * (double) кулдаунТира(тир));
    }

    /** Сколько очков снимает улучшенный отвар на этом тире. */
    public static float отварЛечение(int тир) {
        return ОТВАР_ЛЕЧЕНИЕ.get().floatValue() * силаТира(тир);
    }

    /** Длительность иммунитета от отвара, в тиках. */
    public static long отварИммунитетТики() {
        return ОТВАР_ИММУНИТЕТ.get() * 1200L;
    }

    /** Сколько тиков держать ПКМ на союзнике, чтобы напоить его. */
    public static int скормитьДлительностьТики() {
        return СКОРМИТЬ_ДЛИТЕЛЬНОСТЬ.get();
    }

    /** Максимальное расстояние до союзника, чтобы его напоить, в блоках. */
    public static double скормитьДистанция() {
        return СКОРМИТЬ_ДИСТАНЦИЯ.get();
    }

    /** Интервал пассивной регенерации Клирика для тира, в тиках; 0 — выключена. */
    public static int клирикИнтервалРегенерации(int тир) {
        int базовый = КЛИРИК_ИНТЕРВАЛ_РЕГЕНЕРАЦИИ.get();
        if (базовый <= 0) return 0;
        return Math.max(1, базовый / Math.max(1, тир));
    }

    public static int клирикМастерствоЗаЛечение() {
        return КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ.get();
    }

    /**
     * Интервал пассивного ремонта Кузнеца для тира, в тиках;
     * 0 — ремонт выключен. Чем выше тир, тем короче интервал,
     * но не короче одного тика.
     */
    public static int кузнецИнтервалРемонта(int тир) {
        int базовый = КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА.get();
        if (базовый <= 0) return 0;
        return Math.max(1, Math.round(базовый / силаТира(тир)));
    }

    public static int кузнецМастерствоЗаКрафт() {
        return КУЗНЕЦ_МАСТЕРСТВО_ЗА_КРАФТ.get();
    }

    /** Мастерство Кузнеца за вынос {@code сколько} предметов из печи. */
    public static int кузнецМастерствоЗаПлавку(int сколько) {
        return сколько / КУЗНЕЦ_ПЛАВОК_НА_ОЧКО.get();
    }

    /** Шанс, что скованная вещь выйдет зачарованной, для тира. */
    public static double кузнецШансЗачарования(int тир) {
        return КУЗНЕЦ_ШАНС_ЗАЧАРОВАНИЯ.get() * Math.max(1, тир);
    }

    /** Сила случайного зачарования для тира, в «уровнях стола». */
    public static int кузнецСилаЗачарования(int тир) {
        return КУЗНЕЦ_СИЛА_ЗАЧАРОВАНИЯ.get() * Math.max(1, тир);
    }

    /** Опыт за скованное снаряжение; на первом тире ноль. */
    public static int кузнецОпытЗаКрафт(int тир) {
        return КУЗНЕЦ_ОПЫТ_ЗА_КРАФТ.get() * Math.max(0, тир - 1);
    }

    /** Доп. опыт за плавку: на тир выше первого и на каждое очко мастерства. */
    public static int кузнецОпытЗаПлавку(int тир, int очкиМастерства) {
        return КУЗНЕЦ_ОПЫТ_ЗА_ПЛАВКУ.get() * Math.max(0, тир - 1) * Math.max(0, очкиМастерства);
    }

    /**
     * Вероятность снять уровень заражения за ночь для тира партии.
     * Тир 0 — Кузнеца в игре нет: работает базовая сила, «уровень,
     * доступный без класса» из спека классов 2.1.
     */
    public static float очистительСила(int тирПартии) {
        float база = ОЧИСТИТЕЛЬ_СИЛА.get().floatValue();
        return тирПартии <= 0 ? база : база * силаТира(тирПартии);
    }

    /** Сколько уровней очиститель пытается снять с чанка за ночь. */
    public static int очистительПопыток() {
        return ОЧИСТИТЕЛЬ_ПОПЫТОК.get();
    }

    /** Радиус работы очистителя в чанках вокруг своего. */
    public static int очистительРадиус(boolean латунный) {
        return латунный ? ЛАТУННЫЙ_РАДИУС.get() : ОЧИСТИТЕЛЬ_РАДИУС.get();
    }

    /** Сколько реагента очиститель тира тратит за одну ночь работы. */
    public static int очистительРасход(boolean латунный) {
        return латунный ? ЛАТУННЫЙ_РАСХОД.get() : 1;
    }

    /** Прирост сопротивления чанка за ночь работы очистителя. */
    public static float очистительСопротивление() {
        return ОЧИСТИТЕЛЬ_СОПРОТИВЛЕНИЕ.get().floatValue();
    }

    /** Минимальная скорость вращения, при которой очиститель считается запитанным. */
    public static float очистительМинСкорость(boolean латунный) {
        return (латунный ? ЛАТУННЫЙ_МИН_СКОРОСТЬ : ОЧИСТИТЕЛЬ_МИН_СКОРОСТЬ).get().floatValue();
    }

    /**
     * Нагрузка очистителя на сеть Create — impact на оборот, как у машин
     * Create. Умножается на скорость: восьмёрка на восьми оборотах даёт
     * спековые 64 SU.
     */
    public static float очистительСтресс(boolean латунный) {
        return (латунный ? СТРЕСС_ЛАТУННЫЙ : СТРЕСС_АНДЕЗИТОВЫЙ).get().floatValue();
    }

    public static int кузнецМастерствоЗаОчистку() {
        return КУЗНЕЦ_МАСТЕРСТВО_ЗА_ОЧИСТКУ.get();
    }

    /** Доп. насыщение Фермера, долей от сытности блюда, для тира. */
    public static double фермерБонусЕды(int тир) {
        return ФЕРМЕР_БОНУС_ЕДЫ.get() * силаТира(тир);
    }

    /** Шанс щедрого урожая для тира. */
    public static double фермерЩедрыйУрожай(int тир) {
        return ФЕРМЕР_ЩЕДРЫЙ_УРОЖАЙ.get() * Math.max(1, тир);
    }

    public static int фермерМастерствоЗаУрожай() {
        return ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ.get();
    }

    /** Во сколько раз грядка бутона растёт медленнее ванильных культур. */
    public static int фермерДелительРоста() {
        return ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА.get();
    }

    /** Вероятность, что заражённая трава обронит бутон чумы. */
    public static double фермерДикийШанс() {
        return ФЕРМЕР_ДИКИЙ_ШАНС.get();
    }

    /** С какого уровня заражения чанка трава начинает ронять бутон. */
    public static int фермерДикийУровень() {
        return ФЕРМЕР_ДИКИЙ_УРОВЕНЬ.get();
    }

    /** Радиус обзора Летописца для тира, в блоках. */
    public static double летописецРадиус(int тир) {
        return ЛЕТОПИСЕЦ_РАДИУС.get() * силаТира(тир);
    }

    /** Прибавка к скорости бега Летописца для тира, долей от базовой. */
    public static double летописецСкорость(int тир) {
        return switch (тир) {
            case 2 -> ЛЕТОПИСЕЦ_СКОРОСТЬ_2.get();
            case 3 -> ЛЕТОПИСЕЦ_СКОРОСТЬ_3.get();
            default -> ЛЕТОПИСЕЦ_СКОРОСТЬ_1.get();
        };
    }

    public static int летописецМастерствоВМинуту() {
        return ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ.get();
    }

    /** Сколько тиков снимок живёт на экране. */
    public static int снимокЖивётТики() {
        return СНИМОК_ДЛИТЕЛЬНОСТЬ.get() * 1200;
    }

    /** Кулдаун снимка для тира Летописца, в тиках. */
    public static long снимокКулдаунТики(int тир) {
        return Math.round(СНИМОК_КУЛДАУН.get() * 1200L * (double) кулдаунТира(тир));
    }

    /** Радиус снимка в чанках для тира. */
    public static int снимокРадиусЧанков(int тир) {
        return Math.max(1, Math.round(СНИМОК_РАДИУС.get() * силаТира(тир)));
    }

    public static int летописецМастерствоЗаСнимок() {
        return ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_СНИМОК.get();
    }

    public static int летописецМастерствоЗаШифр() {
        return ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_ШИФР.get();
    }

    /** Идентификаторы предметов-камер, щелчок которыми считается снимком. */
    public static Set<String> камерыЛетописца() {
        return Set.copyOf(КАМЕРЫ.get());
    }

    /** Идентификаторы предметов, которыми Кузнец включает форсаж очистителя. */
    public static Set<String> ключиКузнеца() {
        return Set.copyOf(КЛЮЧИ_КУЗНЕЦА.get());
    }

    /**
     * Множитель силы очистки за скорость вращения: 1.0 на пороге тира,
     * до {@code 1 + purifierSpeedBonus} на {@code purifierSpeedForMax}.
     */
    public static float очистительМножительСкорости(float скорость, boolean латунный) {
        float порог = очистительМинСкорость(латунный);
        float полная = ОЧИСТИТЕЛЬ_СКОРОСТЬ_ПОЛНАЯ.get().floatValue();
        if (скорость <= порог || полная <= порог) return 1f;
        float доля = Math.min((скорость - порог) / (полная - порог), 1f);
        return 1f + доля * ОЧИСТИТЕЛЬ_ПРИБАВКА_ЗА_СКОРОСТЬ.get().floatValue();
    }

    /** На сколько чанков форсаж Кузнеца расширяет очиститель на одну ночь. */
    public static int форсажРадиус() {
        return ФОРСАЖ_РАДИУС.get();
    }

    /** Во сколько раз ночь форсажа дороже по реагенту. */
    public static int форсажРасход() {
        return ФОРСАЖ_РАСХОД.get();
    }

    /** Вероятность, что распыление курильницы снимет уровень заражения. */
    public static float курильницаСила() {
        return КУРИЛЬНИЦА_СИЛА.get().floatValue();
    }

    /** Прирост сопротивления чанка за одно распыление курильницы. */
    public static float курильницаСопротивление() {
        return КУРИЛЬНИЦА_СОПРОТИВЛЕНИЕ.get().floatValue();
    }

    /** Пауза между распылениями курильницы, в тиках. */
    public static int курильницаПерезарядка() {
        return КУРИЛЬНИЦА_ПЕРЕЗАРЯДКА.get();
    }

    // ── правка чисел прямо в игре ─────────────────────────────────────

    /**
     * Числа, которые можно крутить командой {@code /lmpcclasses tune},
     * не выходя из игры и не перезапуская сервер.
     *
     * Заведено по прямой просьбе владельца про скорость грядки, но
     * ограничивать список одним ключом смысла нет: все эти числа
     * подбираются одинаково — на живой сессии, по ощущению. Это то же
     * правило проекта «игровые числа наружу», доведённое до конца:
     * за день до сессии баланс должен править не пересборка мода
     * и даже не перезапуск сервера, а одна строка в чате.
     *
     * Порядок вставки сохраняется — по нему же команда печатает список.
     */
    private static final Map<String, ModConfigSpec.ConfigValue<?>> НАСТРАИВАЕМЫЕ =
        new LinkedHashMap<>();

    static {
        НАСТРАИВАЕМЫЕ.put("classSwitchCooldownMinutes", КУЛДАУН_СМЕНЫ_КЛАССА);
        НАСТРАИВАЕМЫЕ.put("masteryKeepFraction", ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ);
        НАСТРАИВАЕМЫЕ.put("masteryTier2At", ПОРОГ_ТИРА_2);
        НАСТРАИВАЕМЫЕ.put("masteryTier3At", ПОРОГ_ТИРА_3);
        НАСТРАИВАЕМЫЕ.put("masteryPowerPerTier", СИЛА_ЗА_ТИР);
        НАСТРАИВАЕМЫЕ.put("masteryCooldownCutPerTier", КУЛДАУН_ЗА_ТИР);
        НАСТРАИВАЕМЫЕ.put("clericPendantProtection", КУЛОН_БАЗОВАЯ_ЗАЩИТА);
        НАСТРАИВАЕМЫЕ.put("clericBrewCooldownMinutes", ОТВАР_КУЛДАУН);
        НАСТРАИВАЕМЫЕ.put("clericBrewCureAmount", ОТВАР_ЛЕЧЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("clericMasteryPerCure", КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("clericRegenIntervalTicks", КЛИРИК_ИНТЕРВАЛ_РЕГЕНЕРАЦИИ);
        НАСТРАИВАЕМЫЕ.put("smithRepairIntervalTicks", КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА);
        НАСТРАИВАЕМЫЕ.put("smithMasteryPerCraft", КУЗНЕЦ_МАСТЕРСТВО_ЗА_КРАФТ);
        НАСТРАИВАЕМЫЕ.put("smithSmeltsPerMastery", КУЗНЕЦ_ПЛАВОК_НА_ОЧКО);
        НАСТРАИВАЕМЫЕ.put("smithEnchantChancePerTier", КУЗНЕЦ_ШАНС_ЗАЧАРОВАНИЯ);
        НАСТРАИВАЕМЫЕ.put("smithEnchantPowerPerTier", КУЗНЕЦ_СИЛА_ЗАЧАРОВАНИЯ);
        НАСТРАИВАЕМЫЕ.put("smithCraftXpPerTier", КУЗНЕЦ_ОПЫТ_ЗА_КРАФТ);
        НАСТРАИВАЕМЫЕ.put("smithSmeltBonusXpPerTier", КУЗНЕЦ_ОПЫТ_ЗА_ПЛАВКУ);
        НАСТРАИВАЕМЫЕ.put("purifierCleansePower", ОЧИСТИТЕЛЬ_СИЛА);
        НАСТРАИВАЕМЫЕ.put("purifierLevelsPerNight", ОЧИСТИТЕЛЬ_ПОПЫТОК);
        НАСТРАИВАЕМЫЕ.put("purifierRadiusChunks", ОЧИСТИТЕЛЬ_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("purifierResistanceGain", ОЧИСТИТЕЛЬ_СОПРОТИВЛЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("purifierMinSpeed", ОЧИСТИТЕЛЬ_МИН_СКОРОСТЬ);
        НАСТРАИВАЕМЫЕ.put("purifierSpeedBonus", ОЧИСТИТЕЛЬ_ПРИБАВКА_ЗА_СКОРОСТЬ);
        НАСТРАИВАЕМЫЕ.put("purifierSpeedForMax", ОЧИСТИТЕЛЬ_СКОРОСТЬ_ПОЛНАЯ);
        НАСТРАИВАЕМЫЕ.put("smithOverdriveRadiusBonus", ФОРСАЖ_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("smithOverdriveReagentFactor", ФОРСАЖ_РАСХОД);
        НАСТРАИВАЕМЫЕ.put("censerCleansePower", КУРИЛЬНИЦА_СИЛА);
        НАСТРАИВАЕМЫЕ.put("censerResistanceGain", КУРИЛЬНИЦА_СОПРОТИВЛЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("censerCooldownTicks", КУРИЛЬНИЦА_ПЕРЕЗАРЯДКА);
        НАСТРАИВАЕМЫЕ.put("brassPurifierRadiusChunks", ЛАТУННЫЙ_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("brassPurifierReagentPerNight", ЛАТУННЫЙ_РАСХОД);
        НАСТРАИВАЕМЫЕ.put("brassPurifierMinSpeed", ЛАТУННЫЙ_МИН_СКОРОСТЬ);
        НАСТРАИВАЕМЫЕ.put("purifierStressImpact", СТРЕСС_АНДЕЗИТОВЫЙ);
        НАСТРАИВАЕМЫЕ.put("brassPurifierStressImpact", СТРЕСС_ЛАТУННЫЙ);
        НАСТРАИВАЕМЫЕ.put("smithMasteryPerCleanse", КУЗНЕЦ_МАСТЕРСТВО_ЗА_ОЧИСТКУ);
        НАСТРАИВАЕМЫЕ.put("farmerFoodBonus", ФЕРМЕР_БОНУС_ЕДЫ);
        НАСТРАИВАЕМЫЕ.put("farmerMasteryPerHarvest", ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ);
        НАСТРАИВАЕМЫЕ.put("farmerExtraDropChancePerTier", ФЕРМЕР_ЩЕДРЫЙ_УРОЖАЙ);
        НАСТРАИВАЕМЫЕ.put("farmerBloomGrowthDivisor", ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА);
        НАСТРАИВАЕМЫЕ.put("farmerBloomWildChance", ФЕРМЕР_ДИКИЙ_ШАНС);
        НАСТРАИВАЕМЫЕ.put("farmerBloomWildMinLevel", ФЕРМЕР_ДИКИЙ_УРОВЕНЬ);
        НАСТРАИВАЕМЫЕ.put("chroniclerInsightRadius", ЛЕТОПИСЕЦ_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("chroniclerSpeedTier1", ЛЕТОПИСЕЦ_СКОРОСТЬ_1);
        НАСТРАИВАЕМЫЕ.put("chroniclerSpeedTier2", ЛЕТОПИСЕЦ_СКОРОСТЬ_2);
        НАСТРАИВАЕМЫЕ.put("chroniclerSpeedTier3", ЛЕТОПИСЕЦ_СКОРОСТЬ_3);
        НАСТРАИВАЕМЫЕ.put("chroniclerMasteryPerMinute", ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ);
        НАСТРАИВАЕМЫЕ.put("chroniclerSnapshotMinutes", СНИМОК_ДЛИТЕЛЬНОСТЬ);
        НАСТРАИВАЕМЫЕ.put("chroniclerSnapshotCooldownMinutes", СНИМОК_КУЛДАУН);
        НАСТРАИВАЕМЫЕ.put("chroniclerSnapshotRadiusChunks", СНИМОК_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("chroniclerMasteryPerSnapshot", ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_СНИМОК);
        НАСТРАИВАЕМЫЕ.put("chroniclerMasteryPerCipher", ЛЕТОПИСЕЦ_МАСТЕРСТВО_ЗА_ШИФР);
    }

    /** Имена настраиваемых чисел, в порядке объявления. */
    public static Set<String> настраиваемые() {
        return НАСТРАИВАЕМЫЕ.keySet();
    }

    /** Текущее значение числа; {@code null}, если такого ключа нет. */
    public static Object значение(String ключ) {
        ModConfigSpec.ConfigValue<?> поле = НАСТРАИВАЕМЫЕ.get(ключ);
        return поле == null ? null : поле.get();
    }

    /**
     * Записать новое значение и сохранить файл конфига. Возвращает
     * то, что реально записалось, или {@code null}, если ключ неизвестен
     * либо конфиг ещё не загружен.
     *
     * Значение за границами {@code defineInRange} не отвергается тихо:
     * ModConfigSpec поправит его при следующей загрузке, а до тех пор
     * в памяти жило бы то, чего в файле нет. Поэтому запись сразу
     * перечитывается и наружу отдаётся именно она.
     */
    public static Object задать(String ключ, double новое) {
        ModConfigSpec.ConfigValue<?> поле = НАСТРАИВАЕМЫЕ.get(ключ);
        if (поле == null) return null;
        try {
            if (поле instanceof ModConfigSpec.IntValue целое) {
                целое.set((int) Math.round(новое));
            } else if (поле instanceof ModConfigSpec.DoubleValue дробное) {
                дробное.set(новое);
            } else {
                return null;
            }
            поле.save();
            return поле.get();
        } catch (RuntimeException e) {
            // Конфиг ещё не загружен или значение не лезет в диапазон —
            // для команды это обычный отказ, а не повод ронять сервер.
            return null;
        }
    }

    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
