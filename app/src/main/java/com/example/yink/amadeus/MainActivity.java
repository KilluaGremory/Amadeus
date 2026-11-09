package com.example.yink.amadeus;

/*
 * Big thanks to https://github.com/RIP95 aka Emojikage
 */

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_CODE = 11302;

    private VoiceLine[] voiceLines = VoiceLine.Line.getLines();
    private Random randomgen = new Random();
    private String recogLang;
    private String[] contextLang;
    private SpeechRecognizer sr;
    private Handler handler;
    private AmadeusMemory memory;

    // Shake sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelCurrent;
    private float accelLast;
    private float shake;
    private long lastShakeTime = 0;

    // Poke / Fast tap counter
    private int fastTapCount = 0;
    private long lastTapTime = 0;

    // Inactivity speech reaction
    private static final long INACTIVITY_DELAY_MS = 45000L; // 45 secondi
    private Handler inactivityHandler;
    private Runnable inactivityRunnable;

    // Idle eye blink
    private Handler blinkHandler;
    private Runnable blinkRunnable;
    private ImageView kurisuImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationHelper.createNotificationChannels(this);

        // Edge-to-edge system window insets support
        View rootView = findViewById(R.id.activity_main);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        ImageView kurisu = (ImageView) findViewById(R.id.imageView_kurisu);
        kurisuImageView = kurisu;
        ImageView subtitlesBackground = (ImageView) findViewById(R.id.imageView_subtitles);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        recogLang = settings.getString("recognition_lang", getString(R.string.default_recog_lang));
        contextLang = recogLang.split("-");

        initSpeechRecognizer();
        initInactivityTimer();
        initBlink();
        initShakeSensor();

        handler = new Handler(Looper.getMainLooper());

        // Inizializza la memoria persistente e registra la sessione
        memory = new AmadeusMemory(this);
        int pastSessions = memory.getPastSessionCount();
        long daysSinceLast = memory.getDaysSinceLastSession();
        long minutesSinceLast = memory.getMinutesSinceLastSession();
        String savedName = memory.getUserName();
        memory.startNewSession();

        if (!settings.getBoolean("show_subtitles", false) && subtitlesBackground != null) {
            subtitlesBackground.setVisibility(View.INVISIBLE);
        }

        requestAppPermissions();

        // ── Saluto contestuale ────────────────────────────────────────────────
        speakGreeting(pastSessions, daysSinceLast, minutesSinceLast, savedName);

        final Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (Amadeus.isLoop) {
                    Amadeus.speak(voiceLines[randomgen.nextInt(voiceLines.length)], MainActivity.this);
                    handler.postDelayed(this, 5000 + randomgen.nextInt(5) * 1000L);
                }
            }
        };

        kurisu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetInactivityTimer();
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 2500) {
                    fastTapCount++;
                } else {
                    fastTapCount = 1;
                }
                lastTapTime = now;

                if (Amadeus.isLoop) {
                    return;
                }

                // Se l'utente continua a toccare Kurisu ripetutamente (poke)
                if (fastTapCount >= 7) {
                    fastTapCount = 0;
                    Amadeus.speak(voiceLines[VoiceLine.Line.PERVERT_IDIOT], MainActivity.this);
                    return;
                } else if (fastTapCount >= 4) {
                    Amadeus.speak(voiceLines[VoiceLine.Line.WHY_SAY_THAT], MainActivity.this);
                    return;
                }

                int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);

                if (!Amadeus.isSpeaking) {
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        promptSpeechInput();
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
                        Amadeus.speak(voiceLines[VoiceLine.Line.DAGA_KOTOWARU], MainActivity.this);
                    }
                }
            }
        });

        kurisu.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                resetInactivityTimer();
                fastTapCount = 0;
                if (!Amadeus.isLoop && !Amadeus.isSpeaking) {
                    handler.post(loop);
                    Amadeus.isLoop = true;
                } else {
                    handler.removeCallbacks(loop);
                    Amadeus.isLoop = false;
                }
                return true;
            }
        });
    }

    /**
     * Sceglie il saluto iniziale in base alla storia delle sessioni, all'ora del giorno
     * e al nome salvato dell'utente. Fedele allo stile di Amadeus/Kurisu in S;G0.
     */
    private void speakGreeting(int pastSessions, long daysSinceLast, long minutesSinceLast, String savedName) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        // Prima volta assoluta
        if (pastSessions == 0) {
            Amadeus.speak(voiceLines[VoiceLine.Line.PLEASED_TO_MEET], this);
            return;
        }

        // Ritorno rapido (meno di 10 minuti fa) → "Uh… Senpai?"
        if (minutesSinceLast >= 0 && minutesSinceLast < 10) {
            Amadeus.speak(voiceLines[VoiceLine.Line.UH_SENPAI], this);
            return;
        }

        // Dopo più di 7 giorni → saluto caloroso con "NICE_TO_MEET_OKABE" o simile
        if (daysSinceLast > 7) {
            Amadeus.speak(voiceLines[VoiceLine.Line.LOOKING_FORWARD_TO_WORKING], this);
            return;
        }

        // Saluto basato sull'ora del giorno
        if (hour >= 6 && hour < 12) {
            // Mattina
            Amadeus.speak(voiceLines[VoiceLine.Line.HELLO], this);
        } else if (hour >= 12 && hour < 18) {
            // Pomeriggio — saluto un po' più neutro
            Amadeus.speak(voiceLines[VoiceLine.Line.COULD_I_HELP], this);
        } else if (hour >= 18 && hour < 23) {
            // Sera
            Amadeus.speak(voiceLines[VoiceLine.Line.WHAT_IS_IT], this);
        } else {
            // Notte tarda (23–6): Kurisu assonnata
            Amadeus.speak(voiceLines[VoiceLine.Line.WHAT_DO_YOU_WANT], this);
        }
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (sr != null) {
                sr.destroy();
            }
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new Listener());
        }
    }

    private void requestAppPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    initSpeechRecognizer();
                }
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangContext.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        if (Amadeus.m != null) {
            try {
                if (Amadeus.m.isPlaying()) Amadeus.m.stop();
            } catch (Exception ignored) {
            }
            Amadeus.m.release();
            Amadeus.m = null;
        }
        if (inactivityHandler != null && inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Amadeus.isLoop = false;
        if (inactivityHandler != null && inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Amadeus.isLoop = false;
        if (inactivityHandler != null && inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        recogLang = settings.getString("recognition_lang", getString(R.string.default_recog_lang));
        contextLang = recogLang.split("-");
        resetInactivityTimer();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
            blinkHandler.postDelayed(blinkRunnable, 3000);
        }
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recogLang);

        if (sr != null) {
            sr.startListening(intent);
        } else {
            try {
                startActivityForResult(intent, 1);
            } catch (ActivityNotFoundException a) {
                Toast.makeText(this, R.string.google_app_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK && data != null) {
                resetInactivityTimer();
                Context context = LangContext.load(getApplicationContext(), contextLang[0]);
                ArrayList<String> input = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (input != null && !input.isEmpty()) {
                    String text = input.get(0);
                    // Riconoscimento nome anche dal fallback activity-based
                    String myNameTrigger = context.getString(R.string.my_name_is);
                    if (Amadeus.containInput(text, myNameTrigger)) {
                        String detectedName = extractNameFromInput(text.toLowerCase(), myNameTrigger);
                        if (detectedName != null && !detectedName.isEmpty()) {
                            memory.setUserName(detectedName);
                            Amadeus.speak(voiceLines[VoiceLine.Line.I_SEE], MainActivity.this);
                            return;
                        }
                    }

                    // ── Ricerca Web su Google: "cerca [argomento]" ───────────────
                    String searchTrigger = context.getString(R.string.search);
                    if (Amadeus.containInput(text, searchTrigger)) {
                        String query = extractSearchQuery(text, searchTrigger);
                        if (query != null && !query.isEmpty()) {
                            Amadeus.speak(voiceLines[VoiceLine.Line.OK], MainActivity.this);
                            try {
                                Intent searchIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                                startActivity(searchIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Search intent error", e);
                            }
                            return;
                        }
                    }

                    Amadeus.responseToInput(text, context, MainActivity.this, memory);
                }
            }
        }
    }

    private class Listener implements RecognitionListener {

        private final String TAG = "VoiceListener";

        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Speech recognition ready");
        }

        public void onBeginningOfSpeech() {
            Log.d(TAG, "Speech recognition listening");
        }

        public void onRmsChanged(float rmsdB) {
        }

        public void onBufferReceived(byte[] buffer) {
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "Speech recognition ended");
        }

        public void onError(int error) {
            Log.d(TAG, "Speech recognition error: " + error);
            if (sr != null) {
                sr.cancel();
            }
            Amadeus.speak(voiceLines[VoiceLine.Line.SORRY], MainActivity.this);
        }

        public void onResults(Bundle results) {
            if (results == null) return;
            ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data == null || data.isEmpty()) return;

            resetInactivityTimer();

            String input = data.get(0);
            Log.d(TAG, "Recognition input: " + input);

            String[] splitInput = input.split(" ");
            if (splitInput.length > 0 && splitInput[0].equalsIgnoreCase("Асистент")) {
                splitInput[0] = "Ассистент";
            }

            Context context = LangContext.load(getApplicationContext(), contextLang[0]);

            // ── Riconoscimento nome: "mi chiamo [nome]" ───────────────────────
            String myNameTrigger = context.getString(R.string.my_name_is);
            if (Amadeus.containInput(input, myNameTrigger)) {
                String detectedName = extractNameFromInput(input.toLowerCase(), myNameTrigger);
                if (detectedName != null && !detectedName.isEmpty()) {
                    memory.setUserName(detectedName);
                    Log.d(TAG, "Name saved: " + detectedName);
                    Amadeus.speak(voiceLines[VoiceLine.Line.I_SEE], MainActivity.this);
                    return;
                }
            }

            // ── Ricerca Web su Google: "cerca [argomento]" ───────────────────
            String searchTrigger = context.getString(R.string.search);
            if (Amadeus.containInput(input, searchTrigger)) {
                String query = extractSearchQuery(input, searchTrigger);
                if (query != null && !query.isEmpty()) {
                    Amadeus.speak(voiceLines[VoiceLine.Line.OK], MainActivity.this);
                    try {
                        Intent searchIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                        startActivity(searchIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Search intent error", e);
                    }
                    return;
                }
            }

            if (splitInput.length > 2 && Amadeus.containInput(splitInput[0], context.getString(R.string.assistant))) {
                String cmd = splitInput[1].toLowerCase();
                String[] args = new String[splitInput.length - 2];
                System.arraycopy(splitInput, 2, args, 0, splitInput.length - 2);

                if (Amadeus.containInput(cmd, context.getString(R.string.open))) {
                    Amadeus.openApp(args, MainActivity.this);
                }
            } else {
                Amadeus.responseToInput(input, context, MainActivity.this, memory);
            }
        }

        public void onPartialResults(Bundle partialResults) {
        }

        public void onEvent(int eventType, Bundle params) {
        }
    }

    /**
     * Estrae la query di ricerca dopo trigger come "cerca", "cerca su google", ecc.
     * Es. "cerca ricetta carbonara" → "ricetta carbonara"
     */
    private String extractSearchQuery(String input, String triggers) {
        String clean = input.toLowerCase();
        String[] variants = triggers.split("[,;]");
        for (String v : variants) {
            String trigger = v.trim().toLowerCase();
            if (!trigger.isEmpty() && clean.contains(trigger)) {
                int index = clean.indexOf(trigger) + trigger.length();
                if (index < input.length()) {
                    String query = input.substring(index).trim();
                    query = query.replaceFirst("^(su\\s+internet|su\\s+google|sul\\s+web|per|di|del|della|il|la|un|una)\\s+", "");
                    if (!query.isEmpty()) {
                        return query.trim();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Estrae il nome dall'input vocale cercando i trigger definiti in my_name_is.
     * Es. "mi chiamo Luca" → "Luca"
     */
    private String extractNameFromInput(String input, String triggers) {
        String[] variants = triggers.split("[,;]");
        for (String trigger : variants) {
            String t = trigger.trim().toLowerCase();
            if (!t.isEmpty() && input.contains(t)) {
                String afterTrigger = input.substring(input.indexOf(t) + t.length()).trim();
                // Prendi la prima parola dopo il trigger come nome
                String[] words = afterTrigger.split("\\s+");
                if (words.length > 0 && !words[0].isEmpty()) {
                    // Capitalizza la prima lettera
                    String name = words[0];
                    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
                }
            }
        }
        return null;
    }

    // ── Inactivity Reaction ───────────────────────────────────────────────────
    private void initInactivityTimer() {
        inactivityHandler = new Handler(Looper.getMainLooper());
        inactivityRunnable = new Runnable() {
            @Override
            public void run() {
                if (!Amadeus.isSpeaking && !Amadeus.isLoop && !isFinishing() && !isDestroyed()) {
                    VoiceLine[] idleLines = new VoiceLine[]{
                            voiceLines[VoiceLine.Line.COULD_I_HELP],
                            voiceLines[VoiceLine.Line.WHAT_IS_IT],
                            voiceLines[VoiceLine.Line.WHAT_DO_YOU_WANT],
                            voiceLines[VoiceLine.Line.SENPAI_QUESTIONMARK],
                            voiceLines[VoiceLine.Line.SENPAI_WHAT_WE_TALKING]
                    };
                    Amadeus.speak(idleLines[randomgen.nextInt(idleLines.length)], MainActivity.this);
                }
                inactivityHandler.postDelayed(this, INACTIVITY_DELAY_MS);
            }
        };
    }

    private void resetInactivityTimer() {
        if (inactivityHandler != null && inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
            inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_DELAY_MS);
        }
    }

    // ── Idle Eye Blinking ─────────────────────────────────────────────────────
    private void initBlink() {
        blinkHandler = new Handler(Looper.getMainLooper());
        blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!Amadeus.isSpeaking && !Amadeus.isLoop && kurisuImageView != null && !isFinishing() && !isDestroyed()) {
                    kurisuImageView.setImageResource(R.drawable.kurisu_eyes_closed);
                    blinkHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!Amadeus.isSpeaking && !Amadeus.isLoop && kurisuImageView != null && !isFinishing() && !isDestroyed()) {
                                kurisuImageView.setImageResource(R.drawable.kurisu_normal);
                            }
                        }
                    }, 180);
                }
                long nextBlink = 3500 + randomgen.nextInt(4000);
                blinkHandler.postDelayed(this, nextBlink);
            }
        };
    }

    // ── Shake Sensor ──────────────────────────────────────────────────────────
    private void initShakeSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        accelCurrent = SensorManager.GRAVITY_EARTH;
        accelLast = SensorManager.GRAVITY_EARTH;
        shake = 0.00f;
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            accelLast = accelCurrent;
            accelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = accelCurrent - accelLast;
            shake = shake * 0.9f + delta;

            if (shake > 12) {
                long now = System.currentTimeMillis();
                if (now - lastShakeTime > 3000) {
                    lastShakeTime = now;
                    resetInactivityTimer();
                    if (!Amadeus.isSpeaking && !Amadeus.isLoop) {
                        VoiceLine[] shakeLines = new VoiceLine[]{
                                voiceLines[VoiceLine.Line.GAH],
                                voiceLines[VoiceLine.Line.UH_SENPAI],
                                voiceLines[VoiceLine.Line.GAH_EXTENDED]
                        };
                        Amadeus.speak(shakeLines[randomgen.nextInt(shakeLines.length)], MainActivity.this);
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
