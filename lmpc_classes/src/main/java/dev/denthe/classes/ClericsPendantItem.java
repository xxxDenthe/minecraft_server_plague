package dev.denthe.classes;

import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Кулон Клирика. Спек — 2026-09-04-klassy-design.md, раздел 4.
 *
 * Сам по себе ничего не делает — {@code ICurioItem} без единого
 * переопределения, вся сила в том, что его можно надеть в слот
 * Curios (necklace). Проверку «надет ли кулон» и саму защиту
 * считает {@link ClassesApi#protectionBonus}, которую дёргает
 * `plaguecore` через рефлексию.
 */
public class ClericsPendantItem extends Item implements ICurioItem {
    public ClericsPendantItem(Properties свойства) {
        super(свойства);
    }
}
