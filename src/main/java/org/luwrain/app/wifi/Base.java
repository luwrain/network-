/*
   Copyright 2012-2016 Michael Pozhidaev <michael.pozhidaev@gmail.com>

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
import org.luwrain.popups.*;
import org.luwrain.network.*;
import org.luwrain.util.RegistryPath;

class Base
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Network network;
    private Luwrain luwrain;
    private Strings strings;
    private final FixedListModel listModel = new FixedListModel();
    private FutureTask scanningTask;
    private FutureTask connectionTask;
    private Actions actions;

    boolean init(Luwrain luwrain, Actions actions,
		 Strings strings)
    {
	NullCheck.notNull(luwrain, "luwrain");
	NullCheck.notNull(actions, "actions");
	NullCheck.notNull(strings, "strings");
	this.luwrain = luwrain;
	this.actions = actions;
	this.strings = strings;
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
	if (connectTo.hasPassword() && !askForPassword(connectTo))
	    return false;
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

    private boolean askForPassword(WifiNetwork network)
    {
	NullCheck.notNull(network, "network");
	final org.luwrain.network.Settings.WifiNetwork settings = org.luwrain.network.Settings.createWifiNetwork(luwrain.getRegistry(), network);
	if (!settings.getPassword("").isEmpty() &&
	    Popups.confirmDefaultYes(luwrain, strings.connectionPopupName(), strings.useSavedPassword()))
	{
	    network.setPassword(settings.getPassword(""));
	    return true;
	}
	final String password = Popups.simple(luwrain, strings.connectionPopupName(), strings.enterThePassword(), "");
	if (password == null)
	    return false;
    if (Popups.confirmDefaultYes(luwrain, strings.connectionPopupName(), strings.saveThePassword()))
	settings.setPassword(password);
    network.setPassword(password);
    return true;
    }

}
