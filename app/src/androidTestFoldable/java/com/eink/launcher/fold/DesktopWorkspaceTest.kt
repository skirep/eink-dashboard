package com.eink.launcher.fold

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DesktopWorkspaceTest {
    @Test fun windowsPanelsDragResizeMinimizeAndSurviveFold() = exercise(LauncherThemes.WINDOWS)
    @Test fun macPanelsDragResizeMinimizeAndSurviveFold() = exercise(LauncherThemes.DESKTOP)
    @Test fun linuxPanelsDragResizeMinimizeAndSurviveFold() = exercise(LauncherThemes.LINUX)
    private fun exercise(theme: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("fold-metro",0)
        val original=prefs.all
        val device=UiDevice.getInstance(instrumentation)
        fun restorePrefs() {
            val edit=prefs.edit().clear()
            for((key,value) in original) when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
                is Int -> edit.putInt(key,value)
                is Long -> edit.putLong(key,value)
                is Float -> edit.putFloat(key,value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key,value as Set<String>)
            }
            edit.commit()
        }
        fun window(root: View): DesktopWorkspace.WorkspaceWindow? {
            if(root is DesktopWorkspace.WorkspaceWindow && root.windowKey=="weather") return root
            if(root is ViewGroup) for(i in 0 until root.childCount) window(root.getChildAt(i))?.let { return it }
            return null
        }
        try {
            prefs.edit().putString("theme",theme).putBoolean("desktopEnabled",true)
                .remove("desk:$theme:weather").remove("desk-hidden:$theme:weather").remove("desk-max:$theme:weather").commit()
            device.executeShellCommand("wm size 1920x1920");device.executeShellCommand("wm density 420")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.desc("Arrossega Temps")),10000))
                device.waitForIdle()
                fun bounds(): Rect {
                    var result=Rect()
                    scenario.onActivity { activity ->
                        val panel=requireNotNull(window(activity.window.decorView))
                        result=Rect(panel.x.toInt(),panel.y.toInt(),panel.x.toInt()+panel.width,panel.y.toInt()+panel.height)
                    }
                    return result
                }
                val before=bounds()
                val title=device.findObject(By.desc("Arrossega Temps")).visibleBounds
                device.swipe(title.centerX(),title.centerY(),title.centerX()+90,title.centerY()+70,25)
                device.waitForIdle()
                val dragged=bounds()
                assertTrue("Dragging a title must move its actual panel",dragged.left>before.left+30 && dragged.top>before.top+20)
                assertNotNull("Position must be persisted",prefs.getString("desk:$theme:weather",null))
                val corner=device.findObject(By.desc("Redimensiona Temps")).visibleBounds
                device.swipe(corner.right-12,corner.centerY(),corner.right+65,corner.centerY()+30,25)
                device.waitForIdle()
                val resized=bounds()
                assertTrue("Corner drag must resize the window",resized.width()>dragged.width()+15)
                device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/$theme-desktop-moved.png")
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.desc("Arrossega Temps")),10000))
                device.waitForIdle()
                val restored=bounds()
                assertTrue("Recreation must keep the position",abs(restored.left-resized.left)<8 && abs(restored.top-resized.top)<8)
                assertTrue("Recreation must keep the size",abs(restored.width()-resized.width())<8)
                device.findObject(UiSelector().description("Maximitza Temps")).click()
                device.waitForIdle()
                assertTrue(bounds().width()>restored.width())
                device.findObject(UiSelector().description("Restaura Temps")).click()
                device.findObject(UiSelector().description("Minimitza Temps")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Restaura el panell Temps")),5000))
                device.findObject(UiSelector().description("Restaura el panell Temps")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Arrossega Temps")),5000))
                device.executeShellCommand("wm size 1080x1920")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista compacta")),10000))
                assertFalse(device.hasObject(By.desc("Arrossega Temps")))
                device.executeShellCommand("wm size 1920x1920")
                assertTrue(device.wait(Until.hasObject(By.desc("Arrossega Temps")),10000))
                device.waitForIdle()
                assertTrue("Unfolding restores saved desktop",abs(bounds().left-resized.left)<8)
                // Resolve the control at action time: resize/data callbacks can
                // replace accessibility nodes between a By lookup and click.
                device.findObject(UiSelector().description("Pàgina aplicacions")).click()
                assertTrue("Window drags must not break normal app-page navigation",device.wait(Until.hasObject(By.clazz("android.widget.EditText")),10000))
            }
        } finally {
            restorePrefs();LauncherThemes.select(original["theme"] as? String)
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
