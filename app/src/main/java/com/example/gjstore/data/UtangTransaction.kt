package com.example.gjstore.data

data class UtangTransaction(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val type: String = "", // "Credit", "Kuwang", "Payment"
    val amount: Double = 0.0,
    val itemsSummary: String = "",
    val date: String = "",
    val recordedBy: String = "",
    val notes: String = ""
)
