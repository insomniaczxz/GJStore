package com.example.gjstore.data

data class Product(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val unit: String = "",
    val size: Double = 0.0,
    val cost: Double = 0.0,
    val lastBoughtStore: String = "",
    val markupType: String = "", // "Percentage" or "Fixed"
    val markupValue: Double = 0.0,
    val price: Double = 0.0,
    val stock: Int = 0,
    val threshold: Int = 0,
    val date: String = "",
    val idealStock: Int = 0
) {
    val formattedSize: String
        get() = if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()
}
