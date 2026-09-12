package dev.denthe.plaguecore.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Чтение записи в руке. Спек «Запись как предмет», раздел «Решение по носителю».
 *
 * Наследуется от ванильного экрана книги, а не пишет своё чтение заново:
 * страницы приходят тем же {@code BookAccess}, через который работает
 * врезка тайнописи. Отличий от ванили ровно два, и оба — про ощущение
 * физической бумаги, а не интерфейса.
 *
 * Первое: убрана кнопка «Готово». Она единственная в этом экране выглядела
 * кнопкой мода, а не книгой; закрывается запись клавишей выхода, как
 * закрывается любой экран.
 *
 * Второе: после перелистывания через страницу проходит лист бумаги.
 * Рисуется он той же текстурой разворота, сжатой по ширине, — так лист
 * уходит к переплёту, а не «переключается». Полторы десятых секунды,
 * чтобы движение читалось, но не мешало читать.
 *
 * Сам вид страницы — бумага, разлиновка, чернильные уголки навигации —
 * лежит в ресурсах ({@code assets/minecraft/textures/gui/book.png}),
 * а не здесь: тогда запись на кафедре выглядит так же, как в руке.
 */
public class ArchiveRecordScreen extends BookViewScreen {

    /** Сколько идёт переворот листа, мс. Дольше — начинает мешать чтению. */
    private static final long ПЕРЕВОРОТ = 150L;

    /** Область бумаги в текстуре разворота: без переплёта, с полями. */
    private static final int ЛИСТ_X = 29, ЛИСТ_Y = 4, ЛИСТ_Ш = 136, ЛИСТ_В = 173;

    private final BookViewScreen.BookAccess доступ;
    private long началПереворот = -1L;
    private boolean вперёд;
    private int страница;

    /** Открыть запись, если в стопке есть начинка книги. */
    public static void открыть(ItemStack стопка) {
        BookViewScreen.BookAccess доступ = BookViewScreen.BookAccess.fromItem(стопка);
        if (доступ != null) {
            Minecraft.getInstance().setScreen(new ArchiveRecordScreen(доступ));
        }
    }

    private ArchiveRecordScreen(BookViewScreen.BookAccess доступ) {
        super(доступ);
        this.доступ = доступ;
    }

    /** Никаких кнопок внизу: выход — клавишей выхода. */
    @Override
    protected void createMenuControls() {
    }

    // Номер открытой страницы ванильный экран держит в закрытом поле, а
    // анимации нужно знать, случился ли переворот на самом деле: стрелками
    // клавиатуры перелистывают и с последней страницы. Поэтому считаем сами.

    @Override
    protected void pageForward() {
        super.pageForward();
        if (страница < доступ.getPageCount() - 1) {
            страница++;
            запомнитьПереворот(true);
        }
    }

    @Override
    protected void pageBack() {
        super.pageBack();
        if (страница > 0) {
            страница--;
            запомнитьПереворот(false);
        }
    }

    @Override
    public boolean setPage(int номер) {
        boolean сменилась = super.setPage(номер);
        if (сменилась) {
            вперёд = номер > страница;
            страница = Mth.clamp(номер, 0, доступ.getPageCount() - 1);
            запомнитьПереворот(вперёд);
        }
        return сменилась;
    }

    private void запомнитьПереворот(boolean вперёд) {
        this.началПереворот = Util.getMillis();
        this.вперёд = вперёд;
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        super.render(графика, мышьX, мышьY, кадр);
        переворот(графика);
    }

    private void переворот(GuiGraphics графика) {
        if (началПереворот < 0) return;
        long прошло = Util.getMillis() - началПереворот;
        if (прошло >= ПЕРЕВОРОТ) {
            началПереворот = -1L;
            return;
        }

        // Лист складывается к переплёту: ширина падает, высота остаётся.
        float доля = 1f - (float) прошло / ПЕРЕВОРОТ;
        int ширина = Math.max(1, Math.round(ЛИСТ_Ш * доля * доля));
        int край = (this.width - 192) / 2 + ЛИСТ_X;
        int x = вперёд ? край : край + ЛИСТ_Ш - ширина;

        графика.blit(BookViewScreen.BOOK_LOCATION, x, 2 + ЛИСТ_Y, ширина, ЛИСТ_В,
            ЛИСТ_X, ЛИСТ_Y, ЛИСТ_Ш, ЛИСТ_В, 256, 256);
    }
}
