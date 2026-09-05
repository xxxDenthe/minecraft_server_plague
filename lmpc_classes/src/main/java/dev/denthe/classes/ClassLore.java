package dev.denthe.classes;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Тексты гримуара: заголовок, роль, лор, список способностей и строка
 * «как растёт мастерство». Спек — 2026-09-04-klassy-design.md, разделы
 * 4–7.
 *
 * **С 0.6.0 тексты живут в языковых файлах, не в Java.** Причина
 * та же, по которой в проекте вынесены наружу игровые числа: лор —
 * не моя территория, править его владельцу, а правка
 * `assets/lmpc_classes/lang/ru_ru.json` не требует ни пересборки
 * мода, ни моего участия. Заодно английский файл перестал врать —
 * до этого он переводил только названия предметов, а весь текст
 * гримуара был русским в коде.
 *
 * Класс публичный: тем же методом пользуются и экран гримуара
 * в подпакете {@code client}, и серверные сообщения команд.
 * Отдельный мост {@code ClassLoreAccess} за этим больше не нужен
 * и удалён.
 */
public final class ClassLore {
    private ClassLore() {}

    /** Сколько строк способностей объявлено в языковом файле у каждого класса. */
    private static int способностей(PlayerClassData.Класс класс) {
        return switch (класс) {
            case CLERIC -> 3;
            case FARMER -> 3;
            case SMITH, CHRONICLER -> 1;
            case NONE -> 0;
        };
    }

    /** Корень ключей класса, например {@code class.lmpc_classes.cleric}. */
    private static String ключ(PlayerClassData.Класс класс) {
        return "class." + LmpcClasses.MODID + "." + класс.name().toLowerCase(Locale.ROOT);
    }

    /** Название класса. */
    public static Component заголовок(PlayerClassData.Класс класс) {
        return Component.translatable(ключ(класс));
    }

    /** Роль одной строкой — та же формулировка, что в спеке. */
    public static Component роль(PlayerClassData.Класс класс) {
        return Component.translatable(ключ(класс) + ".role");
    }

    /** Лор-абзац. */
    public static Component лор(PlayerClassData.Класс класс) {
        return Component.translatable(ключ(класс) + ".lore");
    }

    /** Чем растёт мастерство этого класса — самое частое «а как качать?». */
    public static Component ростМастерства(PlayerClassData.Класс класс) {
        return Component.translatable(ключ(класс) + ".mastery");
    }

    /** Список реально работающих способностей. У NONE пустой. */
    public static List<Component> способности(PlayerClassData.Класс класс) {
        List<Component> список = new ArrayList<>();
        for (int i = 1; i <= способностей(класс); i++) {
            список.add(Component.translatable(ключ(класс) + ".ability." + i));
        }
        return список;
    }
}
