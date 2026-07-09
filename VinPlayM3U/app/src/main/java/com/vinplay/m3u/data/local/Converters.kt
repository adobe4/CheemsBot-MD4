package com.vinplay.m3u.data.local

import androidx.room.TypeConverter
import com.vinplay.m3u.data.model.ChannelKind
import com.vinplay.m3u.data.model.TestStatus

/** Room persists enums as their stable String name so re-ordering the enum is safe. */
class Converters {
    @TypeConverter fun kindToString(value: ChannelKind): String = value.name
    @TypeConverter fun stringToKind(value: String): ChannelKind =
        runCatching { ChannelKind.valueOf(value) }.getOrDefault(ChannelKind.UNKNOWN)

    @TypeConverter fun statusToString(value: TestStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): TestStatus =
        runCatching { TestStatus.valueOf(value) }.getOrDefault(TestStatus.UNTESTED)
}
