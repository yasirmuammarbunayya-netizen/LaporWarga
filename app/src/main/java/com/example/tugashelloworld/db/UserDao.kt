package com.example.tugashelloworld.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.tugashelloworld.model.User

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?
}
