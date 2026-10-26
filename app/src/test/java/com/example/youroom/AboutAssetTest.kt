package com.example.youroom

import com.example.youroom.ui.AboutCopy
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AboutAssetTest {
    @Test fun squintOnlyReplacesEyesWithInwardChevronsAndKeepsOriginalColors() {
        val normal=xml("res/drawable/about_pig.xml").getElementsByTagName("path")
        val squint=xml("res/drawable/about_pig_squint.xml").getElementsByTagName("path")
        assertEquals(12,squint.length)
        for(index in 0..9) for(attribute in listOf("android:fillColor","android:pathData"))
            assertEquals(normal.item(index).attributes.getNamedItem(attribute).nodeValue,
                squint.item(index).attributes.getNamedItem(attribute).nodeValue)
        for(index in 10..11) {
            val attributes=squint.item(index).attributes
            assertEquals("#2D2D2B",attributes.getNamedItem("android:strokeColor").nodeValue)
            assertEquals("#00000000",attributes.getNamedItem("android:fillColor").nodeValue)
            assertEquals("round",attributes.getNamedItem("android:strokeLineJoin").nodeValue)
            assertEquals(2,Regex("L").findAll(attributes.getNamedItem("android:pathData").nodeValue).count())
        }
    }
    private fun file(path:String)=File("src/main/$path").takeIf {it.exists()} ?: File("app/src/main/$path")
    private fun xml(path:String)=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path))

    @Test fun originalSvgIsBundledUnderSrcWithoutRemoteImageReferences() {
        val svg=file("res/raw/about_pig.svg").readText()
        assertTrue(svg.contains("viewBox=\"0 0 128 128\""))
        assertFalse(svg.contains("<image"))
        assertFalse(svg.contains("http://images."))
        assertFalse(svg.contains("https://"))
        val document=xml("res/raw/about_pig.svg")
        assertEquals(9,document.getElementsByTagName("path").length)
        assertEquals(3,document.getElementsByTagName("ellipse").length)
    }

    @Test fun nativeVectorRetainsAllOriginalColorBlocksWithoutTint() {
        val svg=xml("res/raw/about_pig.svg")
        val vector=xml("res/drawable/about_pig.xml")
        val root=vector.documentElement
        assertFalse(root.hasAttribute("android:tint"))
        assertEquals("128",root.getAttribute("android:viewportWidth"))
        val source=(0 until svg.documentElement.childNodes.length).map {svg.documentElement.childNodes.item(it)}
            .filter {it.nodeName=="path" || it.nodeName=="ellipse"}
        val paths=vector.getElementsByTagName("path")
        assertEquals(12,paths.length)
        source.forEachIndexed { index,node ->
            val style=node.attributes.getNamedItem("style").nodeValue
            val color=Regex("fill:(#[0-9A-Fa-f]+)").find(style)!!.groupValues[1]
            val path=paths.item(index).attributes
            assertEquals(color,path.getNamedItem("android:fillColor").nodeValue)
            if(node.nodeName=="path") assertEquals(node.attributes.getNamedItem("d").nodeValue.trim().replace(Regex("\\s+")," "),
                path.getNamedItem("android:pathData").nodeValue)
            else assertTrue(path.getNamedItem("android:pathData").nodeValue.startsWith("M"))
        }
    }

    @Test fun approvedCopyUsesAcademicAffairsAndTheConfirmedCacheWarning() {
        assertEquals("空闲信息来自学校教务，并非实时空座",AboutCopy.data.first())
        assertEquals("清除缓存需使用登录账密校验，不可恢复，请谨慎操作",AboutCopy.local.last())
        assertEquals("北邮空闲教室查询与使用规划个人助手",AboutCopy.description)
        assertEquals("非学校官方应用",AboutCopy.disclaimer)
    }
}
