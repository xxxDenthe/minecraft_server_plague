package dev.denthe.shade.client;

import dev.denthe.shade.ShadeConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

/**
 * Пасмурное небо над Верхним миром. Регистрируем свой
 * DimensionSpecialEffects вместо ванильного — чистый API, без миксинов.
 *
 * SkyType.NONE убирает купол, солнце, луну и звёзды: сверху остаётся
 * ровный цвет тумана (дальше его правит наш ComputeFogColor). Облака
 * рисуются отдельно и остаются, только ниже — cloudHeight.
 *
 * ponytail: диск солнца пропадает совсем. Захотим бледное солнце сквозь
 * дымку — это уже миксин в LevelRenderer#renderSky.
 *
 * Тут же — затемнение ночной поверхности через штатный хук
 * IDimensionSpecialEffectsExtension#adjustLightmapColors: правим сам
 * lightmap (а не только кадр), поэтому ночью без факела не видно ничего
 * по-настоящему — мобов, блоки, всё. Миксин не нужен.
 *
 * Конфиг читается один раз при регистрации эффектов; смена overcast /
 * cloudHeight применяется после перезахода в мир. Затемнение lightmap
 * читает конфиг каждый кадр — крутится на лету.
 */
public final class ShadeSky {
    private ShadeSky() {}

    public static void onRegister(RegisterDimensionSpecialEffectsEvent e) {
        if (!ShadeConfig.SKY_OVERCAST.get()) return;
        e.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS,
            new Overcast(ShadeConfig.CLOUD_HEIGHT.get()));
    }

    private static final class Overcast extends DimensionSpecialEffects {
        Overcast(float cloudHeight) {
            // cloudLevel, hasGround, skyType, forceBrightLightmap, constantAmbientLight
            super(cloudHeight, true, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            // ванильная яркостная кривая, но одинаково по каналам — без синевы
            double k = brightness * 0.94 + 0.06;
            return color.scale(k);
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTick) {
            return null; // без цветных рассветов и закатов — пасмурно ровно
        }

        /**
         * Затемняем ночную поверхность в самом lightmap. Крушим только то,
         * что освещено небом и не освещено блоками: ночью факел/фонарь —
         * единственное, что оставляет видимость. Пещеры (мало skyLight) и
         * день (skyDarken≈1) не трогаем.
         */
        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                         float blockLightRedFlicker, float skyLight,
                                         int pixelX, int pixelY, Vector3f colors) {
            float boost = ShadeConfig.NIGHT_BOOST.get().floatValue();
            if (boost <= 0f) return;
            float night = Mth.clamp((1.0f - skyDarken) / 0.8f, 0f, 1f); // 0 день .. 1 полночь
            if (night <= 0f) return;

            float block = pixelX / 15.0f;   // блочный свет 0..1
            float sky = pixelY / 15.0f;     // небесный свет 0..1
            // Свет «спасает» по крутой кривой: даже слабый блочный свет
            // (тусклый край факела) заметно вытаскивает пиксель из тьмы.
            // Куб вместо квадрата — свет бьёт дальше. Полную тьму
            // (block == 0) не трогаем: там noBlock всё равно 1.
            float lightSave = 1.0f - block;
            float noBlock = lightSave * lightSave * lightSave;
            float crush = Mth.clamp(night * sky * noBlock * boost, 0f, 1f);
            if (crush <= 0f) return;

            float floor = ShadeConfig.SURFACE_NIGHT_FLOOR.get().floatValue();
            colors.mul(Mth.lerp(crush, 1.0f, floor));
        }
    }
}
