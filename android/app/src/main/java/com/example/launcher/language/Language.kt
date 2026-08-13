package com.example.launcher

data class Language(
    val loadStreamsBuffer: String = "Load streams from source",
    val statusLoadingStreams: String = "Loading streams...",
    val statusUnknownError: String = "Unknown error occurred",
    val selectStream: String = "Select stream",
    val statusNoStreams: String = "No streams available",

    val title: String = "Streams",
    val loadRemote: String = "Load Remote",
    val remoteLoadNotAvailable: String = "Remote load not available",

    // Program
    val program: String = "Program",
    val programLoading: String = "Loading...",
    val programEmpty: String = "No program data available",
    val programLoadError: String = "Error loading TV program",
    val programRefreshError: String = "Refresh failed"
)