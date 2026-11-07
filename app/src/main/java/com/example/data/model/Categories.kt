package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

object Categories {

    val expenseCategories = listOf(
        CategoryInfo("อาหาร/เครื่องดื่ม", Icons.Default.Fastfood, Color(0xFFF97316)), // Orange
        CategoryInfo("ช้อปปิ้ง", Icons.Default.LocalMall, Color(0xFFEC4899)), // Pink
        CategoryInfo("การเดินทาง", Icons.Default.DirectionsBus, Color(0xFF3B82F6)), // Blue
        CategoryInfo("บิล/ค่าน้ำไฟ", Icons.Default.Receipt, Color(0xFF8B5CF6)), // Purple
        CategoryInfo("บันเทิง", Icons.Default.Movie, Color(0xFF06B6D4)), // Cyan
        CategoryInfo("สุขภาพ", Icons.Default.MedicalServices, Color(0xFFEF4444)), // Red
        CategoryInfo("ที่พักอาศัย", Icons.Default.Home, Color(0xFF10B981)), // Emerald
        CategoryInfo("การศึกษา", Icons.Default.School, Color(0xFF6366F1)), // Indigo
        CategoryInfo("อื่นๆ", Icons.Default.MoreHoriz, Color(0xFF64748B)) // Slate
    )

    val incomeCategories = listOf(
        CategoryInfo("เงินเดือน", Icons.Default.Paid, Color(0xFF10B981)), // Emerald
        CategoryInfo("ธุรกิจ/ค้าขาย", Icons.Default.Storefront, Color(0xFFF59E0B)), // Amber
        CategoryInfo("งานเสริม/ฟรีแลนซ์", Icons.Default.Work, Color(0xFF3B82F6)), // Blue
        CategoryInfo("การลงทุน", Icons.Default.TrendingUp, Color(0xFF8B5CF6)), // Purple
        CategoryInfo("ของขวัญ/โบนัส", Icons.Default.CardGiftcard, Color(0xFFEC4899)), // Pink
        CategoryInfo("อื่นๆ", Icons.Default.MonetizationOn, Color(0xFF14B8A6)) // Teal
    )

    fun getCategoryInfo(name: String, type: String): CategoryInfo {
        val list = if (type == "INCOME") incomeCategories else expenseCategories
        return list.firstOrNull { it.name == name }
            ?: (if (type == "INCOME") expenseCategories.firstOrNull { it.name == name } else incomeCategories.firstOrNull { it.name == name })
            ?: CategoryInfo(name, Icons.Default.MoreHoriz, Color(0xFF64748B))
    }
}
