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
import com.smartpack.kernelmanager.fragments.ApplyOnBootFragment;
import com.smartpack.kernelmanager.fragments.RecyclerViewFragment;
import com.smartpack.kernelmanager.utils.kernel.npu.NPUFreq;
import com.smartpack.kernelmanager.views.recyclerview.CardView;
import com.smartpack.kernelmanager.views.recyclerview.RecyclerViewItem;
import com.smartpack.kernelmanager.views.recyclerview.SelectView;
import com.smartpack.kernelmanager.views.recyclerview.XYGraphView;

import java.util.List;

/**
 * Created by willi on 12.05.16.
 */
public class NPUFragment extends RecyclerViewFragment {

    private NPUFreq mNPUFreq;
    private XYGraphView mCurFreq;

    @Override
    protected void init() {
        super.init();

        mNPUFreq = NPUFreq.getInstance();
        addViewPagerFragment(ApplyOnBootFragment.newInstance(this));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        npuInit(items);
    }

    private void npuInit(List<RecyclerViewItem> items) {
        CardView npuCard = new CardView(getActivity());
        npuCard.setTitle(getString(R.string.npu));

        if (mNPUFreq.hasCurFreq() && mNPUFreq.getAvailableFreqs() != null) {
            mCurFreq = new XYGraphView();
            mCurFreq.setTitle(getString(R.string.npu_freq));
            npuCard.addItem(mCurFreq);
        }

        if (mNPUFreq.hasMaxFreq() && mNPUFreq.getAvailableFreqs() != null) {
            SelectView maxFreq = new SelectView();
            maxFreq.setTitle(getString(R.string.npu_max_freq));
            maxFreq.setSummary(getString(R.string.npu_max_freq_summary));
            maxFreq.setItems(mNPUFreq.getAdjustedFreqs(getActivity()));
            maxFreq.setItem((mNPUFreq.getMaxFreq() / mNPUFreq.getMaxFreqOffset()) + getString(R.string.mhz));
            maxFreq.setOnItemSelected((selectView, position, item) -> mNPUFreq.setMaxFreq(mNPUFreq.getAvailableFreqs().get(position), getActivity()));

            npuCard.addItem(maxFreq);
        }

        if (mNPUFreq.hasMinFreq() && mNPUFreq.getAvailableFreqs() != null) {
            SelectView minFreq = new SelectView();
            minFreq.setTitle(getString(R.string.npu_min_freq));
            minFreq.setSummary(getString(R.string.npu_min_freq_summary));
            minFreq.setItems(mNPUFreq.getAdjustedFreqs(getActivity()));
            minFreq.setItem((mNPUFreq.getMinFreq() / mNPUFreq.getMinFreqOffset()) + getString(R.string.mhz));
            minFreq.setOnItemSelected((selectView, position, item) -> mNPUFreq.setMinFreq(mNPUFreq.getAvailableFreqs().get(position), getActivity()));

            npuCard.addItem(minFreq);
        }

        if (mNPUFreq.hasGovernor()) {
            SelectView governor = new SelectView();
            governor.setTitle(getString(R.string.npu_governor));
            governor.setSummary(getString(R.string.npu_governor_summary));
            governor.setItems(mNPUFreq.getAvailableGovernors());
            governor.setItem(mNPUFreq.getGovernor());
            governor.setOnItemSelected((selectView, position, item) -> mNPUFreq.setGovernor(item, getActivity()));

            npuCard.addItem(governor);
        }

        if (npuCard.size() > 0) {
            items.add(npuCard);
        }
    }

    private Integer mLoad;
    private Integer mFreq;
    private List<Integer> mFreqs;

    @Override
    protected void refreshThread() {
        super.refreshThread();

        mLoad = mNPUFreq.hasLoad() ? mNPUFreq.getLoad() : null;
        mFreq = mNPUFreq.getCurFreq();
        mFreqs = mNPUFreq.getAvailableFreqs();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void refresh() {
        super.refresh();

        if (mCurFreq != null && mFreq != null && mFreqs != null) {
            int load = -1;
            String text = "";
            if (mLoad != null) {
                load = mLoad;
                load = load > 100 ? 100 : Math.max(load, 0);
                text += load + "% - ";
            }

            int freq = mFreq;
            float maxFreq = mFreqs.get(mFreqs.size() - 1);
            text += freq / mNPUFreq.getCurFreqOffset() + getString(R.string.mhz);
            mCurFreq.setText(text);
            float per = (float) freq / maxFreq * 100f;
            mCurFreq.addPercentage(load >= 0 ? load : Math.round(per > 100 ? 100 : per < 0 ? 0 : per));
        }
    }

}