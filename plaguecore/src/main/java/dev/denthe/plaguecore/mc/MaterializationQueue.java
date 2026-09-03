package dev.denthe.plaguecore.mc;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Кто ждёт перерисовки и на чём мы остановились. Дизайн материализации, 4.3.
 *
 * Очередь — не оптимизация, а требование: ChunkEvent.Load приходит раньше,
 * чем чанк дошёл до статуса FULL, и трогать мир прямо в обработчике —
 * верный способ повесить загрузку. Обработчик только ставит номер в очередь,
 * работает серверный тик.
 *
 * Хранятся индексы ячеек PlagueGrid, а не координаты: индекс — это то, чем
 * сетка адресуется, и он влезает в int.
 *
 * Курсор относится к голове очереди: не доделанный за тик чанк остаётся
 * первым и продолжает с того же столбца.
 *
 * Класс не потокобезопасен — и не должен быть: вся материализация идёт
 * в главном потоке (решение 3.2 дизайна).
 */
public final class MaterializationQueue {

    private final int потолок;
    private final ArrayDeque<Integer> порядок = new ArrayDeque<>();
    private final Set<Integer> вОчереди = new HashSet<>();
    private int курсор = 0;

    public MaterializationQueue(int потолок) {
        if (потолок <= 0) throw new IllegalArgumentException("потолок должен быть положительным");
        this.потолок = потолок;
    }

    /**
     * Поставить чанк в очередь. Возвращает false, если он там уже есть или
     * очередь полна: в обоих случаях делать ничего не надо, отвергнутый
     * чанк всё равно приедет при следующей загрузке.
     */
    public boolean enqueue(int gridIndex) {
        if (вОчереди.contains(gridIndex)) return false;
        if (порядок.size() >= потолок) return false;
        порядок.addLast(gridIndex);
        вОчереди.add(gridIndex);
        return true;
    }

    public boolean contains(int gridIndex) { return вОчереди.contains(gridIndex); }

    public int size() { return порядок.size(); }

    public boolean isEmpty() { return порядок.isEmpty(); }

    /** Индекс чанка, над которым работаем сейчас, или -1, если очередь пуста. */
    public int head() {
        Integer h = порядок.peekFirst();
        return h == null ? -1 : h;
    }

    /** На каком столбце головного чанка остановились в прошлый тик. */
    public int cursor() { return курсор; }

    public void setCursor(int value) { this.курсор = Math.max(0, value); }

    /** Голова доделана: убрать её и начать следующий чанк с начала. */
    public void finishHead() {
        Integer h = порядок.pollFirst();
        if (h != null) вОчереди.remove(h);
        курсор = 0;
    }

    public void clear() {
        порядок.clear();
        вОчереди.clear();
        курсор = 0;
    }
}
