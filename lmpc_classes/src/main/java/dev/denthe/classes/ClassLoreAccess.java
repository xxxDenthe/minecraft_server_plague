package dev.denthe.classes;

import net.minecraft.client.Minecraft;

/**
 * Публичный доступ к {@link ClassLore} (package-private) и к
 * мастерству локального игрока — для экрана гримуара в подпакете
 * {@code client}.
 */
public final class ClassLoreAccess {
    private ClassLoreAccess() {}

    public static String заголовок(PlayerClassData.Класс класс) {
        return ClassLore.записьДля(класс).заголовок();
    }

    public static String роль(PlayerClassData.Класс класс) {
        return ClassLore.записьДля(класс).роль();
    }

    public static String лор(PlayerClassData.Класс класс) {
        return ClassLore.записьДля(класс).лор();
    }

    /** Мастерство текущего класса локального игрока. 0, если игрока нет. */
    public static int мастерство() {
        var игрок = Minecraft.getInstance().player;
        return игрок == null ? 0 : PlayerClassData.данные(игрок).мастерство;
    }
}
