package com.mygithub.lab.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ===== 实体 =====

@Entity(tableName = "repo_cache")
data class RepoCacheEntity(
    @PrimaryKey val full_name: String,  // owner/repo
    val json: String,                   // 序列化的 GitHubRepo JSON
    val cached_at: Long,
    val category: String = "personal"   // personal | starred | trending
)

@Entity(tableName = "star_tags")
data class StarTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repo_full_name: String,
    val tag: String,
    val note: String = ""               // 本地私有备注
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val type: String = "repo",          // repo | issue
    val searched_at: Long
)

@Entity(tableName = "recent_views")
data class RecentViewEntity(
    @PrimaryKey val repo_full_name: String,
    val owner: String,
    val name: String,
    val description: String,
    val language: String,
    val stars: Int,
    val viewed_at: Long
)

// ===== DAO =====

@Dao
interface RepoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(repos: List<RepoCacheEntity>)

    @Query("SELECT * FROM repo_cache WHERE category = :category ORDER BY cached_at DESC")
    suspend fun getByCategory(category: String): List<RepoCacheEntity>

    @Query("SELECT * FROM repo_cache WHERE full_name = :fullName")
    suspend fun get(fullName: String): RepoCacheEntity?

    @Query("DELETE FROM repo_cache WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("SELECT COUNT(*) FROM repo_cache")
    suspend fun count(): Int
}

@Dao
interface StarTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: StarTagEntity): Long

    @Query("SELECT * FROM star_tags WHERE repo_full_name = :repoFullName")
    suspend fun getTags(repoFullName: String): List<StarTagEntity>

    @Query("SELECT DISTINCT tag FROM star_tags")
    suspend fun getAllTags(): List<String>

    @Query("SELECT * FROM star_tags WHERE tag = :tag")
    suspend fun getByTag(tag: String): List<StarTagEntity>

    @Query("DELETE FROM star_tags WHERE repo_full_name = :repoFullName AND tag = :tag")
    suspend fun deleteTag(repoFullName: String, tag: String)

    @Query("SELECT DISTINCT repo_full_name FROM star_tags")
    suspend fun allTaggedRepos(): List<String>
}

@Dao
interface RecentViewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(view: RecentViewEntity)

    @Query("SELECT * FROM recent_views ORDER BY viewed_at DESC LIMIT 50")
    suspend fun getRecent(): List<RecentViewEntity>

    @Query("DELETE FROM recent_views")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistoryEntity)

    @Query("SELECT DISTINCT query FROM search_history WHERE type = :type ORDER BY searched_at DESC LIMIT 20")
    suspend fun getRecent(type: String): List<String>

    @Query("DELETE FROM search_history WHERE type = :type")
    suspend fun clear(type: String)
}

// ===== 数据库 =====

@Database(
    entities = [RepoCacheEntity::class, StarTagEntity::class, SearchHistoryEntity::class, RecentViewEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoCacheDao(): RepoCacheDao
    abstract fun starTagDao(): StarTagDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun recentViewDao(): RecentViewDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mygithub.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

// ===== 最近查看历史 =====
