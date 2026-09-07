package dev.denthe.classes.client;

import dev.denthe.classes.ClassItems;
import dev.denthe.classes.LmpcClasses;
import dev.denthe.classes.PurifierBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Сцены «размышления» (Ponder) для обоих очистителей — тот показ
 * с анимацией, который в Create открывается клавишей W на предмете.
 *
 * <p><b>Зачем.</b> Очиститель — единственный наш блок, который сам
 * ничего о себе не говорит: он без меню, работает раз за ночь и молча
 * отказывается работать, если чего-то не хватает. Строка состояния
 * по правому клику отвечает уже поставившему; сцена отвечает тому,
 * кто держит блок в руках и не знает, что с ним делать. Для компании,
 * где половина не разбирается в Create, это единственная встроенная
 * инструкция.
 *
 * <p><b>Где живёт постройка.</b> Сцена показывает не выдуманную
 * картинку, а настоящую структуру из
 * {@code assets/lmpc_classes/ponder/*.nbt} — ванильный формат
 * структурного блока. Файлы собираются скриптом
 * {@code tools/ponder_structures.py}: руками двоичный gzip не написать.
 *
 * <p><b>Текст — в языковых файлах, а не здесь.</b> Ponder на показе
 * читает ключ {@code lmpc_classes.ponder.<сцена>.text_N} через
 * {@code I18n}, а строки в коде ниже — только исходник этих ключей
 * и запасной вариант. Поэтому порядок вызовов {@code .text(...)}
 * менять нельзя, не поправив номера в {@code ru_ru.json} и
 * {@code en_us.json}: номер приходит от порядка вызова, а не от текста.
 */
public class PurifierPonderPlugin implements PonderPlugin {

    /** Вызывается только когда Ponder точно есть — см. {@link PonderHook}. */
    public static void подключить() {
        PonderIndex.addPlugin(new PurifierPonderPlugin());
        LmpcClasses.LOG.info("Сцены размышления очистителей подключены к Ponder");
    }

    @Override
    public String getModId() {
        return LmpcClasses.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> помощник) {
        помощник.forComponents(предмет("andesite_purifier"))
            .addStoryBoard("andesite_purifier", PurifierPonderPlugin::андезитовый);
        помощник.forComponents(предмет("brass_purifier"))
            .addStoryBoard("brass_purifier", PurifierPonderPlugin::латунный);
    }

    private static ResourceLocation предмет(String путь) {
        return ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, путь);
    }

    /**
     * Раскрутить кинетику в сцене: Ponder-мир не считает поток воды,
     * поэтому колесо в нём само не завертится. Скорость кладётся прямо
     * в NBT блок-сущности — тем же ключом {@code Speed}, каким её пишет
     * сам Create; так делают и его собственные сцены.
     *
     * Класс сущности намеренно {@code BlockEntity}: ссылаться отсюда
     * на {@code KineticBlockEntity} значило бы завести жёсткую
     * зависимость на Create, а выборка и так содержит только его блоки.
     */
    private static void крутить(SceneBuilder сцена, Selection что, float скорость) {
        сцена.world().modifyBlockEntityNBT(что, BlockEntity.class,
            тег -> тег.putFloat("Speed", скорость));
    }

    /**
     * Андезитовый тир: что это, откуда вращение, куда реагент
     * и что он делает по ночам.
     *
     * Привод в сцене — настоящее водяное колесо в жёлобе, а не
     * творческий мотор: игрок должен увидеть тот механизм, который
     * ему предстоит собрать. Как собирается само колесо, показывает
     * сцена Create, повторять её здесь незачем.
     */
    private static void андезитовый(SceneBuilder сцена, SceneBuildingUtil утиль) {
        сцена.title("andesite_purifier", "Andesite Purifier");
        сцена.configureBasePlate(0, 0, 5);
        сцена.showBasePlate();
        сцена.idle(10);

        BlockPos очиститель = утиль.grid().at(2, 2, 2);
        BlockPos вал = утиль.grid().at(2, 1, 2);
        BlockPos колесо = утиль.grid().at(0, 1, 2);
        Selection привод = утиль.select().fromTo(колесо, вал);
        Vec3 верх = утиль.vector().topOf(очиститель);

        сцена.world().showSection(утиль.select().position(очиститель), Direction.DOWN);
        сцена.idle(10);
        сцена.overlay().showText(70)
            .text("Cleanses plague infection from the land around it")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.world().showSection(привод, Direction.EAST);
        сцена.idle(15);
        крутить(сцена, привод, 8f);
        крутить(сцена, утиль.select().position(очиститель), 8f);
        сцена.overlay().showText(70)
            .text("It runs on rotation only: rotation must come from straight below")
            .pointAt(утиль.vector().centerOf(вал)).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.overlay().showText(60)
            .colored(PonderPalette.MEDIUM)
            .text("One water wheel is enough: a gearbox only stands the shaft upright")
            .pointAt(утиль.vector().centerOf(колесо)).placeNearTarget();
        сцена.idle(70);

        сцена.overlay().showControls(верх, Pointing.DOWN, 40)
            .rightClick().withItem(new ItemStack(ClassItems.CLEANSING_AGENT.get()));
        сцена.overlay().showText(70)
            .text("Right-click with Cleansing Agent to load it. One is spent per night")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.world().modifyBlock(очиститель, с -> с.setValue(PurifierBlock.РАБОТАЕТ, true), false);
        сцена.effects().emitParticles(верх,
            сцена.effects().simpleParticleEmitter(ParticleTypes.SPLASH, new Vec3(0, 0.15, 0)),
            2f, 60);
        сцена.overlay().showText(70)
            .colored(PonderPalette.GREEN)
            .text("Turning and loaded, it hums, sprays and glows")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.overlay().showText(80)
            .text("Once per night it cleanses the 3x3 chunks around it. A Smith in the party makes it stronger")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(90);

        сцена.overlay().showControls(верх, Pointing.DOWN, 30).rightClick();
        сцена.overlay().showText(70)
            .text("Right-click empty-handed for a report: rotation, reagent and Smith tier")
            .pointAt(верх).placeNearTarget();
        сцена.idle(80);
    }

    /**
     * Латунный тир: чем отличается от андезитового, как его разогнать
     * до 32 об/мин и кто его ставит.
     *
     * Привод показан целиком: водяное колесо даёт восемь оборотов,
     * контроллер скорости под большой шестернёй поднимает их до
     * тридцати двух. Очиститель стоит на шестерне сверху: с 0.15.0 вал
     * подключается только снизу, и шестерня с осью Y под блоком —
     * единственный вид привода, который ему годится.
     */
    private static void латунный(SceneBuilder сцена, SceneBuildingUtil утиль) {
        сцена.title("brass_purifier", "Brass Purifier");
        сцена.configureBasePlate(0, 0, 5);
        сцена.scaleSceneView(0.8f);   // башня в четыре блока, иначе не влезает
        сцена.showBasePlate();
        сцена.idle(10);

        BlockPos очиститель = утиль.grid().at(2, 3, 2);
        BlockPos шестерня = утиль.grid().at(2, 2, 2);
        BlockPos контроллер = утиль.grid().at(2, 1, 2);
        BlockPos вал = утиль.grid().at(1, 1, 2);
        BlockPos колесо = утиль.grid().at(0, 1, 2);
        Selection привод = утиль.select().fromTo(колесо, вал);
        Selection разгон = утиль.select().fromTo(контроллер, шестерня);
        Vec3 верх = утиль.vector().topOf(очиститель);

        сцена.world().showSection(утиль.select().position(очиститель), Direction.DOWN);
        сцена.idle(10);
        сцена.overlay().showText(70)
            .text("The second tier of purifier. Same job, much larger reach")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.overlay().showText(70)
            .colored(PonderPalette.GREEN)
            .text("It cleanses 7x7 chunks around itself instead of 3x3")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.world().showSection(привод, Direction.EAST);
        сцена.idle(15);
        крутить(сцена, привод, 8f);
        сцена.overlay().showText(70)
            .colored(PonderPalette.FAST)
            .text("It needs 32 RPM: a lone water wheel gives only 8")
            .pointAt(утиль.vector().centerOf(колесо)).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.world().showSection(разгон, Direction.DOWN);
        сцена.idle(15);
        сцена.world().modifyBlockEntityNBT(утиль.select().position(контроллер),
            BlockEntity.class, тег -> тег.putFloat("TargetSpeed", 32f));
        крутить(сцена, разгон, 32f);
        крутить(сцена, утиль.select().position(очиститель), 32f);
        сцена.overlay().showText(80)
            .colored(PonderPalette.FAST)
            .text("A Rotation Speed Controller under a large cogwheel sets exactly the speed you ask for")
            .pointAt(утиль.vector().centerOf(контроллер)).placeNearTarget().attachKeyFrame();
        сцена.idle(90);

        сцена.overlay().showText(70)
            .text("The purifier takes rotation from the cogwheel below: the shaft connects from below and nowhere else")
            .pointAt(утиль.vector().centerOf(шестерня)).placeNearTarget();
        сцена.idle(80);

        сцена.world().modifyBlock(очиститель, с -> с.setValue(PurifierBlock.РАБОТАЕТ, true), false);
        сцена.effects().emitParticles(верх,
            сцена.effects().simpleParticleEmitter(ParticleTypes.SPLASH, new Vec3(0, 0.15, 0)),
            3f, 60);
        сцена.overlay().showText(70)
            .text("It eats four reagents per night instead of one")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(80);

        сцена.overlay().showText(80)
            .colored(PonderPalette.RED)
            .text("Only a Smith can place it. In other hands the block will not go down")
            .pointAt(верх).placeNearTarget().attachKeyFrame();
        сцена.idle(90);

        сцена.overlay().showText(70)
            .text("Assembled on Mechanical Crafters, not on a belt like the andesite one")
            .pointAt(верх).placeNearTarget();
        сцена.idle(80);
    }
}
