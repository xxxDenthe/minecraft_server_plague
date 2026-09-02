package dev.denthe.plaguecore.core;

/**
 * Параметры одной фазы эпидемии.
 *
 * @param base              базовая вероятность заражения соседнего чанка
 * @param budget            потолок новых заражённых чанков за ночь
 * @param growthEveryNights раз во сколько ночей уровень растёт на месте
 * @param growthAmount      на сколько растёт уровень за раз
 */
public record PhaseParams(float base, int budget, int growthEveryNights, int growthAmount) {}
