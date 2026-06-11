package com.lexipopup.di

import android.content.Context
import com.google.gson.Gson
import com.lexipopup.data.local.database.DictionaryRepositoryImpl
import com.lexipopup.data.local.database.LexiDatabase
import com.lexipopup.data.local.database.VocabularyRepositoryImpl
import com.lexipopup.data.remote.api.DictionaryApi
import com.lexipopup.domain.repositories.DictionaryRepository
import com.lexipopup.domain.repositories.VocabularyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideLexiDatabase(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): LexiDatabase = LexiDatabase.create(context, scope)

    @Provides
    fun provideWordDao(db: LexiDatabase) = db.wordDao()

    @Provides
    fun provideVocabularyDao(db: LexiDatabase) = db.vocabularyDao()

    @Provides
    fun provideFlashcardDao(db: LexiDatabase) = db.flashcardDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.dictionaryapi.dev/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideDictionaryApi(retrofit: Retrofit): DictionaryApi =
        retrofit.create(DictionaryApi::class.java)

    @Provides
    @Singleton
    fun provideDictionaryRepository(impl: DictionaryRepositoryImpl): DictionaryRepository = impl

    @Provides
    @Singleton
    fun provideVocabularyRepository(impl: VocabularyRepositoryImpl): VocabularyRepository = impl
}
