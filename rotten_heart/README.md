# Rotten Heart — гнилое сердце (GeckoLib)

Большая органическая модель сердца с корнями-заражением. Бьётся. Разбита на куски,
которые можно прятать по одному — для постепенного разрушения.

---

## 1. Что где лежит

```
rotten_heart/
├── assets/
│   ├── geo/rotten_heart.geo.json              ← геометрия
│   ├── animations/rotten_heart.animation.json ← анимация биения
│   └── textures/entity/rotten_heart.png       ← атлас текстур 128x64
├── source/
│   ├── rotten_heart_geckolib.bbmodel          ← исходник для правок (текущий)
│   └── rotten_heart_static.bbmodel            ← старая версия: статичный Java block
└── README.md
```

Скопировать в мод так (замени `<modid>` на свой):

| Отсюда | Куда |
|---|---|
| `assets/geo/rotten_heart.geo.json` | `src/main/resources/assets/<modid>/geo/` |
| `assets/animations/rotten_heart.animation.json` | `src/main/resources/assets/<modid>/animations/` |
| `assets/textures/entity/rotten_heart.png` | `src/main/resources/assets/<modid>/textures/entity/` |

---

## 2. Характеристики модели

| Параметр | Значение |
|---|---|
| Идентификатор геометрии | `geometry.rotten_heart` |
| Имя анимации | `heartbeat` |
| Длина анимации | 1.2 сек, зациклена (`loop`) |
| Кубов | 297 |
| Костей | 23 (3 родительских + 20 разрушаемых) |
| Текстура | 128x64, один атлас |
| Габариты | примерно 2.6 x 2.9 x 2.3 блока |

Анимация — настоящий сердечный ритм «туп-туп»: сильный удар, слабый удар, пауза.
Ветки сверху пульсируют с задержкой, корни — ещё позже. Волна расходится наружу.

---

## 3. Кости

Три родительских кости держат анимацию. Трогать их для разрушения **не надо** —
если спрятать родителя, исчезнет вся треть модели разом.

```
heart_body   ← на ней анимация масштаба (биение)
arteries     ← анимация масштаба + качание
roots        ← анимация масштаба + качание
```

Двадцать дочерних костей — это и есть разрушаемые куски. Все примерно одного размера.

| Кость | Кубов | Что это |
|---|---|---|
| `body_p1` … `body_p8` | по 13 (p8 — 14) | тело сердца, 8 равных долек |
| `arteries_p1` … `arteries_p4` | по 11–12 | ветки/артерии сверху |
| `roots_p1` … `roots_p8` | по 18 (p8 — 19) | корни-заражение |

Куски нарезаны по медиане (kd-разбиение), поэтому каждый отвечает за свой участок
пространства, а не за случайные кубы. Пропадание одного читается как «отвалился кусок».

---

## 4. Разрушение — как кодить

Идея простая: **прячем кости**. GeckoLib умеет `bone.setHidden(true)` на лету,
каждый кадр. Модель не надо перегружать, ничего не надо пересобирать.

Ниже — GeckoLib 4.x (MC 1.20+). Для 3.x отличия внизу.

### 4.1. Сущность: хранить, что уже отвалилось

20 кусков влезают в один `int` как битовая маска. Синхронизируем через
`SynchedEntityData`, чтобы клиент знал, что рисовать.

```java
public class RottenHeartEntity extends PathfinderMob implements GeoEntity {

    /** Порядок важен: индекс в массиве = номер бита в маске. */
    public static final String[] PIECES = {
        // сначала осыпаются корни
        "roots_p1", "roots_p2", "roots_p3", "roots_p4",
        "roots_p5", "roots_p6", "roots_p7", "roots_p8",
        // потом обламываются ветки
        "arteries_p1", "arteries_p2", "arteries_p3", "arteries_p4",
        // и только потом разваливается тело
        "body_p1", "body_p2", "body_p3", "body_p4",
        "body_p5", "body_p6", "body_p7", "body_p8",
    };

    private static final EntityDataAccessor<Integer> BROKEN =
        SynchedEntityData.defineId(RottenHeartEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache =
        GeckoLibUtil.createInstanceCache(this);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BROKEN, 0);
    }

    public int getBrokenMask() {
        return this.entityData.get(BROKEN);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BrokenMask", getBrokenMask());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(BROKEN, tag.getInt("BrokenMask"));
    }
}
```

### 4.2. Считать урон → отваливать куски

Сколько кусков должно отсутствовать, считаем прямо из здоровья.
Это надёжнее, чем считать удары: работает и после загрузки мира, и после лечения.

```java
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            updateBrokenPieces();
        }
        return hit;
    }

    private void updateBrokenPieces() {
        // 1.0 = целое, 0.0 = вот-вот умрёт
        float ratio = this.getHealth() / this.getMaxHealth();

        // при полном здоровье 0 кусков, при нуле — все 20
        int shouldBeGone = Math.round((1.0F - ratio) * PIECES.length);
        shouldBeGone = Mth.clamp(shouldBeGone, 0, PIECES.length);

        int mask = 0;
        for (int i = 0; i < shouldBeGone; i++) {
            mask |= (1 << i);
        }

        int old = getBrokenMask();
        if (mask != old) {
            this.entityData.set(BROKEN, mask);
            spawnDebris(old, mask);
        }
    }
```

### 4.3. Частицы в месте отлома

Чтобы кусок не исчезал молча.

```java
    private void spawnDebris(int oldMask, int newMask) {
        if (!(this.level() instanceof ServerLevel server)) return;

        int justBroke = Integer.bitCount(newMask) - Integer.bitCount(oldMask);
        if (justBroke <= 0) return;

        server.sendParticles(
            ParticleTypes.ASH,
            this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(),
            20 * justBroke,          // количество
            0.8, 0.8, 0.8,           // разброс
            0.02                     // скорость
        );
        server.playSound(null, this.blockPosition(),
            SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
    }
```

### 4.4. Рендерер: собственно прятать кости

Вот здесь и происходит всё волшебство. `preRender` вызывается каждый кадр
до отрисовки — самое место, чтобы выставить видимость.

```java
public class RottenHeartRenderer extends GeoEntityRenderer<RottenHeartEntity> {

    public RottenHeartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RottenHeartModel());
    }

    @Override
    public void preRender(PoseStack poseStack, RottenHeartEntity entity,
                          BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight,
                          int packedOverlay, float red, float green,
                          float blue, float alpha) {

        int mask = entity.getBrokenMask();

        for (int i = 0; i < RottenHeartEntity.PIECES.length; i++) {
            boolean hidden = (mask & (1 << i)) != 0;
            model.getBone(RottenHeartEntity.PIECES[i])
                 .ifPresent(bone -> bone.setHidden(hidden));
        }

        super.preRender(poseStack, entity, model, bufferSource, buffer,
                        isReRender, partialTick, packedLight,
                        packedOverlay, red, green, blue, alpha);
    }
}
```

> **Важно.** `setHidden` надо выставлять **каждый кадр, и для скрытых, и для видимых**.
> `BakedGeoModel` переиспользуется между всеми сущностями этого типа. Если выставить
> флаг только один раз, соседнее сердце унаследует чужие дырки.

### 4.5. Модель

```java
public class RottenHeartModel extends GeoModel<RottenHeartEntity> {

    @Override
    public ResourceLocation getModelResource(RottenHeartEntity e) {
        return new ResourceLocation(MODID, "geo/rotten_heart.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RottenHeartEntity e) {
        return new ResourceLocation(MODID, "textures/entity/rotten_heart.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RottenHeartEntity e) {
        return new ResourceLocation(MODID, "animations/rotten_heart.animation.json");
    }
}
```

### 4.6. Контроллер анимации

```java
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "beat", 0, state ->
            state.setAndContinue(
                RawAnimation.begin().thenLoop("heartbeat"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
```

---

## 5. Приятные мелочи

**Сердце слабеет.** Замедляй биение по мере урона — очень читаемо:

```java
    // внутри контроллера
    controller.setAnimationSpeed(0.35 + 0.65 * (getHealth() / getMaxHealth()));
```

**Трещины.** Сделай второй PNG с более разбитой текстурой и переключай:

```java
    @Override
    public ResourceLocation getTextureResource(RottenHeartEntity e) {
        boolean hurt = e.getHealth() < e.getMaxHealth() * 0.4F;
        return new ResourceLocation(MODID,
            hurt ? "textures/entity/rotten_heart_cracked.png"
                 : "textures/entity/rotten_heart.png");
    }
```

**Хитбокс.** Модель крупная, поставь под неё коробку:

```java
    EntityType.Builder.of(RottenHeartEntity::new, MobCategory.MONSTER)
        .sized(2.6F, 2.9F)
        .clientTrackingRange(10);
```

---

## 6. GeckoLib 3.x — что отличается

Если сидишь на старой версии:

| GeckoLib 4.x | GeckoLib 3.x |
|---|---|
| `GeoEntity` | `IAnimatable` |
| `model.getBone(name)` → `Optional<GeoBone>` | `getAnimationProcessor().getBone(name)` → `IBone` |
| `bone.setHidden(true)` | `bone.setHidden(true)` (так же) |
| `preRender(...)` | `render(...)` перед `super` |
| `RawAnimation.begin().thenLoop("x")` | `new AnimationBuilder().addAnimation("x", true)` |

Смысл тот же, меняются только имена.

---

## 7. Если надо править саму модель

Открывай `source/rotten_heart_geckolib.bbmodel` в Blockbench.
Формат проекта — **Bedrock Entity** (не пугайся названия, для Java-модов через
GeckoLib используется именно он).

**Box UV выключен**, стоит per-face UV — так работает атлас из семи разных текстур
на одной картинке. Не включай Box UV обратно, иначе развёртка слетит.

Раскладка атласа 128x64, плитки по 32x32:

| Позиция | Плитка |
|---|---|
| (0, 0) | `flesh_1_necrotic` — самая тёмная плоть, много трещин |
| (32, 0) | `flesh_2_rot` — гниль, красно-бурые пятна |
| (64, 0) | `flesh_3_mould` — зеленоватая плесень |
| (96, 0) | `flesh_4_dry` — сухая, светлее, волокна |
| (0, 32) | `flesh_5_scar` — самая светлая, бледные прожилки |
| (32, 32) | `root_dark` — корни, почти чёрные |
| (64, 32) | `root_bark` — корни, чуть коричневее |
| (96, 32) | пусто — можно занять |

Плитки распределены по модели не случайно: низ сердца темнее и гнилее, верх светлее,
переходы плавные. Если перерисовываешь — держи ту же палитру (тёплый серый),
иначе цельность рассыпется.

После правок: `File → Export → Bedrock Geometry` для `.geo.json`
и `Animation → Export Animations` для `.animation.json`.
