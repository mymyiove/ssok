package app.parkingcoach.shot

import android.view.View
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.parkingcoach.R
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 밤 모드(values-night 색 + 어두운 어라운드뷰·리플레이 팔레트) — 같은 장면을 밤으로 한 장 */
class NightShotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 1280, screenHeight = 720, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false, nightMode = NightMode.NIGHT),
        theme = "Theme.ParkingCoach", showSystemUi = false)
    @Test fun play_underground_stage2_night() { val root = paparazzi.inflate<View>(R.layout.activity_main); playShot(root, 1, 3, -520.0, true); paparazzi.snapshot(root, "play-02-nightmode") }
}
