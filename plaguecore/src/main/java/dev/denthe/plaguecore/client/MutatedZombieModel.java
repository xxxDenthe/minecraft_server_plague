package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Геометрия мутировавшего зомби: ванильный гуманоид плюс семь наростов.
 *
 * Своего класса модели нет — рендерер берёт ванильный {@code ZombieModel},
 * он находит части по именам head/body/right_arm/… и ничего не знает о наших
 * добавках. Наросты навешены дочерними частями, а {@code ModelPart} рисует
 * своих детей вместе с собой, поэтому они едут с рукой и головой сами,
 * и анимация зомби их подхватывает бесплатно.
 *
 * <h3>Откуда числа</h3>
 * Наросты нарисованы в Blockbench (textures_src/ROT_ZOMBIE/mutated_zombie.bbmodel),
 * а там ось Y смотрит ВВЕРХ и отсчёт идёт от пола. В Java ось Y смотрит ВНИЗ
 * и отсчёт идёт от опорной точки части. Перевод:
 *
 * <pre>
 *   java_y = мировой_Y_опоры − мировой_Y_блокбенча
 *   поворот вокруг X и Z меняет знак, вокруг Y — нет
 * </pre>
 *
 * Опорные точки ванильного гуманоида в мировых координатах:
 * голова и тело — 24, руки — 22, ноги — 12. Отсюда, например, горб на плече
 * (в Blockbench Y 21..26) даёт java_y от 24−26 = −2 на пять пикселей вниз.
 *
 * UV-смещения — это {@code uv_offset} тех же кубов, прочитанные из проекта.
 * Они лежат в пустых зонах зомбиного атласа (второй слой, Y 32..48), поэтому
 * текстура осталась 64×64 и ни один нарост не налез на ванильную часть.
 * Проверку на пересечения делает генератор texgen/zombie.py.
 */
public final class MutatedZombieModel {
    private MutatedZombieModel() {}

    public static final ModelLayerLocation СЛОЙ = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "mutated_zombie"), "main");

    /** Градусы в радианы — читать наклон в градусах куда понятнее. */
    private static float гр(double градусы) {
        return (float) Math.toRadians(градусы);
    }

    public static LayerDefinition создатьСлой() {
        MeshDefinition сетка = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition корень = сетка.getRoot();
        PartDefinition голова = корень.getChild("head");
        PartDefinition тело = корень.getChild("body");
        PartDefinition леваяРука = корень.getChild("left_arm");
        PartDefinition праваяНога = корень.getChild("right_leg");

        // Горб на правом плече: самый крупный нарост, читается с любой стороны.
        тело.addOrReplaceChild("shoulder_hump", CubeListBuilder.create()
            .texOffs(0, 32).addBox(-8.0F, -2.0F, 1.0F, 5.0F, 5.0F, 5.0F),
            PartPose.ZERO);

        // Два шипа вдоль хребта. Наклонены назад-вверх, поэтому у каждого
        // своя опорная точка — без неё поворот пошёл бы вокруг центра тела.
        тело.addOrReplaceChild("spine_spike_upper", CubeListBuilder.create()
            .texOffs(20, 32).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 3.0F, гр(34), 0.0F, 0.0F));

        тело.addOrReplaceChild("spine_spike_lower", CubeListBuilder.create()
            .texOffs(32, 32).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 4.0F),
            PartPose.offsetAndRotation(2.0F, 10.0F, 3.0F, гр(26), 0.0F, гр(-14)));

        // Вскрытая опухоль на груди: плоская накладка в один пиксель толщиной.
        тело.addOrReplaceChild("chest_tumor", CubeListBuilder.create()
            .texOffs(32, 41).addBox(-4.0F, 5.0F, -3.0F, 5.0F, 4.0F, 1.0F),
            PartPose.ZERO);

        // Рог на черепе.
        голова.addOrReplaceChild("skull_horn", CubeListBuilder.create()
            .texOffs(44, 32).addBox(-1.0F, -3.0F, -1.0F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(2.0F, -6.0F, 2.0F, гр(-12), 0.0F, гр(14)));

        // Вздутие на левом предплечье — едет с рукой при замахе.
        леваяРука.addOrReplaceChild("arm_swelling", CubeListBuilder.create()
            .texOffs(20, 41).addBox(-1.0F, 6.0F, -2.0F, 3.0F, 3.0F, 3.0F),
            PartPose.ZERO);

        // Нарост на правом бедре.
        праваяНога.addOrReplaceChild("thigh_growth", CubeListBuilder.create()
            .texOffs(56, 32).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(-2.1F, 3.0F, 2.0F, 0.0F, 0.0F, гр(-18)));

        return LayerDefinition.create(сетка, 64, 64);
    }
}
