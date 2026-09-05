package dev.denthe.classes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Грядка бутона чумы — эксклюзив Фермера (спек классов, раздел 6).
 *
 * Обычная ванильная культура во всём, кроме одного: растёт медленнее
 * в {@code farmerBloomGrowthDivisor} раз. Так грядка не обнуляет смысл
 * похода в Гниль — она страховка и хозяйство, а не замена риску.
 * Множитель живёт в конфиге и правится прямо в игре
 * ({@code /lmpcclasses tune}), потому что подобрать его можно только
 * на живой сессии.
 *
 * Замедление сделано отсевом случайных тиков, а не своей формулой
 * роста: ванильная {@code CropBlock#randomTick} уже учитывает
 * освещённость, вспаханную землю, воду и соседние грядки, и
 * переписывать всё это ради одного множителя незачем.
 */
public class PlagueBloomCropBlock extends CropBlock {

    public static final MapCodec<PlagueBloomCropBlock> CODEC = simpleCodec(PlagueBloomCropBlock::new);

    public PlagueBloomCropBlock(Properties свойства) {
        super(свойства);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ClassItems.PLAGUE_BLOOM.get();
    }

    @Override
    protected void randomTick(
            BlockState состояние, ServerLevel уровень, BlockPos позиция, RandomSource случай) {
        int делитель = ClassesConfig.фермерДелительРоста();
        if (делитель > 1 && случай.nextInt(делитель) != 0) return;
        super.randomTick(состояние, уровень, позиция, случай);
    }
}
