package dev.denthe.plaguecore.mc;

/**
 * Номера видений. Общие для сервера и клиента, поэтому лежат в `mc`,
 * а не в клиентском пакете: тянуть клиентский класс в код, который
 * выполняется на выделенном сервере, нельзя.
 */
public final class DreadKinds {
    private DreadKinds() {}

    /** Свет гаснет на пару секунд — будто факел задуло. */
    public static final int ТЕМНОТА = 0;

    /** Своё сердцебиение в ушах. */
    public static final int СЕРДЦЕ = 1;

    /** Человек на краю зрения, которого через секунду нет. */
    public static final int СИЛУЭТ = 2;

    /** Сколько всего видов. */
    public static final int ВСЕГО = 3;
}
