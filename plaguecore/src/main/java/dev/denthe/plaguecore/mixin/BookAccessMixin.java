package dev.denthe.plaguecore.mixin;

import dev.denthe.plaguecore.client.SecretText;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Врезка тайнописи в книгу. Спек лора, раздел 5.
 *
 * Точка выбрана намеренно узкой: {@code BookAccess.getPage} — единственное
 * место, откуда экран книги берёт текст страницы. Через него проходят и
 * книга в руке, и книга на пюпитре, поэтому одной врезки хватает на обе.
 *
 * Дорого это не стоит: экран кэширует разобранную страницу и зовёт
 * {@code getPage} только при перелистывании, а не каждый кадр.
 */
@Mixin(BookViewScreen.BookAccess.class)
public class BookAccessMixin {

    @Inject(method = "getPage", at = @At("RETURN"), cancellable = true)
    private void plaguecore$подменитьТайныеСлова(int страница, CallbackInfoReturnable<FormattedText> ci) {
        FormattedText было = ci.getReturnValue();
        FormattedText стало = SecretText.применить(было);
        if (стало != было) ci.setReturnValue(стало);
    }
}
