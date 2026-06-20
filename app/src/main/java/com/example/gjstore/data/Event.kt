package com.example.gjstore.data

data class Event(
    val dateCreated: String = "",
    val details: String = "",
    val amount: Double = 0.0,
    val createdBy: String = "",
    val editedBy: String = "",
    val editedDate: String = ""
)
