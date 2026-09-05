package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Одержимость: чужие руки на пульте тела.
 * Заметка `docs/superpowers/notes/2026-09-06-oderzhimost.md`.
 *
 * Тело двигает клиент самой жертвы, просто чужими нажатиями. Сервер
 * их только развозит — двигать тело отсюда нельзя, движение в Minecraft
 * клиентское, и любая попытка кончается резинкой на экране.
 *
 * Водителей два, труба одна: админ шлёт нажатия пакетом
 * {@link PlagueNetwork.Steer}, чума считает их здесь же
 * в {@link #вестиЧумой}. Дальше оба пути сходятся в один
 * {@link PlagueNetwork.Drive} до жертвы.
 *
 * Вся арифметика — в {@link PossessionRules}, она без Minecraft
 * и проверяется обычным JUnit.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class Possession {
    private Possession() {}

    /** Одна живая сессия. Ключ в {@link #сессии} — кого ведут. */
    private static final class Сессия {
        final UUID жертва;
        /** Кто правит. {@code null} — правит чума. */
        final UUID админ;
        int осталось;

        int флаги;
        int прошлыеФлаги;
        float рыскание;
        float тангаж;

        /** Чума бьёт один раз за сессию — решение владельца. */
        boolean ударНанесён;

        // Куда вернуть админа, когда он слезет с пульта.
        ResourceKey<Level> откудаМир;
        Vec3 откуда;
        float откудаРыскание;
        float откудаТангаж;
        GameType прежнийРежим;

        Сессия(UUID жертва, UUID админ, int осталось) {
            this.жертва = жертва;
            this.админ = админ;
            this.осталось = осталось;
        }

        boolean чума() { return админ == null; }
    }

    /** Кого ведут → сессия. */
    private static final Map<UUID, Сессия> сессии = new HashMap<>();

    /** Кто правит → кого он правит. Стережёт {@link #принятьНажатие}. */
    private static final Map<UUID, UUID> заПультом = new HashMap<>();

    /** Кого когда предлагали. Без этого чат зальёт одним именем. */
    private static final Map<UUID, Long> когдаЗвали = new HashMap<>();

    private static long тик;

    /**
     * Сервер, взятый из тика. Держим здесь, а не тянем в PlagueCore:
     * завершение сессии умеет прийти и из команды, и из выхода игрока,
     * и обеим точкам нужен список игроков.
     */
    private static MinecraftServer сервер;

    // ── вход ───────────────────────────────────────────────────────────

    /**
     * Отдать тело чуме на несколько секунд.
     *
     * @return null, если получилось; иначе причина отказа
     */
    public static Component отдатьЧуме(ServerPlayer жертва) {
        Component отказ = проверить(жертва);
        if (отказ != null) return отказ;

        Сессия с = new Сессия(жертва.getUUID(), null, PlagueConstants.SEIZE_TICKS);
        сессии.put(жертва.getUUID(), с);
        предупредить(жертва);
        return null;
    }

    /**
     * Посадить админа за пульт чужого тела.
     *
     * @return null, если получилось; иначе причина отказа
     */
    public static Component вселить(ServerPlayer админ, ServerPlayer жертва) {
        if (админ == жертва) return Component.literal("В себя вселяться незачем.");
        if (заПультом.containsKey(админ.getUUID())) {
            return Component.literal("Ты уже за пультом. Сначала /plague release.");
        }
        Component отказ = проверить(жертва);
        if (отказ != null) return отказ;

        Сессия с = new Сессия(жертва.getUUID(), админ.getUUID(), PlagueConstants.POSSESS_TICKS);
        с.рыскание = жертва.getYRot();
        с.тангаж = жертва.getXRot();

        с.откудаМир = админ.level().dimension();
        с.откуда = админ.position();
        с.откудаРыскание = админ.getYRot();
        с.откудаТангаж = админ.getXRot();
        с.прежнийРежим = админ.gameMode.getGameModeForPlayer();

        сессии.put(жертва.getUUID(), с);
        заПультом.put(админ.getUUID(), жертва.getUUID());

        // Наблюдатель и телепорт к телу: камера админа рисует жертву из его
        // собственного мира, а не приходит картинкой. Без этого сущности
        // рядом с жертвой на его клиенте попросту нет.
        админ.setGameMode(GameType.SPECTATOR);
        админ.teleportTo((ServerLevel) жертва.level(),
            жертва.getX(), жертва.getY(), жертва.getZ(), жертва.getYRot(), жертва.getXRot());
        PacketDistributor.sendToPlayer(админ, new PlagueNetwork.Puppet(жертва.getId()));

        предупредить(жертва);
        return null;
    }

    /** Оборвать сессию этого тела. @return был ли кто-то за пультом */
    public static boolean отпустить(UUID жертва) {
        Сессия с = сессии.remove(жертва);
        if (с == null) return false;
        завершить(с);
        return true;
    }

    private static Component проверить(ServerPlayer жертва) {
        if (сессии.containsKey(жертва.getUUID())) {
            return Component.literal("Это тело уже ведут.");
        }
        if (!жертва.isAlive()) return Component.literal("Тело мертво.");
        if (жертва.isSleeping()) return Component.literal("Тело спит. Разбуди сначала.");
        if (жертва.isPassenger()) return Component.literal("Тело едет верхом.");
        if (жертва.containerMenu != жертва.inventoryMenu) {
            return Component.literal("Тело копается в сундуке.");
        }
        int стадия = PlagueApi.getStage(жертва);
        if (стадия < PlagueConstants.POSSESS_MIN_STAGE) {
            return Component.literal("Стадия " + стадия + ", а нужна "
                + PlagueConstants.POSSESS_MIN_STAGE + ". Тело ещё своё.");
        }
        return null;
    }

    /** Сердцебиение в уши: жертва должна понять, что это чума, а не лаги. */
    private static void предупредить(ServerPlayer жертва) {
        жертва.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0f, 0.6f);
    }

    // ── нажатия админа ─────────────────────────────────────────────────

    /**
     * Нажатие с клиента админа. Проверка одна и здесь: отправитель обязан
     * прямо сейчас кого-то вести. Пакет открыт всем, доверять ему нельзя.
     */
    public static void принятьНажатие(ServerPlayer отправитель, PlagueNetwork.Steer пакет) {
        UUID кого = заПультом.get(отправитель.getUUID());
        if (кого == null) return;
        Сессия с = сессии.get(кого);
        if (с == null || с.чума()) return;

        с.флаги = пакет.флаги() & 0xFF;
        с.рыскание = пакет.рыскание();
        с.тангаж = пакет.тангаж();
    }

    // ── тик ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void приТике(ServerTickEvent.Post событие) {
        сервер = событие.getServer();
        тик++;

        if (!сессии.isEmpty()) вестиТела(сервер);

        int период = PlagueConstants.POSSESS_OFFER_TICKS;
        if (период > 0 && тик % период == 0) искатьКогоПредложить(сервер);
    }

    private static void вестиТела(MinecraftServer сервер) {
        List<Сессия> кончились = new ArrayList<>();

        for (Сессия с : List.copyOf(сессии.values())) {
            ServerPlayer жертва = сервер.getPlayerList().getPlayer(с.жертва);
            if (жертва == null || !жертва.isAlive()) {
                кончились.add(с);
                continue;
            }
            if (!с.чума() && сервер.getPlayerList().getPlayer(с.админ) == null) {
                кончились.add(с);   // водитель вышел из игры
                continue;
            }

            if (с.чума()) вестиЧумой(сервер, жертва, с);

            if (PossessionRules.есть(с.флаги, PossessionRules.УДАР)
                && !PossessionRules.есть(с.прошлыеФлаги, PossessionRules.УДАР)) {
                ударить(жертва);
            }
            с.прошлыеФлаги = с.флаги;

            PlagueNetwork.отправитьУправление(жертва, new PlagueNetwork.Drive(
                true, с.чума(), с.флаги, с.рыскание, с.тангаж));

            if (--с.осталось <= 0) кончились.add(с);
        }

        for (Сессия с : кончились) {
            сессии.remove(с.жертва);
            завершить(с);
        }
    }

    /**
     * Что чума делает с телом: повернуться к ближайшему живому, дойти
     * и ударить **один раз**. Не серию — решение владельца, один удар
     * пугает сильнее и никого не добивает.
     */
    private static void вестиЧумой(MinecraftServer сервер, ServerPlayer жертва, Сессия с) {
        List<ServerPlayer> рядом = соседи(сервер, жертва);
        if (рядом.isEmpty()) {
            с.флаги = PossessionRules.ВПЕРЁД;   // бить некого — просто бредёт
            return;
        }

        double[] x = new double[рядом.size()];
        double[] z = new double[рядом.size()];
        for (int i = 0; i < рядом.size(); i++) {
            x[i] = рядом.get(i).getX();
            z[i] = рядом.get(i).getZ();
        }
        int i = PossessionRules.ближайший(жертва.getX(), жертва.getZ(), x, z,
            PlagueConstants.POSSESS_OFFER_RADIUS);
        if (i < 0) {
            с.флаги = PossessionRules.ВПЕРЁД;
            return;
        }

        double dx = x[i] - жертва.getX();
        double dz = z[i] - жертва.getZ();
        с.рыскание = PossessionRules.курс(dx, dz);
        с.тангаж = 0f;

        if (PossessionRules.дотянуться(dx, dz, PlagueConstants.SEIZE_REACH)) {
            с.флаги = с.ударНанесён ? 0 : PossessionRules.УДАР;
            с.ударНанесён = true;
        } else {
            с.флаги = PossessionRules.ВПЕРЁД;
        }
    }

    /** Живые игроки того же мира, кроме самой жертвы и наблюдателей. */
    private static List<ServerPlayer> соседи(MinecraftServer сервер, ServerPlayer жертва) {
        List<ServerPlayer> список = new ArrayList<>();
        for (ServerPlayer p : сервер.getPlayerList().getPlayers()) {
            if (p == жертва || !p.isAlive() || p.isSpectator()) continue;
            if (p.level() != жертва.level()) continue;
            список.add(p);
        }
        return список;
    }

    /**
     * Удар телом жертвы. Считает сервер, а не клиент: пробрасывать ЛКМ
     * до клиента значило бы доверять ему выбор цели, а он у нас чужой.
     * Луч тот же, что у ванильного прицела, — {@link ProjectileUtil}.
     */
    private static void ударить(ServerPlayer жертва) {
        double дальность = PlagueConstants.SEIZE_REACH;
        Vec3 глаза = жертва.getEyePosition();
        Vec3 взгляд = жертва.getViewVector(1.0f);
        Vec3 конец = глаза.add(взгляд.scale(дальность));
        AABB коробка = жертва.getBoundingBox().expandTowards(взгляд.scale(дальность)).inflate(1.0);

        EntityHitResult попадание = ProjectileUtil.getEntityHitResult(
            жертва, глаза, конец, коробка,
            e -> e instanceof LivingEntity && e != жертва && e.isAlive() && !e.isSpectator(),
            дальность * дальность);

        жертва.swing(InteractionHand.MAIN_HAND, true);
        if (попадание != null) {
            Entity цель = попадание.getEntity();
            жертва.attack(цель);
            жертва.resetAttackStrengthTicker();
        }
    }

    /** Вернуть админа на место и сказать жертве, что она снова своя. */
    private static void завершить(Сессия с) {
        if (сервер == null) return;

        ServerPlayer жертва = сервер.getPlayerList().getPlayer(с.жертва);
        if (жертва != null) {
            PlagueNetwork.отправитьУправление(жертва,
                new PlagueNetwork.Drive(false, false, 0, 0f, 0f));
        }

        if (с.чума()) return;

        заПультом.remove(с.админ);
        ServerPlayer админ = сервер.getPlayerList().getPlayer(с.админ);
        if (админ == null) return;

        PacketDistributor.sendToPlayer(админ, new PlagueNetwork.Puppet(-1));
        админ.setGameMode(с.прежнийРежим);
        ServerLevel обратно = сервер.getLevel(с.откудаМир);
        if (обратно != null) {
            админ.teleportTo(обратно, с.откуда.x, с.откуда.y, с.откуда.z,
                с.откудаРыскание, с.откудаТангаж);
        }
    }

    // ── предложение админу ─────────────────────────────────────────────

    private static void искатьКогоПредложить(MinecraftServer сервер) {
        for (ServerPlayer жертва : сервер.getPlayerList().getPlayers()) {
            if (сессии.containsKey(жертва.getUUID())) continue;
            if (!жертва.isAlive() || жертва.isSpectator()) continue;

            List<ServerPlayer> рядом = соседи(сервер, жертва);
            double[] x = new double[рядом.size()];
            double[] z = new double[рядом.size()];
            for (int i = 0; i < рядом.size(); i++) {
                x[i] = рядом.get(i).getX();
                z[i] = рядом.get(i).getZ();
            }
            int i = PossessionRules.ближайший(жертва.getX(), жертва.getZ(), x, z,
                PlagueConstants.POSSESS_OFFER_RADIUS);

            if (!PossessionRules.предлагать(
                    PlagueApi.getStage(жертва), PlagueConstants.POSSESS_MIN_STAGE,
                    i >= 0, тик,
                    когдаЗвали.getOrDefault(жертва.getUUID(), Long.MIN_VALUE),
                    PlagueConstants.POSSESS_OFFER_COOLDOWN)) {
                continue;
            }

            когдаЗвали.put(жертва.getUUID(), тик);
            позвать(сервер, жертва, рядом.get(i));
        }
    }

    /**
     * Строка в чат всем ОП с кликабельным куском. Ванильный
     * {@link ClickEvent} — панель мастера игры для этого не нужна.
     */
    private static void позвать(MinecraftServer сервер, ServerPlayer жертва, ServerPlayer сосед) {
        String имя = жертва.getGameProfile().getName();

        Component строка = Component.literal(имя + " сгнил. Рядом "
                + сосед.getGameProfile().getName() + ".  ")
            .withStyle(ChatFormatting.GRAY)
            .copy()
            .append(Component.literal("[ОТДАТЬ ЧУМЕ]").withStyle(стиль -> стиль
                .withColor(ChatFormatting.DARK_PURPLE)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/plague seize " + имя))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Пять секунд чужой воли")))));

        for (ServerPlayer p : сервер.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.sendSystemMessage(строка);
        }
    }

    // ── уборка ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void приВыходе(PlayerEvent.PlayerLoggedOutEvent событие) {
        UUID кто = событие.getEntity().getUUID();
        отпустить(кто);

        UUID ведомый = заПультом.remove(кто);
        if (ведомый != null) отпустить(ведомый);
    }
}
