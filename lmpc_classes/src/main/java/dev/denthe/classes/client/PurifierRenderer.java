package dev.denthe.classes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.denthe.classes.PurifierBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Крутящийся вал в нижнем порту очистителя.
 *
 * Сама модель блока по-прежнему статическая: формат Java-моделей кадров
 * не знает, и вращать геометрию может только код. Угол мы не считаем
 * сами — его отдаёт Create ({@code getAngleForBe}), тот же, по которому
 * крутятся его собственные машины. Поэтому вал очистителя идёт в фазе
 * с валом, который в него воткнут, а не живёт своей жизнью.
 *
 * Рисуется обычным ванильным {@code BlockRenderDispatcher}, а не через
 * Flywheel: нам нужен один короткий огрызок вала, и ради него тащить
 * инстансинг Create незачем.
 */
public class PurifierRenderer implements BlockEntityRenderer<PurifierBlockEntity> {

    /** На сколько вал торчит наружу, в долях блока. Один пиксель. */
    private static final float ВЫСТУП = 1f / 16f;

    /**
     * Длина огрызка от полного вала. Полный занял бы весь блок и вылез бы
     * кольцом вокруг сопла: сопло всего 4 пикселя в ширину, а вал шесть.
     */
    private static final float ДЛИНА = 0.30f;

    /**
     * Поджатие по горизонтали. Под очистителем обычно стоит настоящий вал
     * той же толщины 6 × 6; без поджатия их боковые грани совпали бы
     * и замигали (z-fighting). Три сотых не видно глазом.
     */
    private static final float ПОДЖАТИЕ = 0.97f;

    public PurifierRenderer(BlockEntityRendererProvider.Context контекст) {
    }

    @Override
    public void render(PurifierBlockEntity очиститель, float частичныйТик, PoseStack поза,
                       MultiBufferSource буферы, int свет, int оверлей) {
        BlockState вал = KineticBlockEntityRenderer.shaft(Direction.Axis.Y);
        float угол = KineticBlockEntityRenderer.getAngleForBe(
            очиститель, очиститель.getBlockPos(), Direction.Axis.Y);

        поза.pushPose();
        поза.translate(0.5f, -ВЫСТУП, 0.5f);
        поза.mulPose(Axis.YP.rotationDegrees(угол));
        поза.scale(ПОДЖАТИЕ, ДЛИНА, ПОДЖАТИЕ);
        поза.translate(-0.5f, 0f, -0.5f);
        Minecraft.getInstance().getBlockRenderer()
            .renderSingleBlock(вал, поза, буферы, свет, оверлей);
        поза.popPose();
    }
}
