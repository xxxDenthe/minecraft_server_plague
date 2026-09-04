package dev.denthe.shade.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.denthe.shade.LmpcShade;
import dev.denthe.shade.ShadeConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Серость мира. Куски, все клиентские и независимые:
 *
 *  1. Цветокоррекция кадра — ванильный PostChain на главный буфер, один
 *     полноэкранный проход на AFTER_LEVEL.
 *  2. Туман — цвет в серый (ComputeFogColor), под землёй ещё и жёстко
 *     стянут ближе (RenderFog, только с cancel).
 *  3. Споровая взвесь — редкие частицы пепла вокруг игрока (ClientTick),
 *     и отдельно от неё — грибные споры вспышками у самой земли.
 *
 * ponytail: проход на AFTER_LEVEL без миксина. Цена — рука от первого
 * лица и HUD в грейд не попадают (рисуются позже). HUD и так должен
 * оставаться читаемым.
 */
@EventBusSubscriber(modid = LmpcShade.MODID, value = Dist.CLIENT)
public final class ShadeClient {
    private ShadeClient() {}

    private static final Logger LOG = LoggerFactory.getLogger(LmpcShade.MODID);
    private static final float TAU = 6.2831853f;

    private static final ResourceLocation EFFECT =
        ResourceLocation.fromNamespaceAndPath(LmpcShade.MODID, "shaders/post/plague.json");

    private static PostChain chain;
    private static int chainW = -1;
    private static int chainH = -1;
    private static boolean broken; // не загрузился — больше не пытаемся до перезапуска

    private static long lastNano;
    private static float pulsePhase;   // фаза сердцебиения, накапливаем сами (переменный темп)
    private static boolean fogHookLogged;

    // --- цветокоррекция кадра -----------------------------------------

    @SubscribeEvent
    static void onRenderStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (broken || !ShadeConfig.ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (!ensureChain(mc, main)) return;

        float partialTick = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        pushUniforms(mc, partialTick);
        chain.process(partialTick);
        main.bindWrite(false);
    }

    private static boolean ensureChain(Minecraft mc, RenderTarget main) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT);
                chainW = chainH = -1;
            }
            if (w != chainW || h != chainH) {
                chain.resize(w, h);
                chainW = w;
                chainH = h;
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            broken = true;
            close();
            LOG.error("Пост-эффект чумы не загрузился, отключаю его", ex);
            return false;
        }
    }

    /**
     * Раскладываем конфиг и состояние мира в юниформы каждый кадр.
     * PostChain 1.21.1 умеет только setUniform(String, float): вектора
     * собираются в шейдере. Отсутствующий юниформ PostChain глотает сам.
     */
    private static void pushUniforms(Minecraft mc, float partialTick) {
        float[] tint = ShadeConfig.tintRgb();
        chain.setUniform("Saturation", ShadeConfig.SATURATION.get().floatValue());
        chain.setUniform("Brightness", ShadeConfig.BRIGHTNESS.get().floatValue());
        chain.setUniform("TintR", tint[0]);
        chain.setUniform("TintG", tint[1]);
        chain.setUniform("TintB", tint[2]);
        chain.setUniform("TintStrength", ShadeConfig.TINT_STRENGTH.get().floatValue());
        chain.setUniform("Vignette", ShadeConfig.VIGNETTE.get().floatValue());
        chain.setUniform("Grain", ShadeConfig.GRAIN.get().floatValue());
        chain.setUniform("Posterize", ShadeConfig.POSTERIZE.get().floatValue());
        chain.setUniform("NightFactor", nightFactor(mc, partialTick));
        chain.setUniform("NightDarkness", ShadeConfig.NIGHT_DARKNESS.get().floatValue());

        float hf = healthFactor(mc);
        chain.setUniform("HealthFactor", hf);
        chain.setUniform("Pulse", heartbeat(hf));
    }

    /** 0 днём, 1 глухой ночью. getSkyDarken(pt): 1.0 полдень .. 0.2 полночь. */
    private static float nightFactor(Minecraft mc, float partialTick) {
        float boost = ShadeConfig.NIGHT_BOOST.get().floatValue();
        if (boost <= 0f || mc.level == null) return 0f;
        ClientLevel level = mc.level;
        if (!level.dimensionType().hasSkyLight()) return 0f;
        float night = (1.0f - level.getSkyDarken(partialTick)) / 0.8f;
        return Mth.clamp(night * boost, 0f, 1f);
    }

    /** 0 если здоров / выключено; растёт квадратично к 1 у самой смерти. */
    private static float healthFactor(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!ShadeConfig.LOW_HEALTH_EFFECT.get() || p == null || p.isDeadOrDying()) return 0f;
        float max = p.getMaxHealth();
        if (max <= 0f) return 0f;
        float t = ShadeConfig.LOW_HEALTH_THRESHOLD.get().floatValue();
        float frac = p.getHealth() / max;
        if (frac >= t) return 0f;
        float lin = (t - frac) / t;                        // 0..1 линейно
        float eased = lin * lin;                           // тихо в начале, резко у смерти
        return Mth.clamp(eased * ShadeConfig.LOW_HEALTH_INTENSITY.get().floatValue(), 0f, 1f);
    }

    /** Удар сердца 0..1: чем хуже здоровье, тем чаще; фазу копим сами. */
    private static float heartbeat(float hf) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0f : Math.min((now - lastNano) / 1.0e9f, 0.1f);
        lastNano = now;
        if (hf <= 0f) { pulsePhase = 0f; return 0f; }
        float hz = Mth.lerp(hf, 1.1f, 2.7f);
        pulsePhase = (pulsePhase + dt * hz * TAU) % TAU;
        float s = 0.5f + 0.5f * Mth.sin(pulsePhase);
        return s * s * s;                                  // острый «тук», а не плавная волна
    }

    static void close() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }

    // --- туман --------------------------------------------------------

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor e) {
        if (!ShadeConfig.ENABLED.get()) return;
        float k = ShadeConfig.FOG_COLOR_STRENGTH.get().floatValue();
        if (k <= 0f) return;
        float[] c = ShadeConfig.fogRgb();
        e.setRed(Mth.lerp(k, e.getRed(), c[0]));
        e.setGreen(Mth.lerp(k, e.getGreen(), c[1]));
        e.setBlue(Mth.lerp(k, e.getBlue(), c[2]));
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog e) {
        if (!fogHookLogged) {
            fogHookLogged = true;
            LOG.info("RenderFog-хук живой (mode={}, type={})", e.getMode(), e.getType());
        }
        if (!ShadeConfig.ENABLED.get()) return;
        if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return; // небесный туман не трогаем
        float k = ShadeConfig.CAVE_FOG_STRENGTH.get().floatValue();
        if (k <= 0f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = e.getCamera();
        if (mc.level.canSeeSky(cam.getBlockPosition())) return; // под открытым небом не трогаем

        // Плавно по глубине под потолком: под кроной деревьев на поверхности
        // (y ~63) тумана нет, в пещеру он вползает постепенно, без скачка на
        // одном блоке. Раньше был бинарный canSeeSky — отсюда «щелчок» тумана
        // под деревьями у лавового озера.
        float topY = ShadeConfig.CAVE_FOG_TOP_Y.get().floatValue();
        float depth = Mth.clamp((topY - (float) cam.getPosition().y) / 32.0f, 0f, 1f);
        if (depth <= 0f) return;

        float kd = k * depth;
        float target = ShadeConfig.CAVE_FOG_DISTANCE.get().floatValue();
        float far = Mth.lerp(kd, e.getFarPlaneDistance(), Math.min(e.getFarPlaneDistance(), target));
        e.setFarPlaneDistance(far);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.2f));
        e.setCanceled(true); // NeoForge применяет near/far только при cancel
    }

    // --- споровая взвесь ----------------------------------------------

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post e) {
        if (!ShadeConfig.ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        ClientLevel level = mc.level;
        RandomSource rnd = level.random;
        double px = mc.player.getX();
        double py = mc.player.getEyeY();
        double pz = mc.player.getZ();

        // Два независимых эффекта: если пепел выключен (sporeRate = 0),
        // споры у земли всё равно должны работать сами по себе — раньше
        // ранний return пепла глушил и их тоже.
        int rate = ShadeConfig.SPORE_RATE.get();
        for (int i = 0; i < rate; i++) {
            double x = px + (rnd.nextDouble() - 0.5) * 30.0;
            double y = py + (rnd.nextDouble() - 0.5) * 16.0;
            double z = pz + (rnd.nextDouble() - 0.5) * 30.0;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, 0.0, 0.0);
        }

        spawnGroundSpores(level, rnd, px, mc.player.getY(), pz);
    }

    /**
     * Грибные споры у земли — второй, отдельный от пепла эффект: узкий пояс
     * у ног игрока, не облако вокруг головы. Реже пепла и вспышками, а не
     * ровным потоком (groundSporeChance), чтобы не слиться с ним в одну
     * взвесь. Ванильная частица MYCELIUM — уже блёкло-серо-бурая, свою
     * текстуру красить не пришлось.
     */
    private static void spawnGroundSpores(ClientLevel level, RandomSource rnd, double px, double py, double pz) {
        int rate = ShadeConfig.GROUND_SPORE_RATE.get();
        if (rate <= 0) return;
        if (rnd.nextFloat() >= ShadeConfig.GROUND_SPORE_CHANCE.get().floatValue()) return;

        for (int i = 0; i < rate; i++) {
            double x = px + (rnd.nextDouble() - 0.5) * 20.0;
            double y = py + rnd.nextDouble() * 2.0;
            double z = pz + (rnd.nextDouble() - 0.5) * 20.0;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            level.addParticle(ParticleTypes.MYCELIUM, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
