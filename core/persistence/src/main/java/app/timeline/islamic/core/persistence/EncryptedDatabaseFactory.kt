package app.timeline.islamic.core.persistence

import android.content.Context
import androidx.room.Room
import app.timeline.islamic.core.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Opening the encrypted Room database is explicit; there is no plaintext fallback. */
object EncryptedDatabaseFactory {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
        instance ?: open(context.applicationContext).also { instance = it }
    }

    internal fun open(context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, DatabaseKeyManager.DATABASE_NAME)
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabaseMigrations.MIGRATION_1_2,
                AppDatabaseMigrations.MIGRATION_2_3,
                AppDatabaseMigrations.MIGRATION_3_4
            )
            .build()
    }
}
