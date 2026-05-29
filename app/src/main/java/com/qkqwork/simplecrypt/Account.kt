package com.qkqwork.simplecrypt

data class Account(
    val userId: String,
    val useTee: Boolean,
    val currentVersion: Int = 0,
    val isHardwareVerified: Boolean = false
)
