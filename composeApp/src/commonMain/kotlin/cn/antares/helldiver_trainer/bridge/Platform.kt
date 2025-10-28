package cn.antares.helldiver_trainer.bridge

enum class DevicePlatform { Android, Windows }

expect fun getCurrentPlatform(): DevicePlatform