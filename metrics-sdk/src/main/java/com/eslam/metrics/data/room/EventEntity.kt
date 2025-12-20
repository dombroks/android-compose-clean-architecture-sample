package com.eslam.metrics.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * EventEntity - Room entity for storing events
 * 
 * Screenshots are stored as base64-encoded JPEG data for efficient
 * transfer to backend without file I/O overhead.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "event_name")
    val eventName: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,

    /**
     * Base64-encoded JPEG screenshot data.
     * Compressed and downscaled for minimal size.
     * Backend processes the actual image.
     */
    @ColumnInfo(name = "screenshot_data")
    val screenshotData: String? = null,

    @ColumnInfo(name = "memory_usage_mb")
    val memoryUsageMb: Long? = null,

    @ColumnInfo(name = "cpu_usage_percent")
    val cpuUsagePercent: Float? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
