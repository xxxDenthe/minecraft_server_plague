package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.CipherWords;
import dev.denthe.plaguecore.core.InfectionMath;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Точка входа для подсистемы классов. Спек ядра, раздел 9.6.
 *
 * Ядро отдаёт наружу только интерфейс. Сами лекарства, рецепты и
 * способности — подсистема 3. Улучшенный отвар Клирика будет предметом,
 * который дёргает {@link #cure} и {@link #grantImmunity}, а не ещё одной
 * копией логики.
 *
 * В отличие от обычного отвара, {@link #cure} работает на любой стадии:
 * ограничение по стадии — свойство предмета, а не лечения как такового.
 */
public final class PlagueApi {
    private PlagueApi() {}

    /** Снять с игрока заражённость. Отрицательное значение игнорируется. */
    public static void cure(ServerPlayer игрок, float очков) {
        if (очков <= 0f) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        PlayerInfection.задать(игрок, д.заражённость - очков);
    }

    /**
     * Иммунитет на N тиков: набор заражённости и кашель соседей
     * не действуют. Уже действующий иммунитет не укорачивается.
     */
    public static void grantImmunity(ServerPlayer игрок, int тиков) {
        if (тиков <= 0) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.иммунитетДо = Math.max(д.иммунитетДо, игрок.level().getGameTime() + тиков);
    }

    public static int getStage(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).стадия;
    }

    public static float getInfection(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).заражённость;
    }

    /** Сколько HP игрок потерял навсегда за смерти от чумы. */
    public static float getPermanentLoss(ServerPlayer игрок) {
        return InfectionMath.постоянныйШтраф(PlayerPlagueData.данные(игрок).смертей);
    }

    // ------------------------------------------------------------------
    // Чанки: чтение и очистка. Добавлено для подсистемы 3 (классы),
    // 2026-09-05, с разрешения владельца — заметка
    // docs/superpowers/notes/2026-09-05-ochistitel-i-api-chankov.md.
    //
    // Раньше наружу отдавалось только состояние игрока, и всё, что
    // касается местности, приходилось бы читать через внутренности
    // PlagueState. Очиститель Кузнеца (спек ядра 10.1) и снимок
    // Летописца (спек классов 7) — оба про чанки, поэтому интерфейс
    // расширен здесь, а не продублирован рефлексией на приватные поля.
    // ------------------------------------------------------------------

    /** Уровень заражения чанка 0..5; {@code -1}, если чанк вне сетки мира. */
    public static int getChunkLevel(ServerLevel уровень, int чанкX, int чанкZ) {
        PlagueGrid сетка = PlagueState.get(уровень).grid();
        return сетка.contains(чанкX, чанкZ) ? сетка.getLevel(чанкX, чанкZ) : -1;
    }

    /** То же по координатам блока — вызывающему не надо помнить про сдвиг на 4. */
    public static int getChunkLevelAt(ServerLevel уровень, BlockPos позиция) {
        return getChunkLevel(уровень, SectionPos.blockToSectionCoord(позиция.getX()),
            SectionPos.blockToSectionCoord(позиция.getZ()));
    }

    /** Сопротивление чанка повторному заражению, 0.0..1.0; {@code -1}, если чанк вне сетки. */
    public static float getChunkResistance(ServerLevel уровень, int чанкX, int чанкZ) {
        PlagueGrid сетка = PlagueState.get(уровень).grid();
        return сетка.contains(чанкX, чанкZ) ? сетка.getResistance(чанкX, чанкZ) : -1f;
    }

    /** Номер прошедшей ночи. Постройкам, работающим «раз за ночь», больше ничего не нужно. */
    public static int getNight(ServerLevel уровень) {
        return PlagueState.get(уровень).night();
    }

    /**
     * Один ночной проход очистителя по одному чанку — спек ядра 10.1.
     * Поднимает сопротивление и с вероятностью {@code сила} снимает
     * один уровень заражения.
     *
     * Здесь же живёт правило 10.2, которое не даёт окопаться: если
     * у чанка есть сосед с уровнем 3 и выше, очиститель держит его
     * на единице, но ниже не опускает. Правило намеренно стоит в ядре,
     * а не в самом блоке: оно про баланс эпидемии, и любой будущий
     * очиститель — латунный, электрический, чей угодно ещё — должен
     * подчиняться ему, не переписывая условие у себя.
     *
     * @return true, если уровень чанка действительно снизился
     */
    public static boolean cleanseChunk(
            ServerLevel уровень, int чанкX, int чанкZ, float сила, float приростСопротивления) {
        PlagueState состояние = PlagueState.get(уровень);
        PlagueGrid сетка = состояние.grid();
        if (!сетка.contains(чанкX, чанкZ)) return false;

        int индекс = сетка.index(чанкX, чанкZ);
        if (приростСопротивления > 0f) {
            сетка.setResistance(чанкX, чанкZ,
                Math.min(1f, сетка.getResistance(чанкX, чанкZ) + приростСопротивления));
        }

        boolean снизился = false;
        int текущий = сетка.getLevelAt(индекс);
        if (текущий > 0 && уровень.getRandom().nextFloat() < сила) {
            int пол = сетка.maxLevelAround(индекс) >= 3 ? 1 : 0;
            if (текущий > пол) {
                сетка.setLevelAt(индекс, текущий - 1);
                снизился = true;
            }
        }

        // Блоки на поверхности догонят сетку сами: материализация ленивая
        // и сравнивает уровень с уже применённым при загрузке чанка.
        состояние.setDirty();
        return снизился;
    }

    /**
     * Крючок под подсистему 5 «Лор»: снимок Летописца как будущий
     * лор-артефакт (спек классов, раздел 7). Пока только пишет в лог —
     * разведывательный эффект снимка целиком живёт в `lmpc_classes`,
     * а привязка к записям лора появится, когда появится сам лор.
     */
    public static void recordSnapshot(ServerPlayer игрок, BlockPos позиция) {
        PlagueCore.LOG.debug("Снимок Летописца: {} в {}", игрок.getGameProfile().getName(), позиция);
    }

    // ── тайнопись ──────────────────────────────────────────────────────

    /**
     * Раскрыть тайное слово помимо чата. Сюда придёт клин бессонных,
     * когда подсистема лора его сделает: клин — это кнопка «сдаюсь»,
     * а не второй способ читать.
     *
     * @return true, если слово раньше было закрыто
     */
    public static boolean revealWord(ServerPlayer игрок, String корень) {
        ServerLevel мир = игрок.server.getLevel(Level.OVERWORLD);
        if (мир == null) return false;
        boolean новое = PlagueState.get(мир).раскрыть(CipherWords.нормализовать(корень));
        if (новое) PlagueWords.синхронизироватьВсех(игрок.server);
        return новое;
    }

    public static boolean isWordRevealed(ServerPlayer игрок, String корень) {
        ServerLevel мир = игрок.server.getLevel(Level.OVERWORLD);
        return мир != null && PlagueState.get(мир).раскрыт(CipherWords.нормализовать(корень));
    }

    /**
     * Переслать игроку словарь тайнописи. Звать при смене класса:
     * подсказки видит только Летописец, и решает это сервер.
     */
    public static void refreshWords(ServerPlayer игрок) {
        PlagueWords.синхронизировать(игрок);
    }
}
