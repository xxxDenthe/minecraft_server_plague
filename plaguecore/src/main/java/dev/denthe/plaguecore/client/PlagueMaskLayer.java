package dev.denthe.plaguecore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.CuriosBridge;
import dev.denthe.plaguecore.mc.PlagueBlocks;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Тряпичная повязка на лице игрока.
 *
 * Curios хранит вещь в слоте, но сам ничего не рисует: рисовальщика надо
 * писать руками. Вместо API Curios здесь свой слой поверх модели игрока —
 * ровно так же, как ванильная «шапка» второго слоя скина. Причина
 * в границах подсистемы: `plaguecore` обязан грузиться без Curios
 * (см. {@link CuriosBridge}), а рисовальщик Curios потянул бы его API
 * в сборку. Мост на отражении уже умеет отвечать «надета ли» — этого
 * слою хватает, новых зависимостей ноль.
 *
 * Геометрия — один куб головы, раздутый на {@link #РАЗДУТИЕ}. Число
 * взято больше ванильных 0.5 у шапки скина: будь оно равным, повязка
 * дралась бы с шапкой за глубину и мигала, а будь меньше — прятались
 * бы под непрозрачной шапкой.
 *
 * Развёртка та же, что у головы обычного скина 64 × 64, угол (0, 0).
 * Художнику это значит: рисовать в шаблоне скина по области **головы**,
 * всё остальное оставить прозрачным.
 */
public final class PlagueMaskLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ModelLayerLocation СЛОЙ = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "plague_mask"), "main");

    private static final ResourceLocation ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/plague_mask.png");

    /** Насколько куб повязки больше головы. Шапка скина — 0.5, мы поверх неё. */
    private static final float РАЗДУТИЕ = 0.6F;

    private final ModelPart маска;

    public PlagueMaskLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> родитель,
            EntityModelSet модели) {
        super(родитель);
        this.маска = модели.bakeLayer(СЛОЙ).getChild("mask");
    }

    public static LayerDefinition создатьСлой() {
        MeshDefinition сетка = new MeshDefinition();
        сетка.getRoot().addOrReplaceChild("mask",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(РАЗДУТИЕ)),
            PartPose.ZERO);
        return LayerDefinition.create(сетка, 64, 64);
    }

    @Override
    public void render(PoseStack поза, MultiBufferSource буфер, int свет,
                       AbstractClientPlayer игрок,
                       float шагНоги, float скоростьНог, float частичныйТик,
                       float времяЖизни, float поворотГоловыY, float поворотГоловыX) {
        if (игрок.isSpectator() || игрок.isInvisible()) return;

        ModelPart голова = getParentModel().head;
        if (!голова.visible) return;

        if (CuriosBridge.надето(игрок, PlagueBlocks.PLAGUE_MASK.get()).isEmpty()) return;

        // Своей анимации у повязки нет и не надо: она повторяет голову
        // один в один. Позу берём готовую, а не считаем заново.
        маска.copyFrom(голова);
        маска.render(поза, буфер.getBuffer(RenderType.entityCutoutNoCull(ТЕКСТУРА)),
                     свет, OverlayTexture.NO_OVERLAY);
    }
}
