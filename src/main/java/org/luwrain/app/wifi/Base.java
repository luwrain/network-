/*
   Copyright 2012-2015 Michael Pozhidaev <michael.pozhidaev@gmail.com>

   This file is part of the LUWRAIN.

   LUWRAIN is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public
   License as published by the Free Software Foundation; either
   version 3 of the License, or (at your option) any later version.

   LUWRAIN is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.
*/

package org.luwrain.app.wifi;

import java.util.concurrent.*;

import org.luwrain.core.*;
import org.luwrain.core.events.ProgressLineEvent;
import org.luwrain.controls.*;
import org.luwrain.popups.Popups;
import org.luwrain.network.*;

class Base
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Network network;
    private Luwrain luwrain;
    private final FixedListModel listModel = new FixedListModel();
    private FutureTask scanningTask;
    private FutureTask connectionTask;
    private Actions actions;

    boolean init(Luwrain luwrain, Actions actions)
    {
	this.luwrain = luwrain;
	this.actions = actions;
	final Object o = luwrain.getSharedObject("luwrain.network");
	if (o == null || !(o instanceof Network))
	    return false;
	network = (Network)o;
	return true;
    }

    ListArea.Model getListModel()
    {
	return listModel;
    }

    boolean launchScanning()
    {
	if (scanningTask != null && !scanningTask.isDone())
	    return false;
	scanningTask = createScanningTask();
	executor.execute(scanningTask);
	return true;
    }

    boolean launchConnection(ProgressArea destArea, WifiNetwork connectTo)
    {
	if (connectionTask != null && !connectionTask.isDone())
	    return false;

	if (connectTo.hasPassword())
	{
	    final String password = Popups.simple(luwrain, "Подключение к wifi-сети", "Введите пароль для подключения:", "");
	    if (password == null)
		return false;
	    connectTo.setPassword(password);
	}
	connectionTask = createConnectionTask(destArea, connectTo);
	executor.execute(connectionTask);
	return true;
    }

    private void acceptResult(WifiScanResult scanRes)
    {
    if (scanRes.type() != WifiScanResult.Type.SUCCESS)
    {
	listModel.clear();
	actions.onReady();
	return;
    }
    listModel.setItems(scanRes.networks());
    actions.onReady();
    }

    private FutureTask createScanningTask()
    {
	return new FutureTask(()->{
		final WifiScanResult res = network.wifiScan();
		luwrain.runInMainThread(()->acceptResult(res));
	}, null);
    }

    private FutureTask createConnectionTask(final ProgressArea destArea, final WifiNetwork connectTo)
    {
	return new FutureTask(()->{
		if (network.wifiConnect(connectTo, (line)->luwrain.enqueueEvent(new ProgressLineEvent(destArea, line))))
		    luwrain.runInMainThread(()->luwrain.message("Подключение к сети установлено", Luwrain.MESSAGE_DONE)); else
		    luwrain.runInMainThread(()->luwrain.message("Подключиться к сети не удалось", Luwrain.MESSAGE_ERROR));
	}, null);
    }


    boolean isScanning()
    {
	return scanningTask != null && !scanningTask.isDone();
    }
}
