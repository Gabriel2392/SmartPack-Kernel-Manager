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
package com.smartpack.kernelmanager.utils.kernel.allthermal;

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
import java.util.Map;

/**
 * Created by willi on 12.05.16.
 */
public class AllThermalSensors {

    private static AllThermalSensors sIOInstance;

    public static AllThermalSensors getInstance() {
        if (sIOInstance == null) {
            sIOInstance = new AllThermalSensors();
        }
        return sIOInstance;
    }

    private static final String THERMAL_ZONE = "/sys/class/thermal/thermal_zone%d";

    private final HashMap<String, String> mAllThermalSensors = new HashMap<>();

    private AllThermalSensors() {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String zone = Utils.strFormat(THERMAL_ZONE, i);
            String zone_name = zone + "/type";
            String zone_temp = zone + "/temp";
            if (!Utils.existFile(zone_name) || !Utils.existFile(zone_temp)) {
                break;
            }
            mAllThermalSensors.put(Utils.readFile(zone_name), zone_temp);
        }
    }

    public HashMap<String, String> getAllTemperatures() {
        HashMap<String, String> mAllThermalTemperatures = new HashMap<>();
        for (Map.Entry<String, String> entry : mAllThermalSensors.entrySet()) {
            mAllThermalTemperatures.put(entry.getKey(), Utils.readFile(entry.getValue()).strip());
        }
        return mAllThermalTemperatures;
    }

    public boolean supported() {
        return !mAllThermalSensors.isEmpty();
    }
}
