package app.lawnchair.data.wallpaper.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.util.bitmapToByteArray
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class WallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    val dao = AppDatabase.Companion.INSTANCE.get(context).wallpaperDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory mirror of the top wallpapers so callers never block the main thread on Room. */
    @Volatile
    private var cachedTopWallpapers: List<Wallpaper> = emptyList()

    init {
        // Warm up the cache off the main thread; long-press popups read it synchronously.
        scope.launch { refreshCache() }
    }

    suspend fun saveWallpaper(wallpaperManager: WallpaperManager) {
        try {
            val wallpaperDrawable = wallpaperManager.drawable
            val currentBitmap = (wallpaperDrawable as BitmapDrawable).toBitmap()

            val byteArray = bitmapToByteArray(currentBitmap)

            saveWallpaper(byteArray)
        } catch (e: Exception) {
            Log.e("WallpaperChange", "Error detecting wallpaper change: ${e.message}")
        }
    }

    suspend fun ensureAnsutWallpaper() = withContext(Dispatchers.IO) {
        val imageData = context.resources.openRawResource(R.raw.ansut_wallpaper).use(InputStream::readBytes)
        val checksum = calculateChecksum(imageData)
        if (dao.getTopWallpapers().any { it.checksum == checksum }) return@withContext

        val imagePath = saveImageToAppStorage(imageData, checksum)
        dao.insert(
            Wallpaper(
                imagePath = imagePath,
                rank = 0,
                timestamp = System.currentTimeMillis(),
                checksum = checksum,
            ),
        )
        refreshCache()
    }

    private fun calculateChecksum(imageData: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(imageData)
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveWallpaper(imageData: ByteArray) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        val checksum = calculateChecksum(imageData)

        val existingWallpapers = dao.getTopWallpapers()

        if (existingWallpapers.any { it.checksum == checksum }) {
            Log.d("WallpaperService", "Wallpaper already exists with checksum: $checksum")
            return@withContext
        }
        val imagePath = saveImageToAppStorage(imageData, checksum)
        if (existingWallpapers.size < 4) {
            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = existingWallpapers.size,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
        } else {
            val lowestRankedWallpaper = existingWallpapers.minByOrNull { it.timestamp }

            if (lowestRankedWallpaper != null) {
                dao.deleteWallpaper(lowestRankedWallpaper.id)
                deleteWallpaperFile(lowestRankedWallpaper.imagePath)
            }

            for (wallpaper in existingWallpapers) {
                if (wallpaper.rank >= (lowestRankedWallpaper?.rank ?: 0)) {
                    dao.updateRank(wallpaper.rank)
                }
            }

            val wallpaper = Wallpaper(
                imagePath = imagePath,
                rank = 0,
                timestamp = timestamp,
                checksum = checksum,
            )
            dao.insert(wallpaper)
        }
        refreshCache()
    }

    suspend fun updateWallpaperRank(selectedWallpaper: Wallpaper) {
        val topWallpapers = dao.getTopWallpapers()
        val currentTime = System.currentTimeMillis()

        dao.updateWallpaper(selectedWallpaper.id, rank = 0, timestamp = currentTime)

        for (wallpaper in topWallpapers) {
            if (wallpaper.id != selectedWallpaper.id) {
                dao.updateRank(wallpaper.rank)
            }
        }
        refreshCache()
    }

    /**
     * Returns the cached list of top wallpapers. The cache is refreshed asynchronously
     * (see [refreshCache]), so this never performs blocking I/O on the caller's thread.
     */
    fun getTopWallpapers(): List<Wallpaper> = cachedTopWallpapers

    private suspend fun refreshCache(): List<Wallpaper> {
        val wallpapers = dao.getTopWallpapers()
        cachedTopWallpapers = wallpapers
        return wallpapers
    }

    private fun deleteWallpaperFile(imagePath: String) {
        val file = File(imagePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveImageToAppStorage(imageData: ByteArray, checksum: String): String {
        val storageDir = File(context.filesDir, "wallpapers")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        // Use the content checksum as the file name: stable and collision-free, unlike hashCode().
        val imageFile = File(storageDir, "wallpaper_$checksum.jpg")

        if (!imageFile.exists()) {
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }
        }

        return imageFile.absolutePath
    }

    override fun close() {
        scope.cancel()
        cachedTopWallpapers = emptyList()
    }
    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperService)
    }
}
