/*
 * Copyright (C) 2015-2016 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.smartpack.kernelmanager.utils.kernel.cpu;

import android.content.Context;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.smartpack.kernelmanager.R;
import com.smartpack.kernelmanager.fragments.ApplyOnBootFragment;
import com.smartpack.kernelmanager.utils.Device;
import com.smartpack.kernelmanager.utils.Prefs;
import com.smartpack.kernelmanager.utils.Utils;
import com.smartpack.kernelmanager.utils.kernel.cpuhotplug.CoreCtl;
import com.smartpack.kernelmanager.utils.kernel.cpuhotplug.MPDecision;
import com.smartpack.kernelmanager.utils.kernel.cpuhotplug.QcomBcl;
import com.smartpack.kernelmanager.utils.root.Control;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by willi on 19.04.16.
 */
public class CPUFreq {

    private static CPUFreq sInstance;

    public static CPUFreq getInstance() {
        return getInstance(null);
    }

    public static CPUFreq getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CPUFreq(context);
        }
        return sInstance;
    }

    private static final String CPU_PRESENT = "/sys/devices/system/cpu/present";
    private static final String CUR_FREQ = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq";
    private static final String AVAILABLE_FREQS = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_available_frequencies";
    public static final String TIME_STATE = "/sys/devices/system/cpu/cpufreq/stats/cpu%d/time_in_state";
    public static final String TIME_STATE_2 = "/sys/devices/system/cpu/cpu%d/cpufreq/stats/time_in_state";
    private static final String OPP_TABLE = "/sys/devices/system/cpu/cpu%d/opp_table";

    private static final String CPU_MAX_FREQ = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq";
    private static final String CPU_MAX_FREQ_KT = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq_kt";
    private static final String HARD_CPU_MAX_FREQ = "/sys/kernel/cpufreq_hardlimit/scaling_max_freq";
    private static final String CPU_MIN_FREQ = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq";
    private static final String HARD_CPU_MIN_FREQ = "/sys/kernel/cpufreq_hardlimit/scaling_min_freq";
    private static final String CPU_MAX_SCREEN_OFF_FREQ = "/sys/devices/system/cpu/cpu%d/cpufreq/screen_off_max_freq";
    public static final String CPU_ONLINE = "/sys/devices/system/cpu/cpu%d/online";
    private static final String CPU_MSM_CPUFREQ_LIMIT = "/sys/kernel/msm_cpufreq_limit/cpufreq_limit";
    private static final String CPU_ENABLE_OC = "/sys/devices/system/cpu/cpu%d/cpufreq/enable_oc";
    public static final String CPU_LOCK_FREQ = "/sys/kernel/cpufreq_hardlimit/userspace_dvfs_lock";
    private static final String CPU_SCALING_GOVERNOR = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor";
    private static final String CPU_AVAILABLE_GOVERNORS = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_available_governors";
    private static final String CPU_GOVERNOR_TUNABLES = "/sys/devices/system/cpu/cpufreq/%s";
    private static final String CPU_GOVERNOR_TUNABLES_CORE = "/sys/devices/system/cpu/cpu%d/cpufreq/%s";

    private static final String EGO_GOVERNOR_TUNABLES_GROUP = "/sys/devices/platform/ems/ego/coregroup%d";

    private static final String CPU_POLICY0 = "/sys/devices/system/cpu/cpufreq/policy0";
    private static final String CPU_POLICY6 = "/sys/devices/system/cpu/cpufreq/policy6";
    private static final String CPU_POLICY0_MAX_FREQ = CPU_POLICY0 + "/scaling_max_freq";
    private static final String CPU_POLICY6_MAX_FREQ = CPU_POLICY6 + "/scaling_max_freq";

    private int mCpuCount;
    private int mBigCpu = -1;
    private int mMidCpu = -1;
    private int mLITTLECpu = -1;
    public int mCoreCtlMinCpu = 2;
    private final SparseArray<List<Integer>> sFreqs = new SparseArray<>();
    private String[] sGovernors;

    private CPUFreq(Context context) {
        if (context != null) {
            mCoreCtlMinCpu = Prefs.getInt("core_ctl_min_cpus_big", 2, context);
        }
    }

    public String getGovernorTunablesPath(int cpu, String governor) {
        if (governor.equals("energy_aware")) {
            return EGO_GOVERNOR_TUNABLES_GROUP;
        }
        if (Utils.existFile(Utils.strFormat(CPU_GOVERNOR_TUNABLES_CORE, cpu, governor))) {
            return CPU_GOVERNOR_TUNABLES_CORE.replace("%s", governor);
        } else {
            return Utils.strFormat(CPU_GOVERNOR_TUNABLES, governor);
        }
    }

    public boolean isOffline(int cpu) {
        return getCurFreq(cpu) == 0 || !isCPUOnline(cpu);
    }

    public void applyCpu(String path, String value, int min, int max, Context context) {
        boolean cpulock = Utils.existFile(CPU_LOCK_FREQ);
        if (cpulock) {
            run(Control.write("0", CPU_LOCK_FREQ), null, null);
        }
        boolean mpdecision = MPDecision.supported() && MPDecision.isMpdecisionEnabled();
        if (mpdecision) {
            MPDecision.enableMpdecision(false, null);
        }
        for (int i = min; i <= max; i++) {
            boolean offline = isOffline(i);
            if (offline) {
                onlineCpu(i, true, false, null);
            }
            run(Control.chmod("644", Utils.strFormat(path, i)), null, null);
            run(Control.write(value, Utils.strFormat(path, i)), null, null);
            run(Control.chmod("444", Utils.strFormat(path, i)), null, null);
            if (offline) {
                onlineCpu(i, false, false, null);
            }
        }
        if (mpdecision) {
            MPDecision.enableMpdecision(true, null);
        }
        if (cpulock) {
            run(Control.write("1", CPU_LOCK_FREQ), null, null);
        }
        if (context != null) {
            if (isBigLITTLE()) {
                List<Integer> bigCpus = getBigCpuRange();
                List<Integer> midCpus = getMidCpuRange();
                List<Integer> littleCpus = getLITTLECpuRange();
                run("#" + new ApplyCpu(path, value, min, max, bigCpus.toArray(new Integer[0]),
                        midCpus.toArray(new Integer[0]),
                        littleCpus.toArray(new Integer[0]),
                        mCoreCtlMinCpu).toString(), path + min, context);
            } else {
                run("#" + new ApplyCpu(path, value, min, max).toString(), path + min, context);
            }
        }
    }

    public static class ApplyCpu {
        private String mJson;
        private String mPath;
        private String mValue;
        private int mMin;
        private int mMax;

        // big.LITTLE
        private List<Integer> mBigCpus;
        private List<Integer> mMidCpus;
        private List<Integer> mLITTLECpus;
        private int mCoreCtlMin;

        private ApplyCpu(String path, String value, int min, int max) {
            try {
                JSONObject main = new JSONObject();
                init(main, path, value, min, max);
                mJson = main.toString();
            } catch (JSONException ignored) {
            }
        }

        private ApplyCpu(String path, String value, int min, int max, Integer[] bigCpus,
                         Integer[] midCpus, Integer[] littleCpus, int corectlmin) {
            try {
                JSONObject main = new JSONObject();
                init(main, path, value, min, max);

                // big.LITTLE
                JSONArray bigCpusArray = new JSONArray();
                for (int cpu : bigCpus) {
                    bigCpusArray.put(cpu);
                }
                main.put("bigCpus", bigCpusArray);
                mBigCpus = Arrays.asList(bigCpus);

                JSONArray MidCpusArray = new JSONArray();
                for (int cpu : midCpus) {
                    MidCpusArray.put(cpu);
                }
                main.put("MidCpus", MidCpusArray);
                mMidCpus = Arrays.asList(midCpus);

                JSONArray LITTLECpusArray = new JSONArray();
                for (int cpu : littleCpus) {
                    LITTLECpusArray.put(cpu);
                }
                main.put("LITTLECpus", LITTLECpusArray);
                mLITTLECpus = Arrays.asList(littleCpus);

                main.put("corectlmin", mCoreCtlMin = corectlmin);

                mJson = main.toString();
            } catch (JSONException ignored) {
            }
        }

        private void init(JSONObject main, String path, String value, int min, int max)
                throws JSONException {
            main.put("path", mPath = path);
            main.put("value", mValue = value);
            main.put("min", mMin = min);
            main.put("max", mMax = max);
        }

        public ApplyCpu(String json) {
            try {
                JSONObject main = new JSONObject(json);
                mPath = getString(main, "path");
                mValue = getString(main, "value");
                mMin = getInt(main, "min");
                mMax = getInt(main, "max");

                // big.LITTLE
                Integer[] bigCpus = getIntArray(main, "bigCpus");
                if (bigCpus != null) {
                    mBigCpus = Arrays.asList(bigCpus);
                }

                Integer[] MidCpus = getIntArray(main, "MidCpus");
                if (MidCpus != null) {
                    mMidCpus = Arrays.asList(MidCpus);
                }

                Integer[] LITTLECpus = getIntArray(main, "LITTLECpus");
                if (LITTLECpus != null) {
                    mLITTLECpus = Arrays.asList(LITTLECpus);
                }

                mCoreCtlMin = getInt(main, "corectlmin");

                mJson = json;
            } catch (JSONException ignored) {
            }
        }

        private Integer[] getIntArray(JSONObject jsonObject, String key) {
            try {
                JSONArray array = jsonObject.getJSONArray(key);
                Integer[] integers = new Integer[array.length()];
                for (int i = 0; i < integers.length; i++) {
                    integers[i] = array.getInt(i);
                }
                return integers;
            } catch (JSONException ignored) {
                return null;
            }
        }

        private String getString(JSONObject jsonObject, String key) {
            try {
                return jsonObject.getString(key);
            } catch (JSONException ignored) {
                return null;
            }
        }

        private int getInt(JSONObject jsonObject, String key) {
            try {
                return jsonObject.getInt(key);
            } catch (JSONException ignored) {
                return -1;
            }
        }

        public int getCoreCtlMin() {
            return mCoreCtlMin;
        }

        public List<Integer> getLITTLECpuRange() {
            return mLITTLECpus;
        }

        public List<Integer> getMidCpuRange() {
            return mMidCpus;
        }

        public List<Integer> getBigCpuRange() {
            return mBigCpus;
        }

        public boolean isBigLITTLE() {
            return getBigCpuRange() != null && getLITTLECpuRange() != null;
        }

        public int getMax() {
            return mMax;
        }

        public int getMin() {
            return mMin;
        }

        public String getValue() {
            return mValue;
        }

        public String getPath() {
            return mPath;
        }

        @NonNull
        public String toString() {
            return mJson;
        }
    }

    public void setGovernor(String governor, int min, int max, Context context) {
        applyCpu(CPU_SCALING_GOVERNOR, governor, min, max, context);
    }

    public String getGovernor(boolean forceRead) {
        return getGovernor(getBigCpu(), forceRead);
    }

    public String getGovernor(int cpu, boolean forceRead) {
        boolean offline = forceRead && isOffline(cpu);
        if (offline) {
            onlineCpu(cpu, true, false, null);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String value = "";
        if (Utils.existFile(Utils.strFormat(CPU_SCALING_GOVERNOR, cpu))) {
            value = Utils.readFile(Utils.strFormat(CPU_SCALING_GOVERNOR, cpu));
        }

        if (offline) {
            onlineCpu(cpu, false, false, null);
        }
        return value;
    }

    public List<String> getGovernors() {
        if (sGovernors == null) {
            boolean offline = isOffline(0);
            if (offline) {
                onlineCpu(0, true, false, null);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (Utils.existFile(Utils.strFormat(CPU_AVAILABLE_GOVERNORS, 0))) {
                String value = Utils.readFile(Utils.strFormat(CPU_AVAILABLE_GOVERNORS, 0));
                if (value != null) {
                    sGovernors = value.split(" ");
                }
            }

            if (offline) {
                onlineCpu(0, false, false, null);
            }
        }
        if (sGovernors == null) return getGovernors();
        return Arrays.asList(sGovernors);
    }

    private int getFreq(int cpu, String path, boolean forceRead) {
        boolean offline = forceRead && isOffline(cpu);
        if (offline) {
            onlineCpu(cpu, true, false, null);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int freq = 0;
        String value = Utils.readFile(Utils.strFormat(path, cpu));
        if (value != null) freq = Utils.strToInt(value);

        if (offline) {
            onlineCpu(cpu, false, false, null);
        }
        return freq;
    }

    public void setMaxScreenOffFreq(int freq, int min, int max, Context context) {
        applyCpu(CPU_MAX_SCREEN_OFF_FREQ, String.valueOf(freq), min, max, context);
    }

    public int getMaxScreenOffFreq(boolean forceRead) {
        return getMaxScreenOffFreq(getBigCpu(), forceRead);
    }

    public int getMaxScreenOffFreq(int cpu, boolean forceRead) {
        return getFreq(cpu, CPU_MAX_SCREEN_OFF_FREQ, forceRead);
    }

    public boolean hasMaxScreenOffFreq() {
        return hasMaxScreenOffFreq(getBigCpu());
    }

    public boolean hasMaxScreenOffFreq(int cpu) {
        return Utils.existFile(Utils.strFormat(CPU_MAX_SCREEN_OFF_FREQ, cpu));
    }

    private static int cpuPolicyMax(String cpu) {
        if (Utils.existFile(cpu)) {
            return Utils.strToInt(Utils.readFile(cpu));
        }
        return 0;
    }

    public void setMinFreq(int freq, int min, int max, Context context) {
        MSMPerformance msmPerformance = MSMPerformance.getInstance();
        int maxFreq = getMaxFreq(min, false);

        if (maxFreq != 0 && freq > maxFreq) {
            setMaxFreq(freq, min, max, context);
        }
        if (msmPerformance.hasCpuMinFreq()) {
            for (int i = min; i <= max; i++) {
                msmPerformance.setCpuMinFreq(freq, i, context);
            }
        }
        applyCpu(CPU_MIN_FREQ, String.valueOf(freq), min, max, context);
        if (Utils.existFile(HARD_CPU_MIN_FREQ)) {
            run(Control.write(String.valueOf(freq), HARD_CPU_MIN_FREQ), HARD_CPU_MIN_FREQ, context);
        }
    }

    public int getMinFreq(boolean forceRead) {
        return getMinFreq(getBigCpu(), forceRead);
    }

    public int getMinFreq(int cpu, boolean forceRead) {
        return getFreq(cpu, CPU_MIN_FREQ, forceRead);
    }

    public void setMaxFreq(int freq, int min, int max, Context context) {
        MSMPerformance msmPerformance = MSMPerformance.getInstance();

        if (Utils.existFile(CPU_MSM_CPUFREQ_LIMIT) && freq > Utils.strToInt(Utils.readFile(CPU_MSM_CPUFREQ_LIMIT))) {
            run(Control.write(String.valueOf(freq), CPU_MSM_CPUFREQ_LIMIT), CPU_MSM_CPUFREQ_LIMIT, context);
        }
        int minFreq = getMinFreq(min, false);
        if (minFreq != 0 && freq < minFreq) {
            setMinFreq(freq, min, max, context);
        }
        if (Utils.existFile(Utils.strFormat(CPU_ENABLE_OC, 0))) {
            for (int i = min; i <= max; i++) {
                run(Control.write("1", Utils.strFormat(CPU_ENABLE_OC, i)),
                        Utils.strFormat(CPU_ENABLE_OC, i), context);
            }
        }
        if (msmPerformance.hasCpuMaxFreq()) {
            for (int i = min; i <= max; i++) {
                msmPerformance.setCpuMaxFreq(freq, i, context);
            }
        }
        if (Utils.existFile(Utils.strFormat(CPU_MAX_FREQ_KT, 0))) {
            applyCpu(CPU_MAX_FREQ_KT, String.valueOf(freq), min, max, context);
        } else {
            applyCpu(CPU_MAX_FREQ, String.valueOf(freq), min, max, context);
        }
        if (Utils.existFile(HARD_CPU_MAX_FREQ)) {
            run(Control.write(String.valueOf(freq), HARD_CPU_MAX_FREQ), HARD_CPU_MAX_FREQ, context);
        }
    }

    public int getMaxFreq(boolean forceRead) {
        return getMaxFreq(getBigCpu(), forceRead);
    }

    public int getMaxFreq(int cpu, boolean forceRead) {
        if (Utils.existFile(Utils.strFormat(CPU_MAX_FREQ_KT, cpu))) {
            return getFreq(cpu, CPU_MAX_FREQ_KT, forceRead);
        } else {
            return getFreq(cpu, CPU_MAX_FREQ, forceRead);
        }
    }

    public List<String> getAdjustedFreq(Context context) {
        return getAdjustedFreq(getBigCpu(), context);
    }

    public List<String> getAdjustedFreq(int cpu, Context context) {
        List<String> freqs = new ArrayList<>();
        if (getFreqs(cpu) != null) {
            for (int freq : getFreqs(cpu)) {
                freqs.add((freq / 1000) + context.getString(R.string.mhz));
            }
        }
        return freqs;
    }

    public List<Integer> getFreqs() {
        return getFreqs(getBigCpu());
    }

    public List<Integer> getFreqs(int cpu) {
        if (sFreqs.indexOfKey(cpu) < 0) {
            if (Utils.existFile(Utils.strFormat(OPP_TABLE, cpu))
                    || Utils.existFile(Utils.strFormat(TIME_STATE, cpu))
                    || Utils.existFile(Utils.strFormat(TIME_STATE_2, cpu))) {
                String file;
                if (Utils.existFile(Utils.strFormat(OPP_TABLE, cpu))) {
                    file = Utils.strFormat(OPP_TABLE, cpu);
                } else if (Utils.existFile(Utils.strFormat(TIME_STATE, cpu))) {
                    file = Utils.strFormat(TIME_STATE, cpu);
                } else {
                    file = Utils.strFormat(TIME_STATE_2, cpu);
                }
                String value = Utils.readFile(file);
                if (value != null) {
                    String[] valueArray = value.trim().split("\\r?\\n");
                    List<Integer> freqs = new ArrayList<>();
                    for (String freq : valueArray) {
                        long freqInt = Utils.strToLong(freq.split(" ")[0]);
                        if (file.endsWith("opp_table")) {
                            freqInt /= 1000;
                        }
                        freqs.add((int) freqInt);
                    }
                    sFreqs.put(cpu, freqs);
                }
            } else if (Utils.existFile(Utils.strFormat(AVAILABLE_FREQS, cpu))) {
                int readcpu = cpu;
                boolean offline = isOffline(cpu);
                if (offline) {
                    onlineCpu(cpu, true, false, null);
                }
                if (!Utils.existFile(Utils.strFormat(Utils.strFormat(AVAILABLE_FREQS, cpu)))) {
                    readcpu = 0;
                }
                String values;
                if ((values = Utils.readFile(Utils.strFormat(AVAILABLE_FREQS, readcpu))) != null) {
                    String[] valueArray = values.split(" ");
                    List<Integer> freqs = new ArrayList<>();
                    for (String freq : valueArray) {
                        freqs.add(Utils.strToInt(freq));
                    }
                    sFreqs.put(cpu, freqs);
                }
                if (offline) {
                    onlineCpu(cpu, false, false, null);
                }
            }
        }
        if (sFreqs.indexOfKey(cpu) < 0) {
            return null;
        }
        List<Integer> freqs = sFreqs.get(cpu);
        Collections.sort(freqs);
        return freqs;
    }

    public int getCurFreq(int cpu) {
        if (Utils.existFile(Utils.strFormat(CUR_FREQ, cpu))) {
            String value = Utils.readFile(Utils.strFormat(CUR_FREQ, cpu));
            if (value != null) {
                return Utils.strToInt(value);
            }
        }
        return 0;
    }

    public void onlineCpu(int cpu, boolean online, boolean onlineSys, Context context) {
        onlineCpu(cpu, online, ApplyOnBootFragment.CPU, onlineSys, context);
    }

    public void onlineCpu(int cpu, boolean online, String category, boolean onlineSys,
                          Context context) {
        CoreCtl coreCtl = CoreCtl.getInstance();
        MSMPerformance msmPerformance = MSMPerformance.getInstance();
        if (!onlineSys) {
            if (QcomBcl.supported()) {
                QcomBcl.online(online, category, context);
            }
            if (coreCtl.hasMinCpus() && getBigCpuRange().contains(cpu)) {
                coreCtl.setMinCpus(online ? getBigCpuRange().size() : mCoreCtlMinCpu, cpu, category,
                        context);
            }
            if (msmPerformance.hasMaxCpus()) {
                msmPerformance.setMaxCpus(online ? getBigCpuRange().size() : -1, online ?
                        getLITTLECpuRange().size() : -1, category, context);
            }
        }
        Control.runSetting(Control.chmod("644", Utils.strFormat(CPU_ONLINE, cpu)),
                category, Utils.strFormat(CPU_ONLINE, cpu) + "chmod644", context);
        Control.runSetting(Control.write(online ? "1" : "0", Utils.strFormat(CPU_ONLINE, cpu)),
                category, Utils.strFormat(CPU_ONLINE, cpu), context);
        Control.runSetting(Control.chmod("444", Utils.strFormat(CPU_ONLINE, cpu)),
                category, Utils.strFormat(CPU_ONLINE, cpu) + "chmod444", context);
    }

    public boolean isCPUOnline(int cpu) {
        if (Utils.existFile(Utils.strFormat(CPU_ONLINE, cpu))) {
            String value = Utils.readFile(Utils.strFormat(CPU_ONLINE, cpu));
            return Utils.strToInt(value) == 1;
        }
        return true;
    }

    public List<Integer> getLITTLECpuRange() {
        List<Integer> list = new ArrayList<>();
        if (hasMidCpu()) {
            for (int i = 0; i < getMidCpu(); i++) {
                list.add(i);
            }
        } else if (!isBigLITTLE()) {
            for (int i = 0; i < getCpuCount(); i++) {
                list.add(i);
            }
        } else if (getLITTLECpu() == 0) {
            for (int i = 0; i < getBigCpu(); i++) {
                list.add(i);
            }
        } else {
            for (int i = getLITTLECpu(); i < getCpuCount(); i++) {
                list.add(i);
            }
        }
        return list;
    }

    public List<Integer> getMidCpuRange() {
        List<Integer> list = new ArrayList<>();
        for (int i = getMidCpu(); i < getBigCpu(); i++) {
            list.add(i);
        }
        return list;
    }

    public List<Integer> getBigCpuRange() {
        List<Integer> list = new ArrayList<>();
        if (!isBigLITTLE()) {
            for (int i = 0; i < getCpuCount(); i++) {
                list.add(i);
            }
        } else if (getBigCpu() == 0) {
            if (hasMidCpu()) {
                for (int i = 0; i < getMidCpu(); i++) {
                    list.add(i);
                }
            } else {
                for (int i = 0; i < getLITTLECpu(); i++) {
                    list.add(i);
                }
            }
        } else {
            for (int i = getBigCpu(); i < getCpuCount(); i++) {
                list.add(i);
            }
        }
        return list;
    }

    public int getLITTLECpu() {
        isBigLITTLE();
        return Math.max(mLITTLECpu, 0);
    }

    public int getMidCpu() {
        isBigLITTLE();
        return Math.max(mMidCpu, 0);
    }

    public int getBigCpu() {
        isBigLITTLE();
        return Math.max(mBigCpu, 0);
    }

    public boolean hasMidCpu() {
        return is4Little2Mid2Big() || is4Little3Mid1Big();
    }

    public boolean isBigLITTLE() {
        if (mBigCpu == -1 || mMidCpu == -1 || mLITTLECpu == -1) {
            if (getCpuCount() <= 4 && !is8996()
                    || (Device.getBoard().startsWith("mt6") && !Device.getBoard().startsWith("mt6595"))
                    || Device.getBoard().startsWith("msm8929")) return false;

            if (is8996()) {
                mBigCpu = 2;
                mLITTLECpu = 0;
            } else if (is4Little2Mid2Big()) {
                mBigCpu = 6;
                mMidCpu = 4;
                mLITTLECpu = 0;
            } else if (is4Little3Mid1Big()){
                mBigCpu = 7;
                mMidCpu = 4;
                mLITTLECpu = 0;
            } else if (is6Little2Big()) {
                mBigCpu = 6;
                mLITTLECpu = 0;
            } else {
                List<Integer> cpu0Freqs = getFreqs(0);
                List<Integer> cpu4Freqs = getFreqs(4);
                if (cpu0Freqs != null && cpu4Freqs != null) {
                    int cpu0Max = cpu0Freqs.get(cpu0Freqs.size() - 1);
                    int cpu4Max = cpu4Freqs.get(cpu4Freqs.size() - 1);
                    if (cpu0Max > cpu4Max
                            || (cpu0Max == cpu4Max && cpu0Freqs.size() > cpu4Freqs.size())) {
                        mBigCpu = 0;
                        mLITTLECpu = 4;
                    } else {
                        mBigCpu = 4;
                        mLITTLECpu = 0;
                    }
                }
            }

            if (mBigCpu == -1 || mLITTLECpu == -1) {
                mBigCpu = -2;
                mLITTLECpu = -2;
            }
        }

        return mBigCpu >= 0 && mLITTLECpu >= 0;
    }

    private boolean is8996() {
        String board = Device.getBoard();
        return board.equalsIgnoreCase("msm8996") || board.equalsIgnoreCase("msm8996pro")
                || board.equalsIgnoreCase("marlin") || board.equalsIgnoreCase("sailfish");
    }

    private boolean is6Little2Big() {
        int cpuPolicy0Max = cpuPolicyMax(CPU_POLICY0_MAX_FREQ);
        int cpuPolicy6Max = cpuPolicyMax(CPU_POLICY6_MAX_FREQ);
        List<Integer> cpu3Freqs = getFreqs(3);
        List<Integer> cpu4Freqs = getFreqs(4);
        List<Integer> cpu5Freqs = getFreqs(5);
        List<Integer> cpu6Freqs = getFreqs(6);
        if (cpu5Freqs != null && cpu6Freqs != null) {
            int cpu3Max = cpu3Freqs.get(cpu3Freqs.size() - 1);
            int cpu4Max = cpu4Freqs.get(cpu4Freqs.size() - 1);
            int cpu5Max = cpu5Freqs.get(cpu5Freqs.size() - 1);
            int cpu6Max = cpu6Freqs.get(cpu6Freqs.size() - 1);
            return cpu3Max == cpu4Max && cpu4Max == cpu5Max && cpu5Max < cpu6Max || cpuPolicy0Max < cpuPolicy6Max;
        }
        return false;
    }

    private boolean is4Little2Mid2Big() {
        List<Integer> cpu3Freqs = getFreqs(3);
        List<Integer> cpu4Freqs = getFreqs(4);
        List<Integer> cpu5Freqs = getFreqs(5);
        List<Integer> cpu6Freqs = getFreqs(6);
        if (cpu5Freqs != null && cpu6Freqs != null) {
            int cpu3Max = cpu3Freqs.get(cpu3Freqs.size() - 1);
            int cpu4Max = cpu4Freqs.get(cpu4Freqs.size() - 1);
            int cpu5Max = cpu5Freqs.get(cpu5Freqs.size() - 1);
            int cpu6Max = cpu6Freqs.get(cpu6Freqs.size() - 1);
            return cpu3Max < cpu4Max && cpu4Max == cpu5Max && cpu5Max < cpu6Max;
        }
        return false;
    }

    private boolean is4Little3Mid1Big() {
        List<Integer> cpu3Freqs = getFreqs(3);
        List<Integer> cpu4Freqs = getFreqs(4);
        List<Integer> cpu5Freqs = getFreqs(5);
        List<Integer> cpu6Freqs = getFreqs(6);
        List<Integer> cpu7Freqs = getFreqs(7);
        if (cpu6Freqs != null && cpu7Freqs != null) {
            int cpu3Max = cpu3Freqs.get(cpu3Freqs.size() - 1);
            int cpu4Max = cpu4Freqs.get(cpu4Freqs.size() - 1);
            int cpu5Max = cpu5Freqs.get(cpu5Freqs.size() - 1);
            int cpu6Max = cpu6Freqs.get(cpu6Freqs.size() - 1);
            int cpu7Max = cpu7Freqs.get(cpu7Freqs.size() - 1);
            return cpu3Max < cpu4Max && cpu4Max == cpu5Max && cpu5Max == cpu6Max && cpu6Max < cpu7Max;
        }
        return false;
    }

    public int getCpuCount() {
        if (mCpuCount == 0 && Utils.existFile(CPU_PRESENT)) {
            try {
                String output = Utils.readFile(CPU_PRESENT);
                mCpuCount = output.equals("0") ? 1 : Integer.parseInt(output.split("-")[1]) + 1;
            } catch (Exception ignored) {
            }
        }
        if (mCpuCount == 0) {
            mCpuCount = Runtime.getRuntime().availableProcessors();
        }
        return mCpuCount;
    }

    public float[] getCpuUsage() throws InterruptedException {
        Usage[] prevUsage = getUsages();
        Thread.sleep(500);
        Usage[] usage = getUsages();

        if (prevUsage != null && usage != null) {
            float[] pers = new float[prevUsage.length];
            for (int i = 0; i < prevUsage.length; i++) {
                float prevIdle = prevUsage[i].getIdle();
                float prevUp = prevUsage[i].getUptime();

                float idle = usage[i].getIdle();
                float up = usage[i].getUptime();

                float cpu = (up - prevUp) / ((up + idle) - (prevUp + prevIdle)) * 100;
                pers[i] = cpu < 0 ? 0 : cpu > 100 ? 100 : cpu;
            }
            return pers;
        }

        return null;
    }

    private Usage[] getUsages() {
        String stat = Utils.readFile("/proc/stat");
        if (stat == null) return null;
        String[] stats = stat.split("\\r?\\n");

        Usage[] usage = new Usage[getCpuCount() + 1];
        for (int i = 0; i < usage.length; i++) {
            if (i >= stats.length) return null;
            usage[i] = new Usage(stats[i]);
        }
        return usage;
    }

    private static class Usage {

        private long[] stats;

        private Usage(String stats) {
            if (stats == null) return;

            String[] values = stats.replaceAll("\\s{2,}", " ").trim().split(" ");
            this.stats = new long[values.length - 1];
            for (int i = 0; i < this.stats.length; i++) {
                this.stats[i] = Utils.strToLong(values[i + 1]);
            }
        }

        public long getUptime() {
            if (stats == null) return 0;
            long l = 0;
            for (int i = 0; i < stats.length - 2; i++) {
                if (i != 3 && i != 4) l += stats[i];
            }
            return l;
        }

        private long getIdle() {
            return stats == null || stats.length < 5 ? 0 : stats[3] + stats[4];
        }

    }

    private void run(String command, String id, Context context) {
        Control.runSetting(command, ApplyOnBootFragment.CPU, id, context);
    }

}