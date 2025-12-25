package com.example.launcher

data class Language(
    val loadStreamsBuffer: String = "Load streams from source",
    val statusLoadingStreams: String = "Loading streams...",
    val statusUnknownError: String = "Unknown error occurred",
    val selectStream: String = "Select stream",
    val statusNoStreams: String = "No streams available"
)