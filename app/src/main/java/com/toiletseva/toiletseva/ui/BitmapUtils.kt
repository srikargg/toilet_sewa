package com.toiletseva.toiletseva.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor {
    val vectorDrawable: Drawable? = ContextCompat.getDrawable(context, vectorResId)
    if (vectorDrawable == null) {
        throw IllegalArgumentException("Resource not found: $vectorResId")
    }
    val width = vectorDrawable.intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = vectorDrawable.intrinsicHeight.takeIf { it > 0 } ?: 48
    vectorDrawable.setBounds(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
