package com.lagradost.cloudstream3.syncproviders.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper

object FirebaseSyncManager {
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    fun pushProgress(
        parentId: Int?,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        position: Long,
        duration: Long,
        updateTime: Long? = null
    ) {
        val uid = FirebaseAuthManager.uid ?: return
        if (parentId == null) return

        ioSafe {
            try {
                val ref = database.getReference("users")
                    .child(uid)
                    .child("continue_watching")
                    .child(parentId.toString())

                val data = hashMapOf<String, Any>(
                    "parentId" to parentId,
                    "episodeId" to (episodeId ?: parentId),
                    "episode" to (episode ?: 0),
                    "season" to (season ?: 0),
                    "position" to position,
                    "duration" to duration,
                    "updateTime" to (updateTime ?: System.currentTimeMillis())
                )
                ref.updateChildren(data)
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    fun syncFromCloud(onComplete: (() -> Unit)? = null) {
        val uid = FirebaseAuthManager.uid ?: return
        try {
            val ref = database.getReference("users").child(uid).child("continue_watching")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    ioSafe {
                        try {
                            for (child in snapshot.children) {
                                val parentId = (child.child("parentId").value as? Number)?.toInt() ?: continue
                                val episodeId = (child.child("episodeId").value as? Number)?.toInt()
                                val episode = (child.child("episode").value as? Number)?.toInt()
                                val season = (child.child("season").value as? Number)?.toInt()
                                val pos = (child.child("position").value as? Number)?.toLong() ?: 0L
                                val dur = (child.child("duration").value as? Number)?.toLong() ?: 0L
                                val updateTime = (child.child("updateTime").value as? Number)?.toLong()

                                DataStoreHelper.setLastWatched(
                                    parentId = parentId,
                                    episodeId = episodeId,
                                    episode = episode,
                                    season = season,
                                    isFromDownload = false,
                                    updateTime = updateTime
                                )

                                val targetId = episodeId ?: parentId
                                if (dur > 0) {
                                    DataStoreHelper.setViewPos(targetId, pos, dur)
                                }
                            }
                            MainActivity.reloadHomeEvent(true)
                            onComplete?.invoke()
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    logError(error.toException())
                }
            })
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun syncAllLocalToCloud() {
        val uid = FirebaseAuthManager.uid ?: return
        ioSafe {
            try {
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: return@ioSafe
                for (id in resumeIds) {
                    val lastWatched = DataStoreHelper.getLastWatched(id) ?: continue
                    val targetId = lastWatched.episodeId ?: lastWatched.parentId ?: continue
                    val posDur = DataStoreHelper.getViewPos(targetId)
                    pushProgress(
                        parentId = lastWatched.parentId,
                        episodeId = lastWatched.episodeId,
                        episode = lastWatched.episode,
                        season = lastWatched.season,
                        position = posDur?.position ?: 0L,
                        duration = posDur?.duration ?: 0L,
                        updateTime = lastWatched.updateTime
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }
}
