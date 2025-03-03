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
package com.smartpack.kernelmanager.fragments.kernel;

import com.smartpack.kernelmanager.R;
import com.smartpack.kernelmanager.fragments.DescriptionFragment;
import com.smartpack.kernelmanager.fragments.RecyclerViewFragment;
import com.smartpack.kernelmanager.utils.Utils;
import com.smartpack.kernelmanager.utils.kernel.allthermal.AllThermalSensors;
import com.smartpack.kernelmanager.views.recyclerview.CardView;
import com.smartpack.kernelmanager.views.recyclerview.DescriptionView;
import com.smartpack.kernelmanager.views.recyclerview.RecyclerViewItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by willi on 12.05.16.
 */
public class AllThermalFragment extends RecyclerViewFragment {

    private AllThermalSensors mAllSensors;
    private HashMap<String, String> map;
    private final HashMap<String, DescriptionView> mSensorViews = new HashMap<>();

    @Override
    protected void init() {
        super.init();

        mAllSensors = AllThermalSensors.getInstance();
        addViewPagerFragment(DescriptionFragment.newInstance(getString(R.string.tm),
                getString(R.string.tm_desc)));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        thermalInit(items);
    }

    private void thermalInit(List<RecyclerViewItem> items) {
        CardView thermalCard = new CardView(getActivity());
        thermalCard.setTitle(getString(R.string.thermal_zones));
        map = mAllSensors.getAllTemperatures();
        mSensorViews.clear();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            DescriptionView temp = new DescriptionView();
            temp.setTitle(entry.getKey());
            int temperature = Integer.parseInt(entry.getValue().strip()) / 1000;
            boolean fahrenheit = Utils.useFahrenheit(getActivity());
            temp.setSummary(String.valueOf(Utils.roundTo2Decimals(fahrenheit ? Utils.celsiusToFahrenheit(temperature) : temperature)) + (fahrenheit ? "°F" : "°C"));
            thermalCard.addItem(temp);
            mSensorViews.put(entry.getKey(), temp);
        }

        if (thermalCard.size() > 0) {
            items.add(thermalCard);
        }
    }

    @Override
    protected void refresh() {
        super.refresh();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            DescriptionView view = mSensorViews.get(entry.getKey());
            if (view != null) {
                int temperature = Integer.parseInt(entry.getValue().strip()) / 1000;
                boolean fahrenheit = Utils.useFahrenheit(getActivity());
                view.setSummary(String.valueOf(Utils.roundTo2Decimals(fahrenheit ? Utils.celsiusToFahrenheit(temperature) : temperature)) + (fahrenheit ? "°F" : "°C"));
            }
        }
    }

    @Override
    protected void refreshThread() {
        super.refreshThread();

        map = mAllSensors.getAllTemperatures();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}