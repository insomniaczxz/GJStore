package com.example.gjstore.data

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

data class DropdownSettings(
    val brands: SnapshotStateList<String> = mutableStateListOf(),
    val categories: SnapshotStateList<String> = mutableStateListOf(),
    val units: SnapshotStateList<String> = mutableStateListOf(),
    val stores: SnapshotStateList<String> = mutableStateListOf()
)
