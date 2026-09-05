package dev.denthe.plaguecore.mixin;

import dev.denthe.plaguecore.client.PossessionClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подмена нажатий при одержимости.
 * Заметка `docs/superpowers/notes/2026-09-06-oderzhimost.md`.
 *
 * Точка одна и единственная возможная: хвост {@code KeyboardInput.tick}.
 * Свои клавиши там уже прочитаны — есть что выбросить, — а движение
 * ещё не посчитано: {@code LocalPlayer.aiStep} зовёт этот tick прямо
 * перед тем, как двигать тело.
 *
 * Логики здесь нет нарочно: миксин только зовёт
 * {@link PossessionClient#подменить}. Врезка в чужой класс должна
 * оставаться в одну строку — её и читать, и чинить приходится
 * с оглядкой на чужие имена полей.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void plaguecore$подменитьНажатия(boolean крадучись, float множитель, CallbackInfo ci) {
        PossessionClient.подменить((Input) (Object) this);
    }
}
