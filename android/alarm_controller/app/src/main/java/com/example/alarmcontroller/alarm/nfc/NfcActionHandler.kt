package com.example.alarmcontroller.alarm.nfc

import android.content.Context
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.alarm.AlarmRingtoneService

class NfcActionHandler(private val context: Context) {

    private val repository = AlarmRepository(context)

    sealed interface NfcResult {
        data class StoppedRingingAlarm(val message: String) : NfcResult
        data class DismissedNextAlarm(val alarmTitle: String, val time: String) : NfcResult
        data class NoAction(val message: String) : NfcResult
    }

    /**
     * Traite la détection d'un tag NFC :
     * 1. Si une alarme sonne actuellement -> l'arrête immédiatement (Stop).
     * 2. Si aucune alarme ne sonne -> ignore la prochaine alarme planifiée ("Cette fois tu ne te déclenches pas").
     */
    fun handleNfcTagScanned(): NfcResult {
        // Cas 1 : Alarme en cours de sonnerie
        if (AlarmRingtoneService.isRinging.value) {
            val title = AlarmRingtoneService.ringingAlarmTitle.value ?: "Alarme"
            AlarmRingtoneService.stopAlarm(context)
            return NfcResult.StoppedRingingAlarm("Tag NFC scanné : Alarme '$title' arrêtée avec succès !")
        }

        // Cas 2 : Aucune alarme ne sonne -> "Cette fois tu ne te déclenches pas"
        val upcoming = repository.findUpcomingAlarm()
        if (upcoming != null) {
            val newDismissState = !upcoming.isDismissedForNext
            repository.dismissNext(upcoming.id, newDismissState)

            return if (newDismissState) {
                NfcResult.DismissedNextAlarm(upcoming.title, upcoming.startFormatted)
            } else {
                NfcResult.NoAction("Tag NFC scanné : L'alarme '${upcoming.title}' a été rétablie.")
            }
        }

        return NfcResult.NoAction("Tag NFC scanné, mais aucune alarme active trouvée.")
    }
}
