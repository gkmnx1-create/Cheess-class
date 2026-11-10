package com.chessclass.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt
import java.util.Base64

/**
 * First artwork-template matcher for the supplied marble chess set.
 * Returns null when the closest match is not sufficiently clear.
 */
object PieceTemplateClassifier {
    private const val SIZE = 16
    private val templates: Map<String, ByteArray> = mapOf(
        "sK" to decode("lZWUkpGRkpSTj46RlJWVlZubnJuZnJCMjJKZlZibnJudnZ2cnpB/f4B9mJyXl5ycnZ2dnZt9fmdte4Oempeam52dnJ6afHpjZnd/oJubnZydnZ+anFhnfXxUbZ6ZnJydnZ6SkZWIj2pzi4+Vj5Ocm56YkYBddIVOTX5uXomPmJqfj4VkNz5ESkM/QDZ0gpScnJlrgkBHRERFRkVJh2qem5iieHxlQ0ZFRUY9aniAoJmYmphnmmVJREVGQn9qmZibnJmhcpWOWEhHP1+He5+Xm5ycmY5wYlpPR0JMbpCbm5ubnZV6aWNnaWprZmx7mJqam52WVFJVU1NTU1VQXpybmQ=="),
        "sV" to decode("eHh3eHh4eXl4eXh4eHh4eYOCgYKDhYOBgoGGhISEhISDg4SGhn5+o6qFeoeHhoWFg4N/eX6Iq4x/u46BfoKFhYSFdKpyk+7Oxe+pbaZ4hYSEiW23tZK9wa/DnK69b4eEhIh1jaDecpmNdcepj3WJhISIdZZwl11XWVCAapl0iYSEhnyKjj9ORkdKOn2ReIeEhIWDfKVBSkpJSz6ThX2FhISEh3K0S0VKSUpCo3qChIWEhIlru6pLRkhITqdyh4OEhISJcLXviVlOQl65coiEhYSFgZGMbHJiUUVHh5aAhoWEhn6Mj32BhIeKhIyRe4eFhIWEUU1SUFBQT1BOTX+EhA=="),
        "sF" to decode("jI2NjY2NjI6OjY2NjY2NjJSUlJWUlJWQi5OVlJWUlJSTlpaWlpaUc2mEl5WVlZWVkZSVlpWZiWpfe5GWlZWVlZGRlJaXlIF2THqMlpWVlZWTk5SUmIx7TlONf5SWlZWVlJOUk5aCZj17QWqPl5WVlZaVlZSVdlkxNzNmh5iUlZSWlpWUmnN3RSxXcIeXlZWVlZaWlZeFdU8xU32Jk5SVlJWVlpaUg0AsOSxnjJWUlZOVlZWYjYhfNjYxVIaUlJSVlZWVk3+khUo5Mz+EhpWTk5SVlIttWFRJPjQvU32TlZSVlJd8ZVFWV1laWldviZiVlZSaXz1IRUVFRERGPYGZlA=="),
        "sR" to decode("hoeUk5OVlZWVlZWVk5OTk3x8mpqclZCRkpCSj5mamZl+f5ubnoZ4hXiEe36RnZqZk5mcm5yGVGxRblJvkJuZmpacm5uchFxaT1tOcY+empqVm5ubm4R5S0dGRXCOnpqblZubnJqCWERJSUZtjZ6ampWbm5yaf1VFSElFa4uem5uVm5ycmX5TRUhJRGiKnpublZubnJh9UkZISURmiZ6bm5Wbm5yXektGSElDZIidm5yVm5udlYFlR0hJRF+GnpublZubnoiVl1pKSUFghpecm5WbnJuFaF1VTUdESniTnZuVmp6TeGZlZ2lpa2Zyg5yblZqejlFXV1ZWVVVXUGafmg=="),
        "sP" to decode("nbqKhYaGhoaIiIaGhoaGhpXXioaGhoiEfH6IhoaHh4aVuYGIhoh/i6uifoaGhoaHf4eGhoiBlMVwj7p9h4aGhnuGhoaJes1cJyWioX6HhoZ7hoaGjHDRiUIzr5t7iIaGeoaGhoxvlJ1POp1thYaGhnqFhoaIe7JaMDCajYSGhoZ6hYaGh4DFST4uja17iIaGe4aGhoGVpzBBNV69e4iGhnuGh4h7qpgyPjw+uoOGhoZ7hoeJdt3VXkFCLqCneoiGe4aIgpmIbFdEOTBEp4uFhnuGiXyee3h+gYKGeZyOgYd7hop6RE9PTk1NTE9FVYqGe4aGh4aDgoKCg4OChIeGhg=="),
        "bK" to decode("cXFxcXF0bmJndHFxcXFxcXFxcXFxb1aha2FzcXFxcXFvcXFzdWSfwbNxbnVxcXFxcHF0amVUZrJ8UmBncXJwcHFzZXecoXipd4yeh2VucHBzaXnBwb7Bv8HBwcGiX3JwdWCev3RUjMGiWmSyvWRvcnVgmLZabGjBf2RdmLBccnFzaXLBb2duwYFlZLOHXnRxcXRbrapaa8CCVJO1YGxzcXF0ZnXBvb7BwbvBg1x0cHFxcHVaj6impaWnkFlvc3JycXNsbo+VlJWUlY1uZ3Nub3F1X6XBwcHBwcHBqWVxl4dxc2hkamhpaWlpaWNbf7WRbGxtZmRkZGRkZGRmam+Kew=="),
        "bV" to decode("kZGQkY50iZGWgHyXkpKSk5GQj4tfX06MeWJYZ5eSkZKRlJV2a65XbF2knUWVkZeSkFtLaE6QWnlfhmtRa1dtl2Z1lkxibphdXaVjYnKuVntnba5iVHOuSGiuZk9/lEV4kVZGq1NarpefrkNmeDJjj5KfWoWtkK6urqyKn01ulI6Rl39drq6urq6urn1Fg46QkpKUUp+tra6trppdWJSQkZGRmWR6rq6urq2KSHyWkZGRkZSITpucnJ6KX06aj5KUkZGTZVNjYmBfWUdBdJqHf4+XdGuurq6urq6ukkd9YnCQlX1KVVRVVVVUV05Ea1Zti4uMenJyc3NycnJwf4VrdA=="),
        "bF" to decode("cXFxcXBxb2Jib3FwcXFxcXFxcXBxcGaYmWVwcXFxcXFxcXFwc2WHwcGPY3RwcXFxcXFxcHVhgL++d2d0cHFxcXFxcXJsabHBglFhbXJwcXBxcXFyYKvBnk17q2NxcXFwcXBzZYHBvnBXrsF/ZHRwcXFwdGGlwLFgbMDAnl90cHFxcXFltsGmU3DBwalhcXFxcXFwaLrBr3WNwMGoZG9xcXFxcWO4wcHBwcHBn2NwcXFxcXRfnLm3t7e5rYhgc3FxcXB0YWuIhYaGh4FmXXNtbnFybHHBwcHBwcHBtXFli5BwcW1jdnR0dHR0dXBfZ6SQbGxtZV1fX19fX19fZGl/hw=="),
        "bR" to decode("dKB0bWNhamVhaGlgZHBwcGiZeWGFnFh3rWRjoGZrcXBpe3Vhkq5Le79gXbJsanFwaW1zYY6/qbW/rrC2aWpxb2lvcmdtsLe1srevj1xucG9ob3ByYGCJiId3cVRocW9waXBxcml7v7+/pJ5obnBwcGlwcHNkhb68v6icbGpxcHBpcHB0YZK/vb+xnnJncnBwaXBwdGCdvr2+uaF5ZHJwcGlwcHJjr7+/v7+uhGNzb3BpcHByW5ikoqKjlXNbdHBwaXByZnuVk5SUlJSPbWZxbGlwcWO5v7+/v7+/v5lcf5VpcHJkYGRjY2NjY2NcXZizZWtsbGZlZmZmZmZmaGmBjA=="),
        "bN" to decode("fX19fX18fHx9fHx8e3p7fIuLi4uLioqKgIuKiouJhoWNjY2NjY2NiLF+joyNjIyJjIyNjYyOhpyYqHyPjIuLi4uLjIyPhKePQI6nhI2Ni4uLi4uLi5CpbUgxYaeIi46MjIyKjISgmWg2RTVdq42MjY2Ni42JpY9WNUFCIIWPho6MjY2IlpWLQjlGu4KldY+MjY2OhrmSgDVDPZqAineSjI2MkX2D3GI4RDWAcEmLjYyMjI2NSKhjNEA4YKqLj4yNjY2NjYvvwmdIQD2uj4mNjY2NjYquem5fTUA2Wq+NjI2NjI6Con6BhYiKjYKhh42NjYyScT5NS0pJSEhMPmqSjA=="),
        "bB" to decode("cXFxcXF0bmJndHFxcXFxcXFxcXFxb1aha2FzcXFxcXFvcXFzdWSfwbNxbnVxcXFxcHF0amVUZrJ8UmBncXJwcHFzZXecoXipd4yeh2VucHBzaXnBwb7Bv8HBwcGiX3JwdWCev3RUjMGiWmSyvWRvcnVgmLZabGjBf2RdmLBccnFzaXLBb2duwYFlZLOHXnRxcXRbrapaa8CCVJO1YGxzcXF0ZnXBvb7BwbvBg1x0cHFxcHVaj6impaWnkFlvc3JycXNsbo+VlJWUlY1uZ3Nub3F1X6XBwcHBwcHBqWVxl4dxc2hkamhpaWlpaWNbf7WRbGxtZmRkZGRkZGRmam+Kew=="),
        "bP" to decode("a1GJi4mIipORlZCKjIuLi2BPi4uIi4lcTlF5koqLi4tydoqJioZCcJeBNGySiouLgYyJh5FRd62trY0yioyLi36HiYiLRqSsrKynP3GRiot+iIiIjkCiraytpTR0kIqLfYiJiZVZSamqqVkxj4uLi36Ii4uQW0mfrZ1KPI+Li4t9iYuMijJ7nZageR99joqLfomLiZJnKpt0dSVUlYmKi3+Ki4yQR4GtqZVoOYqMiYt/iYyMQ3Otqaytq2UuhIyJf4iUXXetra2tra2tYEqSiH6HlUpNZGJkZGRjZTwxkIh/iY56TU9PT05NTU5OboyJf4qLjpaWlpSUlJWUlo+Kiw==")
    )

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    fun classify(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): String? {
        val w = x1 - x0
        val h = y1 - y0
        if (w < 12 || h < 12) return null
        val values = FloatArray(SIZE * SIZE)
        var sum = 0f
        var index = 0
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val px = x0 + (w * (0.06f + 0.88f * (x + 0.5f) / SIZE)).toInt()
            val py = y0 + (h * (0.06f + 0.88f * (y + 0.5f) / SIZE)).toInt()
            val c = bitmap.getPixel(px.coerceIn(0, bitmap.width - 1), py.coerceIn(0, bitmap.height - 1))
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000f
            values[index++] = lum
            sum += lum
        }
        val mean = sum / values.size
        var variance = 0f
        for (v in values) variance += (v - mean) * (v - mean)
        val sd = sqrt(variance / values.size).coerceAtLeast(1f)
        val normalized = ByteArray(values.size) { i ->
            (((values[i] - mean) / sd * 32f + 128f).toInt().coerceIn(0, 255)).toByte()
        }
        var bestName: String? = null
        var best = Double.MAX_VALUE
        var second = Double.MAX_VALUE
        for ((name, template) in templates) {
            var error = 0.0
            for (i in normalized.indices) {
                error += abs((normalized[i].toInt() and 255) - (template[i].toInt() and 255))
            }
            error /= normalized.size
            if (error < best) { second = best; best = error; bestName = name }
            else if (error < second) second = error
        }
        // Conservative gate: avoid calling an empty/unknown square a piece.
        return if (bestName != null && best < 34.0 && second - best > 1.5) bestName else null
    }
}