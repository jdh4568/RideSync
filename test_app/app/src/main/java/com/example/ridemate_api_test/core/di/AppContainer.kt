package com.example.ridemate_api_test.core.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import com.example.ridemate_api_test.BuildConfig
import com.example.ridemate_api_test.app.session.LoginSession
import com.example.ridemate_api_test.core.datastore.KeystoreTokenStore
import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.core.websocket.OkHttpLoginSocket
import com.example.ridemate_api_test.feature.auth.data.remote.HttpAuthApi
import com.example.ridemate_api_test.feature.auth.data.repository.AuthRepositoryImpl
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginViewModel
import com.example.ridemate_api_test.feature.auth.presentation.signup.SignupViewModel
import com.example.ridemate_api_test.feature.connection.data.repository.ConnectionRepositoryImpl
import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository

class AppContainer(context: Context) {
    private val endpoint = ServerEndpoint(BuildConfig.SERVER_BASE_URL, BuildConfig.DEBUG)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val socketClient = client.newBuilder().callTimeout(0, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS).build()
    val session = LoginSession(HttpAuthApi(client, endpoint), KeystoreTokenStore(context.applicationContext),
        OkHttpLoginSocket(socketClient, endpoint), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    val authRepository: AuthRepository = AuthRepositoryImpl(session)
    val loginViewModelFactory = viewModelFactory { initializer { LoginViewModel(authRepository) } }
    val signupViewModelFactory = viewModelFactory { initializer { SignupViewModel() } }
    val connectionRepository: ConnectionRepository = ConnectionRepositoryImpl()
}
