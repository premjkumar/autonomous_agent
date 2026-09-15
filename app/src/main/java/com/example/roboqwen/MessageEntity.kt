package com.example.roboqwen  

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import androidx.room.Dao  
import androidx.room.Insert  
import androidx.room.Query  
import androidx.room.Database  
import androidx.room.RoomDatabase  
import android.content.Context  
import androidx.room.Room  
import kotlinx.coroutines.flow.Flow  

@Entity(tableName = "chat_history")  
data class MessageEntity(  
    @PrimaryKey(autoGenerate = true) val id: Int = 0,  
    val sender: String,  
    val text: String,  
    val timestamp: Long = System.currentTimeMillis()  
)  

@Dao  
interface ChatHistoryDao {  
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")  
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>  

    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")  
    suspend fun getAllMessagesStatic(): List<MessageEntity>  

    @Insert  
    suspend fun insertMessage(message: MessageEntity)  

    @Query("DELETE FROM chat_history")  
    suspend fun purgeAllHistory()  
}  

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)  
abstract class RoboDatabase : RoomDatabase() {  
    abstract fun chatHistoryDao(): ChatHistoryDao  

    companion object {  
        @Volatile private var INSTANCE: RoboDatabase? = null  

        fun getDatabase(context: Context): RoboDatabase {  
            return INSTANCE ?: synchronized(this) {  
                val instance = Room.databaseBuilder(  
                    context.applicationContext,  
                    RoboDatabase::class.java,  
                    "roboqwen_db"  
                ).build()  
                INSTANCE = instance  
                instance  
            }  
        }  
    }  
}
