package ru.ny.nextappprediction.util

/**
 * Утилиты для работы со временем
 */
object TimeUtils {

    /**
     * Определяет временной слот по часу
     * @param hour час (0-23)
     * @return временной слот:
     *   0 - ночь (0-5)
     *   1 - утро (6-11)
     *   2 - день (12-17)
     *   3 - вечер (18-23)
     */
    fun getTimeSlot(hour: Int): Int {
        return when (hour) {
            in 0..5 -> 0    // ночь
            in 6..11 -> 1   // утро
            in 12..17 -> 2  // день
            in 18..23 -> 3  // вечер
            else -> 0
        }
    }
}
