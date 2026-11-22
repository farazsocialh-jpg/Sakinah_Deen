package com.sakinah.deen.util

import android.app.Activity

/**
 * Drops touches that the system reports as obscured by another visible window.
 *
 * Without this, an overlay drawn on top of the app (the classic tapjacking attack) can make a
 * tap aimed elsewhere land on a sensitive control such as Delete account, Export PDF or the
 * widget's Save button. The flag is set on the window's decor view because it applies to the
 * whole view hierarchy of that window, which is what covers the Compose content too.
 */
fun Activity.enableTapjackingProtection() {
    window.decorView.setFilterTouchesWhenObscured(true)
}
