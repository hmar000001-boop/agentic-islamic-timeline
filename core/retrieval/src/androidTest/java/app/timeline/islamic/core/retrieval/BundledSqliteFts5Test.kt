package app.timeline.islamic.core.retrieval

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSqliteFts5Test {
    @Test fun bundledDriverHasFts5AndArabicUnicodeSearch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "fts5-${System.nanoTime()}.sqlite")
        try {
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                connection.execSQL("CREATE VIRTUAL TABLE sample USING fts5(passage_id UNINDEXED, body, tokenize='unicode61 remove_diacritics 2')")
                connection.prepare("INSERT INTO sample(passage_id, body) VALUES (?, ?)").use { insert ->
                    insert.bindText(1, "quran-1-1")
                    // Packs index the application's normalized text; unicode61 does not fold Arabic harakat.
                    insert.bindText(2, "الحمد لله رب العالمين")
                    insert.step()
                }
                connection.prepare("SELECT passage_id FROM sample WHERE sample MATCH ? LIMIT 1").use { query ->
                    query.bindText(1, "\"الحمد\"")
                    assertTrue(query.step())
                    assertEquals("quran-1-1", query.getText(0))
                }
            }
        } finally {
            file.delete()
            File(file.parentFile, file.name + "-wal").delete()
            File(file.parentFile, file.name + "-shm").delete()
        }
    }
}
