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
package com.smartpack.kernelmanager.utils.kernel.npu;

import android.content.Context;

import com.smartpack.kernelmanager.R;
import com.smartpack.kernelmanager.fragments.ApplyOnBootFragment;
import com.smartpack.kernelmanager.utils.Utils;
import com.smartpack.kernelmanager.utils.root.Control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created by willi on 12.05.16.
 */
public class NPUFreq {

    private static NPUFreq sIOInstance;

    public static NPUFreq getInstance() {
        if (sIOInstance == null) {
            sIOInstance = new NPUFreq();
        }
        return sIOInstance;
    }

    private static final String MAX_S5E8825_FREQ = "/sys/class/devfreq/17000030.devfreq_npu/max_freq";
    private static final String MIN_S5E8825_FREQ = "/sys/class/devfreq/17000030.devfreq_npu/min_freq";
    private static final String CUR_S5E8825_FREQ = "/sys/class/devfreq/17000030.devfreq_npu/cur_freq";
    private static final String AVAILABLE_S5E8825_FREQS = "/sys/class/devfreq/17000030.devfreq_npu/available_frequencies";
    private static final String SCALING_S5E8825_GOVERNOR = "/sys/class/devfreq/17000030.devfreq_npu/governor";
    private static final String AVAILABLE_S5E8825_GOVERNORS = "/sys/class/devfreq/17000030.devfreq_npu/available_governors";
    private static final String EXYNOS_LOAD = "/sys/devices/platform/npu_exynos/load";

    private final List<String> mNpuLoads = new ArrayList<>();
    private final HashMap<String, Integer> mCurrentFreqs = new HashMap<>();
    private final HashMap<String, Integer> mMaxFreqs = new HashMap<>();
    private final HashMap<String, Integer> mMinFreqs = new HashMap<>();
    private final HashMap<String, Integer> mAvailableFreqs = new HashMap<>();
    private final List<String> mScalingGovernors = new ArrayList<>();
    private final List<String> mAvailableGovernors = new ArrayList<>();

    {
        mNpuLoads.add(EXYNOS_LOAD);

        mCurrentFreqs.put(CUR_S5E8825_FREQ, 1000);

        mMaxFreqs.put(MAX_S5E8825_FREQ, 1000);

        mMinFreqs.put(MIN_S5E8825_FREQ, 1000);

        mAvailableFreqs.put(AVAILABLE_S5E8825_FREQS, 1000);

        mScalingGovernors.add(SCALING_S5E8825_GOVERNOR);

        mAvailableGovernors.add(AVAILABLE_S5E8825_GOVERNORS);
    }

    private String LOAD;
    private String CUR_FREQ;
    private int CUR_FREQ_OFFSET;
    private List<Integer> AVAILABLE_FREQS;
    private String MAX_FREQ;
    private int MAX_FREQ_OFFSET;
    private String MIN_FREQ;
    private int MIN_FREQ_OFFSET;
    private String GOVERNOR;
    private String[] AVAILABLE_GOVERNORS;
    private int AVAILABLE_GOVERNORS_OFFSET;

    private NPUFreq() {
        for (String file : mNpuLoads) {
            if (Utils.existFile(file)) {
                LOAD = file;
                break;
            }
        }

        for (String file : mCurrentFreqs.keySet()) {
            if (Utils.existFile(file)) {
                CUR_FREQ = file;
                CUR_FREQ_OFFSET = mCurrentFreqs.get(file);
                break;
            }
        }

        for (String file : mAvailableFreqs.keySet()) {
            if (Utils.existFile(file)) {
                String[] freqs = Utils.readFile(file).split(" ");
                AVAILABLE_FREQS = new ArrayList<>();
                for (String freq : freqs) {
                    if (!AVAILABLE_FREQS.contains(Utils.strToInt(freq))) {
                        AVAILABLE_FREQS.add(Utils.strToInt(freq));
                    }
                }
                AVAILABLE_GOVERNORS_OFFSET = mAvailableFreqs.get(file);
                Collections.sort(AVAILABLE_FREQS);
                break;
            }
        }

        for (String file : mMaxFreqs.keySet()) {
            if (Utils.existFile(file)) {
                MAX_FREQ = file;
                MAX_FREQ_OFFSET = mMaxFreqs.get(file);
                break;
            }
        }

        for (String file : mMinFreqs.keySet()) {
            if (Utils.existFile(file)) {
                MIN_FREQ = file;
                MIN_FREQ_OFFSET = mMinFreqs.get(file);
                break;
            }
        }

        for (String file : mScalingGovernors) {
            if (Utils.existFile(file)) {
                GOVERNOR = file;
                break;
            }
        }

        for (String file : mAvailableGovernors) {
            if (Utils.existFile(file)) {
                AVAILABLE_GOVERNORS = Utils.readFile(file).split(" ");
                break;
            }
        }
        if (AVAILABLE_GOVERNORS == null) {
            ;
        }
    }

    public void setGovernor(String value, Context context) {
        run(Control.write(value, GOVERNOR), GOVERNOR, context);
    }

    public List<String> getAvailableGovernors() {
        return Arrays.asList(AVAILABLE_GOVERNORS);
    }

    public String getGovernor() {
        return Utils.readFile(GOVERNOR);
    }

    public boolean hasGovernor() {
        return GOVERNOR != null;
    }

    public void setMinFreq(int value, Context context) {
        run(Control.write(String.valueOf(value), MIN_FREQ), MIN_FREQ, context);
    }

    public int getMinFreqOffset() {
        return MIN_FREQ_OFFSET;
    }

    public int getMinFreq() {
        return Utils.strToInt(Utils.readFile(MIN_FREQ));
    }

    public boolean hasMinFreq() {
        return MIN_FREQ != null;
    }

    public void setMaxFreq(int value, Context context) {
        run(Control.write(String.valueOf(value), MAX_FREQ), MAX_FREQ, context);
    }

    public int getMaxFreqOffset() {
        return MAX_FREQ_OFFSET;
    }

    public int getMaxFreq() {
        return Utils.strToInt(Utils.readFile(MAX_FREQ));
    }

    public boolean hasMaxFreq() {
        return MAX_FREQ != null;
    }

    public List<String> getAdjustedFreqs(Context context) {
        List<String> list = new ArrayList<>();
        for (int freq : getAvailableFreqs()) {
            list.add((freq / AVAILABLE_GOVERNORS_OFFSET) + context.getString(R.string.mhz));
        }
        return list;
    }

    public List<Integer> getAvailableFreqs() {
        return AVAILABLE_FREQS;
    }

    public int getCurFreqOffset() {
        return CUR_FREQ_OFFSET;
    }

    public int getCurFreq() {
        return Utils.strToInt(Utils.readFile(CUR_FREQ));
    }

    public boolean hasCurFreq() {
        return CUR_FREQ != null;
    }

    public int getLoad() {
        return Integer.parseInt(Utils.readFile(LOAD).strip());
    }

    public boolean hasLoad() {
        return LOAD != null;
    }

    public boolean supported() {
        return hasCurFreq()
                || (hasMaxFreq() && getAvailableFreqs() != null)
                || (hasMinFreq() && getAvailableFreqs() != null)
                || hasGovernor();
    }

    private void run(String command, String id, Context context) {
        Control.runSetting(command, ApplyOnBootFragment.NPU, id, context);
    }
}
