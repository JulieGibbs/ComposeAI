package data.repository

import data.local.PreferenceLocalDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PreferenceRepository(
    private val preferenceLocalDataSource: PreferenceLocalDataSource,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    fun welcomeShown(): Flow<Boolean> =
        preferenceLocalDataSource.welcomeShown()

    suspend fun setWelcomeShown() = withContext(defaultDispatcher) {
        preferenceLocalDataSource.setWelcomeShown()
    }

}
