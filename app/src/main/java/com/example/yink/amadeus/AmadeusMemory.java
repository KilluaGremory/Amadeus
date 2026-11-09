package com.example.yink.amadeus;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gestisce la memoria persistente di Amadeus tra le sessioni.
 * Salva: nome utente, contatore sessioni, timestamp ultima sessione,
 * e contatore persistente delle volte che l'utente ha chiamato "Christina".
 */
class AmadeusMemory {

    private static final String PREF_NAME          = "amadeus_memory";
    private static final String KEY_USER_NAME       = "user_name";
    private static final String KEY_SESSION_COUNT   = "session_count";
    private static final String KEY_LAST_SESSION_MS = "last_session_millis";
    private static final String KEY_CHRISTINA_TOTAL = "christina_total";

    private final SharedPreferences prefs;

    AmadeusMemory(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Nome utente ──────────────────────────────────────────────────────────

    /** Restituisce il nome salvato, o null se non ancora impostato. */
    String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    // ── Contatore sessioni ────────────────────────────────────────────────────

    /**
     * Numero di sessioni già avvenute PRIMA di quella corrente.
     * (0 = questa è la prima volta in assoluto)
     */
    int getPastSessionCount() {
        return prefs.getInt(KEY_SESSION_COUNT, 0);
    }

    /**
     * Da chiamare UNA SOLA VOLTA all'avvio di ogni sessione.
     * Incrementa il contatore e aggiorna il timestamp.
     */
    void startNewSession() {
        prefs.edit()
             .putInt(KEY_SESSION_COUNT, getPastSessionCount() + 1)
             .putLong(KEY_LAST_SESSION_MS, System.currentTimeMillis())
             .apply();
    }

    // ── Tempo dall'ultima sessione ────────────────────────────────────────────

    /**
     * Minuti trascorsi dall'ultima sessione.
     * Restituisce -1 se è la prima sessione assoluta.
     */
    long getMinutesSinceLastSession() {
        long last = prefs.getLong(KEY_LAST_SESSION_MS, -1L);
        if (last < 0) return -1L;
        return (System.currentTimeMillis() - last) / (1000L * 60);
    }

    /**
     * Giorni interi trascorsi dall'ultima sessione.
     * Restituisce -1 se è la prima sessione assoluta.
     */
    long getDaysSinceLastSession() {
        long last = prefs.getLong(KEY_LAST_SESSION_MS, -1L);
        if (last < 0) return -1L;
        return (System.currentTimeMillis() - last) / (1000L * 60 * 60 * 24);
    }

    // ── Contatore Christina (persistente) ────────────────────────────────────

    int getChristinaTotal() {
        return prefs.getInt(KEY_CHRISTINA_TOTAL, 0);
    }

    void incrementChristinaTotal() {
        prefs.edit().putInt(KEY_CHRISTINA_TOTAL, getChristinaTotal() + 1).apply();
    }
}
