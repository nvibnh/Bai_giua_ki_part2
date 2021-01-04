package com.rradzzio.sessionmanager.repository

import com.rradzzio.sessionmanager.data.local.AuthTokenDao
import com.rradzzio.sessionmanager.data.local.model.AuthTokenEntity
import com.rradzzio.sessionmanager.data.local.model.AuthTokenEntityMapper
import com.rradzzio.sessionmanager.data.remote.AuthTokenRemoteSource
import com.rradzzio.sessionmanager.data.remote.model.AuthTokenDto
import com.rradzzio.sessionmanager.data.remote.model.AuthTokenDtoMapper
import com.rradzzio.sessionmanager.data.remote.requests.AuthLoginRequest
import com.rradzzio.sessionmanager.data.remote.requests.AuthRegistrationRequest
import com.rradzzio.sessionmanager.domain.models.AuthToken
import com.rradzzio.sessionmanager.domain.models.ResponseType
import com.rradzzio.sessionmanager.domain.models.StateResponse
import com.rradzzio.sessionmanager.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authTokenRemoteSource: AuthTokenRemoteSource,
    private val authTokenDao: AuthTokenDao,
    private val authTokenDtoMapper: AuthTokenDtoMapper,
    private val authTokenEntityMapper: AuthTokenEntityMapper,
) : AuthRepository {

    override suspend fun login(authLoginRequest: AuthLoginRequest): Flow<Resource<AuthToken>> {

        return authTokenRemoteSource.loginAuthToken(authLoginRequest)
            .map { response ->
                if(response.isSuccessful && response.code() == 200) {
                    response.body()?.let { authTokenDto ->
                        val result = saveAuthToken(authTokenDto, authLoginRequest.email)
                        if(result < 0) {
                            Timber.e("Couldn't save an auth token into db.")
                        }
                        authTokenDto.let {
                            Resource.success(
                                authTokenDtoMapper.mapToDomainModel(it)
                            )
                        }
                    }?: returnUnknownError()
                } else {
                    response.errorBody()?.let { responseBody ->
                        val errorMessage = JSONObject(responseBody.charStream().readText()).getString("error")
                        Resource.error(
                            errorMessage,
                            AuthToken(
                                errorResponse = StateResponse(
                                    message = errorMessage,
                                    errorResponseType = ResponseType.Dialog
                                )
                            )
                        )
                    }?: returnUnknownError()
                }
            }

    }

    private suspend fun saveAuthToken(authTokenDto: AuthTokenDto, email: String): Long =
        authTokenDao.insert(
            authTokenEntity = AuthTokenEntity(
                token = authTokenDto.token,
                email = email
            )
        )

    override suspend fun register(authRegistrationRequest: AuthRegistrationRequest): Flow<Resource<AuthToken>> {
        return authTokenRemoteSource.registerAuthToken(authRegistrationRequest)
            .map { response ->
                if(response.isSuccessful && response.code() == 200) {
                    response.body()?.let {
                        Resource.success(
                            authTokenDtoMapper.mapToDomainModel(
                                it
                            )
                        )
                    }?: returnUnknownError()
                } else {
                    response.errorBody()?.let { responseBody ->
                        val errorMessage = JSONObject(responseBody.charStream().readText()).getString("error")
                        Resource.error(
                            errorMessage,
                            AuthToken(
                                errorResponse = StateResponse(
                                    message = errorMessage,
                                    errorResponseType = ResponseType.Dialog
                                )
                            )
                        )
                    }?: returnUnknownError()
                }
            }
    }

    private fun returnUnknownError(): Resource<AuthToken> {
        return Resource.error(
            "Unknown error",
            AuthToken(
                errorResponse = StateResponse(
                    "Unknown error",
                    errorResponseType = ResponseType.Toast
                )
            )
        )
    }

}