package com.example.youroom.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.R
import com.example.youroom.domain.Campus
import com.example.youroom.ui.theme.LocalCampusAppearance
import com.example.youroom.ui.theme.themeRoles

internal object AboutLinks {
    const val project = "https://github.com/Tale2229/YOURoom"
    const val feedback = "$project/issues"
}

internal object AboutCopy {
    const val description = "北邮空闲教室查询与使用规划个人助手"
    const val disclaimer = "非学校官方应用"
    const val author = "23级 软工 小嚎猪"
    val data = listOf("空闲信息来自学校教务，并非实时空座", "教室开放情况请以现场为准")
    val local = listOf(
        "教室标记与设置保存在设备上，可能参与系统备份",
        "退出登录清除登录状态，保留已记住的账号密码、教室标记与设置",
        "清除缓存需使用登录账密校验，不可恢复，请谨慎操作",
    )
}

/** Separate folio layout; practice still uses its existing single physical window. */
@Composable internal fun AboutSheet(version: String, campus: Campus, onDismiss: () -> Unit) {
    val density=LocalDensity.current
    val browser=LocalUriHandler.current
    val appearance=LocalCampusAppearance.current
    val p=guideDirectoryPalette(MaterialTheme.colorScheme,themeRoles(),appearance.tone(campus),
        appearance.isDark(isSystemInDarkTheme()))
    GuideDialog("sheet",onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        CompositionLocalProvider(LocalDensity provides density,LocalUriHandler provides browser) {
            BackHandler(enabled=LocalUsageGuide.current==null,onBack=onDismiss)
            Surface(Modifier.widthIn(max=480.dp).fillMaxWidth(.94f).heightIn(max=740.dp)
                .guideTag("settings_about_dialog"),shape=RoundedCornerShape(20.dp),color=p.paper,contentColor=p.ink) {
                Box {
                    AboutContent(version,p)
                    // The close button stays readable after the cover scrolls away.
                    IconButton(onDismiss,Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(p.cover,RoundedCornerShape(50)).testTag("settings_sheet_close")) {
                        Icon(Icons.Filled.Close,"关闭",Modifier.size(18.dp),tint=p.onCover)
                    }
                }
            }
        }
    }
}

@Composable private fun AboutContent(version:String,p:GuideDirectoryPalette) {
    val context=LocalContext.current
    val browser=LocalUriHandler.current
    val linksEnabled=LocalUsageGuide.current?.stage!=GuideStage.ABOUT
    fun open(url:String) {
        runCatching { browser.openUri(url) }.onFailure {
            Toast.makeText(context,"无法打开链接，请检查浏览器",Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("settings_about_content")) {
        Box(Modifier.fillMaxWidth().heightIn(min=176.dp).background(p.cover).clipToBounds().testTag("settings_about_cover")) {
            Image(painterResource(R.drawable.about_mark),null,
                Modifier.align(Alignment.BottomEnd).offset(x=15.dp,y=(-7).dp).size(133.dp,154.dp).alpha(.11f),
                colorFilter=ColorFilter.tint(p.onCover))
            Column(Modifier.padding(horizontal=24.dp,vertical=16.dp).testTag("settings_about_brand")) {
                Text("YOURoom / ABOUT",Modifier.padding(end=32.dp),color=p.onCover,
                    fontSize=10.sp,lineHeight=16.sp,letterSpacing=1.sp,fontWeight=FontWeight.Medium)
                Text("YOURoom",Modifier.padding(top=12.dp,bottom=8.dp).testTag("settings_about_name").semantics { heading() },
                    color=p.onCover,fontSize=36.sp,lineHeight=43.sp,fontWeight=FontWeight.SemiBold)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("VERSION",color=p.onCover,fontSize=10.sp,lineHeight=16.sp,letterSpacing=.6.sp)
                    Text(version,Modifier.testTag("settings_about_version"),color=p.onCover,fontSize=10.sp,lineHeight=16.sp,letterSpacing=.6.sp)
                }
                Text(AboutCopy.description,Modifier.padding(top=8.dp).guideTag("settings_about_description"),
                    color=p.onCover,fontSize=12.sp,lineHeight=20.sp)
                Text(AboutCopy.disclaimer,Modifier.padding(top=2.dp),color=p.onCover,fontSize=11.sp,lineHeight=18.sp)
            }
        }
        Column(Modifier.padding(horizontal=24.dp)) {
            AboutLink("01","GitHub","Tale2229 / YOURoom","settings_about_project",p,linksEnabled) { open(AboutLinks.project) }
            HorizontalDivider(color=p.rule)
            AboutLink("02","问题反馈","Issues / 问题与建议","settings_about_feedback",p,linksEnabled) { open(AboutLinks.feedback) }
            HorizontalDivider(color=p.rule)
        }
        AboutSection("03","数据与使用","settings_about_data",AboutCopy.data,p)
        HorizontalDivider(Modifier.padding(horizontal=24.dp).testTag("settings_about_data_local_divider"),color=p.rule)
        AboutSection("04","本地与登录","settings_about_local",AboutCopy.local,p)
        HorizontalDivider(Modifier.padding(horizontal=24.dp),color=p.rule)
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=10.dp).testTag("settings_about_author"),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("开发者",color=p.muted,fontSize=10.sp,lineHeight=16.sp)
                Text(AboutCopy.author,Modifier.testTag("settings_about_author_name"),color=p.ink,
                    fontSize=14.sp,lineHeight=23.sp,fontWeight=FontWeight.Bold)
            }
            // Bundled vector converted from res/raw/about_pig.svg; never tinted or downloaded.
            AboutPig()
        }
    }
}

/** One consumer owns the animation; expression changes never restart its effect. */
@Composable private fun AboutPig() {
    val shake=remember { Animatable(0f) }
    val interaction=remember { AboutPigInteraction() }
    LaunchedEffect(interaction) {
        interaction.play {
            shake.snapTo(0f)
            shake.animateTo(0f,keyframes {
                durationMillis=280
                -1f at 35; 1f at 70; -.75f at 110; .65f at 150; -.35f at 195; .15f at 235; 0f at 280
            })
        }
    }
    Image(painterResource(if(interaction.squinting) R.drawable.about_pig_squint else R.drawable.about_pig),"小猪",
        Modifier.size(63.dp).graphicsLayer { translationX=shake.value*4.dp.toPx() }
            .testTag("settings_about_pig")
            .semantics { stateDescription=if(interaction.squinting) "眯眼" else "豆豆眼" }
            .clickable(role=Role.Button,onClickLabel="逗逗小猪",
                interactionSource=remember { MutableInteractionSource() },indication=null,onClick=interaction::click))
}

@Composable private fun AboutIndex(value:String,p:GuideDirectoryPalette) {
    Text(value,Modifier.width(32.dp),color=p.muted,fontSize=23.sp,lineHeight=28.sp,
        fontFamily=FontFamily.Serif,fontStyle=FontStyle.Italic)
}

@Composable private fun AboutSection(index:String,title:String,tag:String,paragraphs:List<String>,p:GuideDirectoryPalette) {
    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp).testTag(tag),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AboutIndex(index,p)
        Column(Modifier.weight(1f)) {
            Text(title,Modifier.testTag("${tag}_heading").semantics { heading() },
                color=p.ink,fontSize=17.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold)
            Column(Modifier.padding(top=5.dp).testTag("${tag}_paragraphs"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                paragraphs.forEach { text ->
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text("•",color=p.muted,fontSize=12.sp,lineHeight=20.sp)
                        Text(text,Modifier.weight(1f),color=p.muted,fontSize=12.sp,lineHeight=20.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun AboutLink(index:String,title:String,caption:String,tag:String,p:GuideDirectoryPalette,
    enabled:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AboutIndex(index,p)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,color=p.ink,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
            Text(caption,color=p.muted,fontSize=10.sp,lineHeight=17.sp)
        }
        GuideArrow(forward=true,description=null,color=p.ink)
    }
}
