package com.frameforge.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.frameforge.app.data.FrameforgeDatabase
import com.frameforge.app.data.ProjectRepository
import com.frameforge.app.data.MediaRepository
import com.frameforge.app.ui.FrameforgeRoot
import com.frameforge.app.ui.theme.FrameforgeTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, FrameforgeDatabase::class.java, "frameforge.db").build()
        setContent { FrameforgeTheme { FrameforgeRoot(ProjectRepository(db.projectDao()), MediaRepository(db.mediaDao())) } }
    }
}
