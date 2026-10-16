package com.example.youroom.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Single-building silhouette and three-stop path, matching the navigation's
 * visual weight without inheriting a house roof or a document/checklist shape. */
internal object NavigationIcons {
    val building=ImageVector.Builder("TeachingBuilding",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.65f,strokeLineCap=StrokeCap.Round,
            strokeLineJoin=StrokeJoin.Round) {
            moveTo(4f,21f);lineTo(4f,4.5f)
            curveTo(8f,2.5f,16f,2.5f,20f,4.5f)
            lineTo(20f,21f);lineTo(14f,21f);lineTo(14f,16.5f)
            lineTo(10f,16.5f);lineTo(10f,21f);close()
        }
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round) {
            for(x in listOf(8f,12f,16f)) for(y in listOf(7f,11f)) {
                moveTo(x,y);lineTo(x,y+.15f)
            }
        }
    }.build()

    val route=ImageVector.Builder("StudyRoute",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.65f,strokeLineCap=StrokeCap.Round,
            strokeLineJoin=StrokeJoin.Round) {
            moveTo(6.4f,4f);lineTo(17f,4f)
            curveTo(22f,4f,22f,12f,17f,12f)
            lineTo(6f,12f);curveTo(1f,12f,1f,20f,6f,20f)
            lineTo(17.6f,20f)
        }
        path(stroke=SolidColor(Color.Black),strokeLineWidth=1.65f) {
            // Hollow stops retain their outline after the tab's color inversion.
            moveTo(6.4f,4f);arcTo(2.4f,2.4f,0f,true,true,1.6f,4f)
            arcTo(2.4f,2.4f,0f,true,true,6.4f,4f);close()
            moveTo(22.4f,20f);arcTo(2.4f,2.4f,0f,true,true,17.6f,20f)
            arcTo(2.4f,2.4f,0f,true,true,22.4f,20f);close()
        }
    }.build()
}
