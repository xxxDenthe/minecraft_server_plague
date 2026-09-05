package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueConfig;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.VoiceKnobs;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.SpreadEngine;
import dev.denthe.plaguecore.core.StartGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Админские команды. Спек, раздел 12.1.
 * В этом плане реализована часть, не требующая материализации блоков.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueCommands {
    private PlagueCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> корень = Commands.literal("plague")
            .requires(s -> s.hasPermission(2));

        корень.then(Commands.literal("gui").executes(PlagueCommands::экран));

        корень.then(Commands.literal("info").executes(PlagueCommands::info));

        корень.then(Commands.literal("spawn")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(PlagueCommands::выводок)));

        корень.then(Commands.literal("center")
            .executes(PlagueCommands::показатьЦентр)
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(ctx -> переместитьЦентр(ctx, false))
                .then(Commands.literal("force")
                    .executes(ctx -> переместитьЦентр(ctx, true)))));

        // Тайнопись: предохранитель мастера игры. Если команда завязла
        // и слово не угадывается — открыть руками, сессия важнее загадки.
        корень.then(Commands.literal("word")
            .then(Commands.literal("list").executes(PlagueCommands::словаСписок))
            .then(Commands.literal("reveal")
                .then(Commands.argument("корень", StringArgumentType.greedyString())
                    .executes(c -> словоПереключить(c, true))))
            .then(Commands.literal("hide")
                .then(Commands.argument("корень", StringArgumentType.greedyString())
                    .executes(c -> словоПереключить(c, false)))));

        корень.then(Commands.literal("night").executes(PlagueCommands::ночь));

        корень.then(Commands.literal("fastforward")
            .then(Commands.argument("nights", IntegerArgumentType.integer(1, 500))
                .executes(PlagueCommands::прогнать)));

        корень.then(Commands.literal("setphase")
            .then(Commands.argument("phase", IntegerArgumentType.integer(0, 4))
                .executes(PlagueCommands::установитьФазу)));

        корень.then(Commands.literal("pause").executes(c -> пауза(c.getSource(), true)));
        корень.then(Commands.literal("resume").executes(c -> пауза(c.getSource(), false)));

        корень.then(Commands.literal("generate")
            .then(Commands.argument("percent", FloatArgumentType.floatArg(0.01f, 1.0f))
                .executes(c -> сгенерировать(c, null))
                .then(Commands.argument("epicenters", IntegerArgumentType.integer(1, 200))
                    .executes(c -> сгенерировать(c, IntegerArgumentType.getInteger(c, "epicenters"))))));

        корень.then(Commands.literal("seed")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, true))));

        корень.then(Commands.literal("setlevel")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .then(Commands.argument("level",
                        IntegerArgumentType.integer(0, PlagueConstants.MAX_LEVEL))
                    .executes(PlagueCommands::выставитьУровень))));

        корень.then(Commands.literal("render")
            .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                .executes(PlagueCommands::перерисовать))
            .executes(c -> перерисоватьВокруг(c, 4)));

        корень.then(Commands.literal("rendercave")
            .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                .executes(PlagueCommands::перерисоватьПещеры))
            .executes(c -> перерисоватьПещерыВокруг(c, 2)));

        корень.then(Commands.literal("redraw")
            .then(Commands.argument("radius", IntegerArgumentType.integer(0, 63))
                .executes(PlagueCommands::перерисоватьЗаново))
            .executes(c -> перерисоватьЗановоВокруг(c, 4)));

        корень.then(Commands.literal("remove")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, false))));

        корень.then(Commands.literal("player")
            .then(Commands.argument("who", EntityArgument.player())
                .executes(PlagueCommands::показатьИгрока)
                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                    .executes(PlagueCommands::выставитьИгроку))));

        LiteralArgumentBuilder<CommandSourceStack> голос = Commands.literal("voice")
            .executes(PlagueCommands::показатьГолос);
        голос.then(Commands.literal("sync").executes(PlagueCommands::синхронизироватьГолос));
        голос.then(Commands.literal("set")
            .then(Commands.argument("knob", StringArgumentType.word())
                .suggests((c, b) -> {
                    for (VoiceKnobs.Ручка р : VoiceKnobs.ВСЕ) b.suggest(р.id());
                    return b.buildFuture();
                })
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes(PlagueCommands::выставитьГолос))));
        корень.then(голос);

        event.getDispatcher().register(корень);
    }

    // ── голос больного ────────────────────────────────────────────────
    // Ручек дюжина, и подбираются они только слухом: покрутил — послушал.
    // Поэтому команда не только правит живые числа, но и пишет их в файл:
    // иначе подобранное за вечер пропадёт при первом перезапуске.

    private static int показатьГолос(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.literal("=== Голос больного ==="), false);
        for (VoiceKnobs.Ручка р : VoiceKnobs.ВСЕ) {
            String уровень = р.уровень() < 0 ? "общее"
                : "стадия " + (PlagueConstants.VOICE_MIN_STAGE + р.уровень())
                  + (р.уровень() == PlagueConstants.VOICE_LEVELS - 1 ? "+" : "");
            String строка = String.format(java.util.Locale.ROOT, "  %-11s %7.3f   %s (%s)",
                р.id(), VoiceKnobs.прочитать(р.id()), р.подпись(), уровень);
            s.sendSuccess(() -> Component.literal(строка), false);
        }
        s.sendSuccess(() -> Component.literal(
            "Менять: /plague voice set <ручка> <число>. Пишется в конфиг сразу."), false);
        синхронизироватьГолос(ctx);
        return VoiceKnobs.ВСЕ.length;
    }

    /**
     * Молча отдать ползункам панели GM то, что стоит на сервере.
     * Без этого они показывали бы конфиг клиента, а голос считается
     * на сервере — и цифры разошлись бы.
     */
    private static int синхронизироватьГолос(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer кому = ctx.getSource().getPlayer();
        if (кому != null) PlagueNetwork.отправитьГолос(кому);
        return 1;
    }

    private static int выставитьГолос(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "knob");
        double значение = DoubleArgumentType.getDouble(ctx, "value");
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        if (р == null) {
            ctx.getSource().sendFailure(Component.literal("Нет такой ручки: " + id));
            return 0;
        }
        if (!PlagueConfig.выставитьГолос(id, значение)) {
            ctx.getSource().sendFailure(Component.literal("Не удалось записать " + id));
            return 0;
        }
        double стало = VoiceKnobs.прочитать(id);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            java.util.Locale.ROOT, "%s = %.3f (%s)", id, стало, р.подпись())), true);
        синхронизироватьГолос(ctx);
        return 1;
    }

    private static ServerLevel мир(CommandSourceStack src) {
        return src.getServer().getLevel(Level.OVERWORLD);
    }

    /** Открыть админский экран. Работает только для игрока — из консоли нечего показывать. */
    private static int экран(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlagueNetwork.отправитьСнимок(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        PlagueGrid g = st.grid();

        int[] поУровням = new int[6];
        for (int cz = g.originZ(); cz < g.originZ() + g.size(); cz++) {
            for (int cx = g.originX(); cx < g.originX() + g.size(); cx++) {
                поУровням[g.getLevel(cx, cz)]++;
            }
        }

        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.literal("=== Состояние чумы ==="), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Ночь %d, фаза %d%s", st.night(), st.phase(),
                st.isPaused() ? " (ПАУЗА)" : "")), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Заражено: %.1f%% (%d из %d чанков)",
                g.infectedFraction() * 100f, g.countInfected(), g.cellCount())), false);
        for (int lvl = 1; lvl <= 5; lvl++) {
            final int l = lvl, n = поУровням[lvl];
            if (n > 0) s.sendSuccess(() -> Component.literal("  уровень " + l + ": " + n), false);
        }
        s.sendSuccess(() -> Component.literal("Очагов: " + st.epicenters().size()), false);
        s.sendSuccess(() -> Component.literal(
            "Местность размечена: " + (st.isTerrainInitialized() ? "да" : "нет")), false);

        int отстаёт = 0;
        int отстаётПодЗемлёй = 0;
        for (int i = 0; i < g.cellCount(); i++) {
            if (g.getLevelAt(i) > g.getAppliedSurfaceAt(i)) отстаёт++;
            if (g.getLevelAt(i) > g.getAppliedUndergroundAt(i)) отстаётПодЗемлёй++;
        }
        final int ждёт = отстаёт;
        final int ждётПодЗемлёй = отстаётПодЗемлёй;
        s.sendSuccess(() -> Component.literal(
            String.format("Не отрисовано чанков: %d, в очереди сейчас: %d",
                ждёт, Materializer.длинаОчереди())), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Под землёй не отрисовано: %d, в очереди сейчас: %d",
                ждётПодЗемлёй, CaveMaterializer.длинаОчереди())), false);
        return 1;
    }

    /**
     * Выпустить ночной выводок у мешка немедленно, без броска кубика.
     *
     * Иначе проверить его на живом сервере нельзя: 30% за ночь означает,
     * что мастер игры будет стоять в поле по три ночи, гадая, сломано
     * оно или просто не повезло. Потолок кучек на игрока команда
     * соблюдает — иначе она проверяла бы не то, что работает в игре.
     */
    private static int выводок(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel уровень = мир(ctx.getSource());
        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        BlockPos место = new BlockPos(pos.x(),
            уровень.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x(), pos.z()),
            pos.z());

        if (SporeSpawner.высыпать(уровень, место, уровень.getRandom())) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Выводок вылез у " + место.getX() + ", " + место.getY() + ", " + место.getZ()), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(
            "Выводок не вылез. Причины: рядом нет игрока (дальше "
            + "128 блоков), игрок ближе " + PlagueConstants.SPAWN_MIN_PLAYER_DISTANCE
            + " блоков, потолок кучек за ночь исчерпан, или вокруг нет места"));
        return 0;
    }

    /** Где сейчас центр мира по мнению чумы. */
    private static int показатьЦентр(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        PlagueState st = PlagueState.get(мир(ctx.getSource()));
        PlagueGrid g = st.grid();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Центр чумы: чанк %d, %d (блок %d, %d). Сетка %d×%d, углы чанков %d,%d..%d,%d",
            st.центрЧанкX(), st.центрЧанкZ(),
            st.центрЧанкX() * 16 + 8, st.центрЧанкZ() * 16 + 8,
            g.size(), g.size(),
            g.originX(), g.originZ(),
            g.originX() + g.size() - 1, g.originZ() + g.size() - 1)), false);
        return 1;
    }

    /**
     * Перенести центр мира: сетка чумы, граница мира и точка возрождения
     * встают вокруг указанной точки.
     *
     * Три вещи ставятся одной командой намеренно. Разъехавшись хоть на
     * чанк, они дают заражение за границей и спавн в углу карты — а
     * заметно это станет только в игре, на живых людях.
     *
     * Сетка при переносе обнуляется, поэтому команда требует слова
     * {@code force}, если чума уже посеяна.
     */
    private static int переместитьЦентр(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean силой) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        if (!силой && st.grid().countInfected() > 0) {
            ctx.getSource().sendFailure(Component.literal(String.format(
                "Чума уже посеяна: %d заражённых чанков. Перенос центра сотрёт их "
                + "вместе с очагами и разметкой местности. Если точно надо — "
                + "допишите force в конец команды.", st.grid().countInfected())));
            return 0;
        }

        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        int цx = pos.x() >> 4;
        int цz = pos.z() >> 4;
        st.переместитьЦентр(цx, цz);

        // Середина центрального чанка: так сетка и граница мира соосны.
        double блокX = цx * 16 + 8;
        double блокZ = цz * 16 + 8;

        WorldBorder граница = level.getWorldBorder();
        граница.setCenter(блокX, блокZ);
        граница.setSize(PlagueConstants.WORLD_SIZE_BLOCKS);

        BlockPos спавн = new BlockPos((int) блокX,
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) блокX, (int) блокZ),
            (int) блокZ);
        level.setDefaultSpawnPos(спавн, 0f);

        int размечено = TerrainInitializer.initialize(level, st);

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Центр мира: чанк %d, %d (блок %d, %d, спавн на высоте %d).%n"
            + "Граница мира %d блоков, местность размечена (%d чанков), сетка чумы пуста.%n"
            + "Дальше: /plague generate 0.05",
            цx, цz, (int) блокX, (int) блокZ, спавн.getY(),
            PlagueConstants.WORLD_SIZE_BLOCKS, размечено)), true);
        return 1;
    }

    /**
     * Выставить уровень заражения одному чанку. Нужно для живой проверки
     * материализации: без этого уровень 2 не получить иначе как ждать,
     * пока эпидемия сама доползёт до нужной стадии.
     */
    private static int выставитьУровень(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        int уровень = IntegerArgumentType.getInteger(ctx, "level");

        PlagueState st = PlagueState.get(мир(ctx.getSource()));
        if (!st.grid().contains(cx, cz)) {
            ctx.getSource().sendFailure(Component.literal(
                "Чанк " + cx + ", " + cz + " вне сетки мира"));
            return 0;
        }
        st.grid().setLevel(cx, cz, уровень);
        st.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Чанк " + cx + ", " + cz + ": уровень " + уровень), true);
        return 1;
    }

    /**
     * Поставить чанки вокруг вызывающего в очередь на перерисовку.
     * Нужно, чтобы не ждать ночного тика при живой проверке.
     */
    private static int перерисовать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return перерисоватьВокруг(ctx, IntegerArgumentType.getInteger(ctx, "radius"));
    }

    private static int перерисоватьВокруг(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int радиус) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        net.minecraft.world.level.ChunkPos центр =
            new net.minecraft.world.level.ChunkPos(
                net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));

        int поставлено = 0;
        for (int dz = -радиус; dz <= радиус; dz++) {
            for (int dx = -радиус; dx <= радиус; dx++) {
                if (Materializer.поставить(st, центр.x + dx, центр.z + dz)) поставлено++;
            }
        }
        final int n = поставлено;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "В очередь на перерисовку поставлено чанков: " + n), true);
        return n;
    }

    /**
     * То же для подземелья. Отдельная команда, потому что подземный проход
     * сам по себе запускается только под живым игроком: без неё стадию
     * пещер иначе как спуском в шахту не проверить.
     */
    private static int перерисоватьПещеры(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return перерисоватьПещерыВокруг(ctx, IntegerArgumentType.getInteger(ctx, "radius"));
    }

    private static int перерисоватьПещерыВокруг(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int радиус) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        net.minecraft.world.level.ChunkPos центр =
            new net.minecraft.world.level.ChunkPos(
                net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));

        int поставлено = 0;
        for (int dz = -радиус; dz <= радиус; dz++) {
            for (int dx = -радиус; dx <= радиус; dx++) {
                if (CaveMaterializer.поставить(st, центр.x + dx, центр.z + dz)) поставлено++;
            }
        }
        final int n = поставлено;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "В очередь на подземную перерисовку поставлено чанков: " + n), true);
        return n;
    }

    /**
     * Забыть, что чанки уже отрисованы, и нарисовать их заново.
     *
     * Материализация помнит, до какого уровня чанк доведён, и второй раз
     * его не трогает. Это правильно в игре и мешает при разработке: стоит
     * поменять правило — и в старом мире оно не применится никогда, потому
     * что чанки давно «готовы». Команда сбрасывает эту память.
     *
     * Уже поставленные блоки чумы не откатываются: проход только добавляет.
     * Для чистого мира нужен новый мир.
     */
    private static int перерисоватьЗаново(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return перерисоватьЗановоВокруг(ctx, IntegerArgumentType.getInteger(ctx, "radius"));
    }

    private static int перерисоватьЗановоВокруг(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int радиус) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        PlagueGrid g = st.grid();
        net.minecraft.world.level.ChunkPos центр =
            new net.minecraft.world.level.ChunkPos(
                net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));

        int сброшено = 0;
        for (int dz = -радиус; dz <= радиус; dz++) {
            for (int dx = -радиус; dx <= радиус; dx++) {
                int cx = центр.x + dx, cz = центр.z + dz;
                int i = g.index(cx, cz);
                if (i < 0) continue;
                g.setAppliedSurfaceAt(i, 0);
                g.setAppliedUndergroundAt(i, 0);
                сброшено++;
                // Незагруженные из очереди тут же вылетят, но вернутся сами
                // при загрузке: память о них уже сброшена.
                Materializer.поставить(st, cx, cz);
                CaveMaterializer.поставить(st, cx, cz);
            }
        }
        st.setDirty();

        final int n = сброшено;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Память об отрисовке сброшена у чанков: " + n
            + ". В очереди сейчас: " + Materializer.длинаОчереди()), true);
        return n;
    }

    private static int ночь(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        st.advanceNight();
        SpreadEngine.NightResult r = NightHook.runNight(level, st, false);
        st.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Ночь %d: заражено %d, выросло %d, зажило %d",
                st.night(), r.newlyInfected(), r.grown(), r.scarsHealed())), true);
        return 1;
    }

    private static int прогнать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int ночей = IntegerArgumentType.getInteger(ctx, "nights");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        long t0 = System.nanoTime();
        int всего = 0;
        for (int i = 0; i < ночей; i++) {
            st.advanceNight();
            всего += NightHook.runNight(level, st, false).newlyInfected();
        }
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        // Ночи прогнали в уме, а мир об этом не знает. Обычный ночной тик
        // догоняет загруженные чанки сам; здесь его не было, и без этой
        // строки перемотка меняла только числа, но не блоки.
        Materializer.поставитьЗагруженные(level, st);

        final int итог = всего;
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Прогнано %d ночей за %d мс. Заражено %d чанков, теперь %.1f%%",
                ночей, мс, итог, st.grid().infectedFraction() * 100f)), true);
        return 1;
    }

    private static int установитьФазу(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int фаза = IntegerArgumentType.getInteger(ctx, "phase");
        PlagueState st = PlagueState.get(мир(ctx.getSource()));
        int[] перваяНочьФазы = { 1, 6, 13, 21, 31 };
        st.setNight(перваяНочьФазы[фаза]);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Фаза " + фаза + ", ночь " + st.night()), true);
        return 1;
    }

    private static int пауза(CommandSourceStack src, boolean значение) {
        PlagueState st = PlagueState.get(src.getServer().getLevel(Level.OVERWORLD));
        st.setPaused(значение);
        src.sendSuccess(() -> Component.literal(
            значение ? "Чума поставлена на паузу" : "Чума продолжается"), true);
        return 1;
    }

    /**
     * @param сколькоОчагов сколько очагов разбросать, или null — тогда берутся
     *                      уже посаженные вручную, а если их нет, разбрасывается
     *                      значение по умолчанию
     */
    private static int сгенерировать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                     Integer сколькоОчагов) {
        float доля = FloatArgumentType.getFloat(ctx, "percent");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        RandomGenerator rng = RandomGeneratorFactory.of("Xoshiro256PlusPlus")
            .create(level.getSeed());

        // Число очагов задаёт темп сильнее любого другого числа: заражение
        // растёт по краю пятна, поэтому один очаг ползёт втрое медленнее,
        // чем та же площадь, разбитая на десятки мелких.
        long[] очаги = st.epicentersArray();
        if (сколькоОчагов != null || очаги.length == 0) {
            int сколько = сколькоОчагов != null ? сколькоОчагов : PlagueConstants.START_EPICENTERS;
            очаги = StartGenerator.scatterEpicenters(st.grid(), сколько, rng);
            for (long p : очаги) st.addEpicenter(p);
        }

        // Сетка сейчас будет переписана целиком, а в очередях лежат индексы
        // и курсор от старой картины мира. Их надо забыть, иначе первый же
        // тик дорисует чанк до уровня, которого у него больше нет.
        Materializer.сброситьОчередь();
        CaveMaterializer.сброситьОчередь();

        long t0 = System.nanoTime();
        StartGenerator.GenerationResult r =
            StartGenerator.generate(st.grid(), доля, очаги, rng);
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Сгенерировано за %d мс: %.1f%% мира, %d ночей симуляции, очагов %d",
                мс, r.achievedFraction() * 100f, r.nightsSimulated(), r.epicenterCount())), true);
        return 1;
    }

    private static int очаг(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                            boolean добавить) {
        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        PlagueState st = PlagueState.get(мир(ctx.getSource()));

        if (!st.grid().contains(cx, cz)) {
            ctx.getSource().sendFailure(Component.literal(
                "Чанк " + cx + ", " + cz + " вне сетки мира"));
            return 0;
        }

        long packed = StartGenerator.packChunk(cx, cz);
        if (добавить) {
            st.addEpicenter(packed);
            st.grid().setLevel(cx, cz, 3);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг посажен в чанке " + cx + ", " + cz), true);
        } else {
            st.removeEpicenter(packed);
            st.grid().setLevel(cx, cz, 0);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг убран из чанка " + cx + ", " + cz), true);
        }
        return 1;
    }

    /** Показать заражённость игрока. Спек подсистемы 2, раздел 9 ядра. */
    private static int показатьИгрока(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerPlagueData д = PlayerPlagueData.данные(кто);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
            "%s: заражённость %.1f, стадия %d, смертей от чумы %d",
            кто.getGameProfile().getName(), д.заражённость, д.стадия, д.смертей)), false);
        return 1;
    }

    /** Выставить заражённость. Главный инструмент проверки подсистемы. */
    private static int выставитьИгроку(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        float значение = FloatArgumentType.getFloat(c, "value");
        PlayerInfection.задать(кто, значение);
        PlayerPlagueData д = PlayerPlagueData.данные(кто);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
            "%s: заражённость %.1f, стадия %d",
            кто.getGameProfile().getName(), д.заражённость, д.стадия)), true);
        return 1;
    }

    // ── тайнопись ──────────────────────────────────────────────────────

    private static int словаСписок(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        if (PlagueWords.корни().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                "Словарь тайнописи пуст: нет файлов в data/*/tainopis/"));
            return 0;
        }

        StringBuilder сб = new StringBuilder("Тайнопись:");
        int закрыто = 0;
        for (String к : PlagueWords.корни()) {
            boolean открыт = st.раскрыт(к);
            if (!открыт) закрыто++;
            сб.append("\n  ").append(открыт ? "[+] " : "[ ] ").append(к)
              .append(" — ").append(PlagueWords.тайна(к).слово());
        }
        сб.append("\nЗакрыто: ").append(закрыто).append(" из ").append(PlagueWords.корни().size());

        String текст = сб.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(текст), false);
        return 1;
    }

    private static int словоПереключить(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean раскрыть) {
        String корень = dev.denthe.plaguecore.core.CipherWords.нормализовать(
            StringArgumentType.getString(ctx, "корень").trim());

        if (!PlagueWords.корни().contains(корень)) {
            ctx.getSource().sendFailure(Component.literal(
                "Нет такого корня: " + корень + ". Список — /plague word list"));
            return 0;
        }

        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        boolean изменилось = раскрыть ? st.раскрыть(корень) : st.спрятать(корень);

        if (изменилось) PlagueWords.синхронизироватьВсех(ctx.getSource().getServer());

        String итог = изменилось
            ? (раскрыть ? "Раскрыто: " : "Спрятано обратно: ") + корень
            : "Уже " + (раскрыть ? "раскрыто" : "спрятано") + ": " + корень;
        ctx.getSource().sendSuccess(() -> Component.literal(итог), true);
        return 1;
    }
}
