package com.example.yink.amadeus;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

class Amadeus {

    static Boolean isSpeaking = false;
    static Boolean isLoop = false;
    static MediaPlayer m;
    private static Visualizer currentVisualizer;
    private static final String TAG = "Amadeus";
    private static int shaman_girls = -1;
    private static final VoiceLine[] voiceLines = VoiceLine.Line.getLines();
    private static final HashMap<Bundle<Integer>, Bundle<VoiceLine>> responseInputMap = new HashMap<>();

    static {
        responseInputMap.put(new Bundle<>(
                R.string.christina
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.CHRISTINA],
                voiceLines[VoiceLine.Line.WHY_CHRISTINA],
                voiceLines[VoiceLine.Line.SHOULD_CHRISTINA],
                voiceLines[VoiceLine.Line.NO_TINA]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.the_zombie, R.string.the_zombie2,
                R.string.celeb17
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.DONT_CALL_ME_LIKE_THAT]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.atchannel,
                R.string.kurigohan,
                R.string.kamehameha
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.SENPAI_DONT_TELL],
                voiceLines[VoiceLine.Line.STILL_NOT_HAPPY]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.salieri,
                R.string.maho,
                R.string.hiyajo
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.SENPAI_QUESTION],
                voiceLines[VoiceLine.Line.SENPAI_WHAT_WE_TALKING],
                voiceLines[VoiceLine.Line.SENPAI_QUESTIONMARK],
                voiceLines[VoiceLine.Line.SENPAI_WHO_IS_THIS]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.time_machine, R.string.time_travel2,
                R.string.cern,
                R.string.time_travel
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.TM_NONCENCE],
                voiceLines[VoiceLine.Line.TM_YOU_SAID],
                voiceLines[VoiceLine.Line.TM_NO_EVIDENCE],
                voiceLines[VoiceLine.Line.TM_DONT_KNOW],
                voiceLines[VoiceLine.Line.TM_NOT_POSSIBLE]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.memory,
                R.string.amadeus,
                R.string.science
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.HUMANS_SOFTWARE],
                voiceLines[VoiceLine.Line.MEMORY_COMPLEXITY],
                voiceLines[VoiceLine.Line.SECRET_DIARY],
                voiceLines[VoiceLine.Line.MODIFIYING_MEMORIES],
                voiceLines[VoiceLine.Line.MEMORIES_CHRISTINA]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.hello,
                R.string.good_morning,
                R.string.konnichiwa,
                R.string.good_evening
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.HELLO],
                voiceLines[VoiceLine.Line.NICE_TO_MEET_OKABE],
                voiceLines[VoiceLine.Line.PLEASED_TO_MEET],
                voiceLines[VoiceLine.Line.LOOKING_FORWARD_TO_WORKING]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.nice_body,
                R.string.hot,
                R.string.sexy,
                R.string.boobies,
                R.string.oppai,
                R.string.pervert
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.DEVILISH_PERVERT],
                voiceLines[VoiceLine.Line.PERVERT_CONFIRMED],
                voiceLines[VoiceLine.Line.PERVERT_IDIOT],
                voiceLines[VoiceLine.Line.PERVERTS_GO_TO_HELL]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.i_love_you
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.HEHEHE],
                voiceLines[VoiceLine.Line.WHY_SAY_THAT],
                voiceLines[VoiceLine.Line.STILL_NOT_HAPPY],
                voiceLines[VoiceLine.Line.DEVILISH_PERVERT]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.how_are_you
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.ASK_ME],
                voiceLines[VoiceLine.Line.I_GUESS],
                voiceLines[VoiceLine.Line.SOUNDS_TOUGH],
                voiceLines[VoiceLine.Line.COULD_I_HELP]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.laugh
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.WHATS_SO_FUNNY],
                voiceLines[VoiceLine.Line.HEHEHE]
        ));
        responseInputMap.put(new Bundle<>(
                R.string.robotics_notes,
                R.string.antimatter
        ), new Bundle<>(
                voiceLines[VoiceLine.Line.HEHEHE]
        ));
    }

    private static void releaseVisualizer() {
        if (currentVisualizer != null) {
            try {
                currentVisualizer.setEnabled(false);
                currentVisualizer.release();
            } catch (Exception ignored) {
            }
            currentVisualizer = null;
        }
    }

    static void speak(VoiceLine line, final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        final TextView subtitles = (TextView) activity.findViewById(R.id.textView_subtitles);
        final ImageView kurisu = (ImageView) activity.findViewById(R.id.imageView_kurisu);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);

        try {
            releaseVisualizer();

            if (m != null) {
                try {
                    if (m.isPlaying()) m.stop();
                } catch (Exception ignored) {
                }
                m.release();
                m = null;
            }

            m = MediaPlayer.create(activity, line.getId());
            if (m == null) return;

            if (settings.getBoolean("show_subtitles", false) && subtitles != null) {
                subtitles.setText(line.getSubtitle());
            }

            Resources res = activity.getResources();
            final AnimationDrawable animation = (AnimationDrawable) Drawable.createFromXml(res, res.getXml(line.getMood()));

            boolean hasAudioPermission = ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

            if (hasAudioPermission) {
                try {
                    currentVisualizer = new Visualizer(m.getAudioSessionId());
                    currentVisualizer.setEnabled(false);
                    currentVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                    currentVisualizer.setDataCaptureListener(
                            new Visualizer.OnDataCaptureListener() {
                                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                                    if (bytes == null || bytes.length == 0) return;
                                    int sum = 0;
                                    for (int i = 1; i < bytes.length; i++) {
                                        sum += bytes[i] + 128;
                                    }
                                    final float normalized = sum / (float) bytes.length;

                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (kurisu != null && animation != null) {
                                                if (normalized > 50) {
                                                    kurisu.setImageDrawable(animation.getFrame((int) Math.ceil(Math.random() * 2)));
                                                } else {
                                                    kurisu.setImageDrawable(animation.getFrame(0));
                                                }
                                            }
                                        }
                                    });
                                }

                                public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                                }
                            }, Visualizer.getMaxCaptureRate() / 2, true, false);
                } catch (Exception e) {
                    Log.w(TAG, "Visualizer could not be initialized: " + e.getMessage());
                    releaseVisualizer();
                }
            }

            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isSpeaking = true;
                    mp.start();
                    if (currentVisualizer != null) {
                        try {
                            currentVisualizer.setEnabled(true);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });

            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    isSpeaking = false;
                    releaseVisualizer();
                    try {
                        mp.release();
                    } catch (Exception ignored) {
                    }
                    if (m == mp) m = null;

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (kurisu != null && animation != null) {
                                kurisu.setImageDrawable(animation.getFrame(0));
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in speak: ", e);
        }
    }

    static void responseToInput(String input, Context context, Activity activity) {
        responseToInput(input, context, activity, null);
    }

    static void responseToInput(String input, Context context, Activity activity, AmadeusMemory memory) {
        VoiceLine[] specificLines = null;
        input = input.toLowerCase();

        if (containInput(input, context.getString(R.string.nullpo))) {
            shaman_girls++;
            if (shaman_girls < 5) {
                specificLines = new VoiceLine[]{
                        voiceLines[VoiceLine.Line.GAH],
                        voiceLines[VoiceLine.Line.GAH_EXTENDED]
                };
            } else {
                VoiceLine singleLine;
                switch (shaman_girls) {
                    case 5:
                        singleLine = new VoiceLine(R.raw.leskinen_awesome, VoiceLine.Mood.WINKING, R.string.line_Leskinen_awesome);
                        break;
                    case 6:
                        singleLine = new VoiceLine(R.raw.leskinen_nice, VoiceLine.Mood.WINKING, R.string.line_Leskinen_nice);
                        break;
                    case 7:
                        singleLine = new VoiceLine(R.raw.leskinen_oh_no, VoiceLine.Mood.WINKING, R.string.line_Leskinen_oh_no);
                        break;
                    case 8:
                        singleLine = new VoiceLine(R.raw.leskinen_shaman, VoiceLine.Mood.WINKING, R.string.line_Leskinen_shaman);
                        break;
                    case 9:
                    default:
                        singleLine = new VoiceLine(R.raw.leskinen_holy_cow, VoiceLine.Mood.WINKING, R.string.line_Leskinen_holy_cow);
                        shaman_girls = 0;
                        break;
                }
                specificLines = new VoiceLine[]{singleLine};
            }
        } else {
            for (Bundle<Integer> input_bundle : responseInputMap.keySet()) {
                for (Integer input_code : input_bundle) {
                    if (containInput(input, context.getString(input_code))) {
                        Bundle<VoiceLine> res = responseInputMap.get(input_bundle);
                        if (res != null) {
                            specificLines = res.toArray();
                        }
                        // Traccia il contatore Christina nella memoria persistente
                        if (input_code == R.string.christina && memory != null) {
                            memory.incrementChristinaTotal();
                            Log.d(TAG, "Christina total: " + memory.getChristinaTotal());
                        }
                        break;
                    }
                }
                if (specificLines != null) break;
            }

            if (specificLines == null) {
                specificLines = new VoiceLine[]{
                        voiceLines[VoiceLine.Line.ASK_ME],
                        voiceLines[VoiceLine.Line.WHAT_DO_YOU_WANT],
                        voiceLines[VoiceLine.Line.WHAT_IS_IT],
                        voiceLines[VoiceLine.Line.HEHEHE],
                        voiceLines[VoiceLine.Line.WHY_SAY_THAT],
                        voiceLines[VoiceLine.Line.YOU_SURE]
                };
            }
        }
        int intTarget = 0;
        if (specificLines.length > 1) {
            intTarget = new Random().nextInt(specificLines.length);
        }
        Amadeus.speak(specificLines[intTarget], activity);
    }

    static boolean containInput(final String input, final String... strings) {
        if (input == null) return false;
        String cleanInput = input.toLowerCase().replaceAll("[.,?!¿¡:;]", " ").trim();
        // Normalizzazione anti-censura automatica per parole censurate da Google Speech Recognition
        cleanInput = cleanInput.replaceAll("t[\\*]+e?", "tette");
        cleanInput = cleanInput.replaceAll("f[\\*]+a?", "figa");
        cleanInput = cleanInput.replaceAll("c[\\*]+o?", "cazzo");
        for (String s : strings) {
            if (s == null) continue;
            String[] variants = s.split("[,;]");
            for (String variant : variants) {
                String trimmed = variant.trim().toLowerCase();
                if (!trimmed.isEmpty() && cleanInput.contains(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void openApp(String[] input, Activity activity) {
        final PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        HashMap<String, Integer> dictionary = new HashMap<>();
        String corrected;
        boolean found;

        dictionary.put("хром", 0);
        dictionary.put("календарь", 1);
        dictionary.put("часы", 2);
        dictionary.put("будильник", 2);
        dictionary.put("камеру", 3);

        // Italian app triggers
        dictionary.put("chrome", 0);
        dictionary.put("google", 0);
        dictionary.put("calendario", 1);
        dictionary.put("orologio", 2);
        dictionary.put("sveglia", 2);
        dictionary.put("fotocamera", 3);
        dictionary.put("camera", 3);

        String[] apps = {
                "chrome", "calendar", "clock", "camera"
        };

        for (ApplicationInfo packageInfo : packages) {
            found = true;
            for (String word : input) {
                if (dictionary.get(word) != null) {
                    corrected = apps[dictionary.get(word)].toLowerCase();
                } else {
                    corrected = word.toLowerCase();
                }
                if (!packageInfo.packageName.contains(corrected)) {
                    found = false;
                    break;
                }
            }

            if (found) {
                Log.d(TAG, "Found app: " + packageInfo.packageName);
                Intent app;
                Amadeus.speak(voiceLines[VoiceLine.Line.OK], activity);
                switch (packageInfo.packageName) {
                    case "com.android.phone": {
                        app = new Intent(Intent.ACTION_DIAL, null);
                        activity.startActivity(app);
                        break;
                    }
                    case "com.android.chrome": {
                        app = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                        app.setPackage(packageInfo.packageName);
                        activity.startActivity(app);
                        break;
                    }
                    default: {
                        app = activity.getPackageManager().getLaunchIntentForPackage(packageInfo.packageName);
                        if (app != null) {
                            app.addCategory(Intent.CATEGORY_LAUNCHER);
                            activity.startActivity(app);
                        }
                        break;
                    }
                }
                break;
            }
        }
    }

    private static class Bundle<T> implements Iterable<T> {

        private final T[] list;

        @SafeVarargs
        public Bundle(T... list) {
            this.list = list;
        }

        public T[] toArray() {
            return list;
        }

        @NonNull
        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                int index = 0;

                @Override
                public boolean hasNext() {
                    return index < list.length;
                }

                @Override
                public T next() {
                    return list[index++];
                }
            };
        }
    }
}
