package com.lexipopup.presentation.widget

import com.lexipopup.data.local.dao.WordDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun wordDao(): WordDao
}
