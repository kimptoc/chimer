package net.kimptoc.timerwithauto.alarm

import android.content.Context
import androidx.car.app.connection.CarConnection

interface CarConnectionChecker {
    fun isConnected(): Boolean
}

/**
 * CarConnection only exposes state via LiveData (no synchronous getter), so this observes
 * forever from construction and caches the latest value for the synchronous [isConnected] check
 * AlarmService needs at ring time.
 */
class SystemCarConnectionChecker(context: Context) : CarConnectionChecker {

    @Volatile private var connectionType: Int = CarConnection.CONNECTION_TYPE_NOT_CONNECTED

    init {
        CarConnection(context.applicationContext).type.observeForever { type ->
            connectionType = type
        }
    }

    override fun isConnected(): Boolean =
        connectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
}
