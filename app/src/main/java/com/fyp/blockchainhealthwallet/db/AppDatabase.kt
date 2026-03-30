package com.fyp.blockchainhealthwallet.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Blockchain Health Wallet application.
 *
 * Currently includes:
 * - ProofRecord: Stores ZK proof verification history
 *
 * Version 1: Initial schema with ProofRecord entity
 */
@Database(entities = [ProofRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun proofDao(): ProofDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get or create the database instance (singleton pattern).
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blockchain_health_wallet.db"
                )
                    .fallbackToDestructiveMigration()  // For development; use proper migrations in production
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * Close the database (useful for testing).
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
