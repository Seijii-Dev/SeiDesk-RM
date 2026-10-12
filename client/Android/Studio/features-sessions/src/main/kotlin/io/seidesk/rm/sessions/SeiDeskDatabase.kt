package io.seidesk.rm.sessions

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

typealias XyDeskDatabase = SeiDeskDatabase

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = false)
abstract class SeiDeskDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val NAME = "seidesk.db"

        @Volatile
        private var instance: SeiDeskDatabase? = null

        fun get(context: Context): SeiDeskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeiDeskDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
