package com.example.gjstore.data

data class Customer(
    val id: String = "",
    val name: String = "",
    val nicknames: String = "",
    val totalOutstanding: Double = 0.0,
    val lastTransactionDate: String = "",
    val dateCreated: String = ""
)
