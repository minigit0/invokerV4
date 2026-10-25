package com.invokermod;

/** Общие данные о сферах и заклинаниях (используются и клиентом, и сервером). */
public final class Spells {
    public static final int FIRE = 0, WATER = 1, AIR = 2;

    public static final int FIREBALL = 0, WIND = 1, STEAM = 2, WALL = 3, RAIN = 4, DASH = 5;

    public static final String[] NAMES = {
            "Фаербол", "Заряд ветра", "Паровой залп", "Водяная стена", "Дождь", "Рывок"
    };

    /** Кулдауны в тиках (20 тиков = 1 сек). */
    public static final int[] COOLDOWNS = {400, 400, 600, 600, 600, 800};

    public static final int BOOK_COOLDOWN = 40;
    public static final int SUPER_BOOK_COOLDOWN = 10;

    private Spells() {}

    /** Индекс заклинания по двум сферам (порядок не важен) или -1. */
    public static int index(int a, int b) {
        if (a < 0 || b < 0) return -1;
        int lo = Math.min(a, b), hi = Math.max(a, b);
        if (lo == FIRE && hi == FIRE) return FIREBALL;
        if (lo == FIRE && hi == AIR) return WIND;
        if (lo == FIRE && hi == WATER) return STEAM;
        if (lo == WATER && hi == WATER) return WALL;
        if (lo == WATER && hi == AIR) return RAIN;
        if (lo == AIR && hi == AIR) return DASH;
        return -1;
    }
}
