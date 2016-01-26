/**
 * Logfaces socket appender using XML layout to be used with logback library
 * Created by Moonlit Software Ltd logfaces team.
 * 
 * All credits go to the authors of logback framework whose source code is re-used.
 * This appender is free software, you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation.
 */

package com.moonlit.logfaces.appenders.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FixedLogfacesAppender extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent>{
	public static final int DEFAULT_RECONNECTION_DELAY = 5000;
	public static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;
	public static final String APPLICATION_KEY = "application";
	public static final String HOSTNAME_KEY = "hostname";

	protected String remoteHost;
	protected InetAddress address;
	protected int port = 55200;
	protected OutputStreamWriter writer;
	protected String application;
	protected boolean locationInfo = false;
	protected boolean delegateMarker = false;
	protected boolean closing = false;
	protected Connector connector;
	protected FixedLogfacesLayout layout;

	protected Appender<ILoggingEvent> backupAppender;
	protected BlockingQueue<ILoggingEvent>  queue;
	protected List<String> hosts = new ArrayList<String>();
	protected Dispatcher dispatcher;
	protected int hostIndex = 0;
	protected long offerTimeout = 0;
	protected long shutdowdnTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
	protected int nofRetries = 3;
	protected int queueSize = 500;
	protected int nofFailures = 0;
	protected int reconnectionDelay = DEFAULT_RECONNECTION_DELAY;
	protected int warnOverflow;

	@Override
	public void start(){
		if (isStarted())
			return;
		if(hosts.size() == 0)
			throw new IllegalStateException("remoteHost property is required for appender: " + name);

		// prepare layout
		layout = new FixedLogfacesLayout(application, locationInfo);

		// prepare async stuff
		closing = false;
		queue = new ArrayBlockingQueue<ILoggingEvent>(queueSize, true);
		dispatcher = new Dispatcher();
		dispatcher.setName("LogfacesDispatcher");
		dispatcher.setDaemon(true);
		dispatcher.start();
		while(!dispatcher.running){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		started = true;

		new Thread(new Runnable(){
			public void run() {
				connect();
			}
		}).start();
	}

	@Override
	public void stop(){
		if (!isStarted())
			return;
		started = false;
		
		shutdownDispatcher();
		detachAndStopAllAppenders();
		cleanUp();
	}
	
	protected void shutdownDispatcher(){
		// if there is anything lingering in the queue 
		// make sure to flush it to server before yielding control
		dispatcher.shutdown = true;
		long timeout = shutdowdnTimeout/100;
		while(!queue.isEmpty() && dispatcher.isAlive() && --timeout > 0){
			try {
				Thread.sleep(100);
			} catch(InterruptedException e){
				break;
			}
		}
		dispatcher = null;
	}

	protected void cleanUp(){
		if (writer != null){
			try{
				writer.close();
			}
			catch (IOException e){
				addError(e.getMessage(),e);
			}
			writer = null;
		}

		if(connector != null){
		   connector.shutdown = true;
		   connector.interrupt();
		   connector = null;
		}
	}

	protected void connect(){
		try{
			cleanUp();
			address = getAddressByName(hosts.get(hostIndex));
			writer = new OutputStreamWriter(new Socket(address, port).getOutputStream());
		}
		catch(Exception e){
			addError(String.format("logFaces: appender can't connect to server %s:%d, starting failover", hosts.get(hostIndex), port));
			startFailover();
		}
	}

	@Override
	public void append(ILoggingEvent event) {
		if (event == null || !started)
			return;

		try {
			event.getThreadName();
			event.getMDCPropertyMap();
			if(locationInfo)
				event.getCallerData();
			if(!queue.offer(event, offerTimeout, TimeUnit.MILLISECONDS)){
				if(warnOverflow++ == 0){
					addWarn(String.format("logFaces: appender queue is full [%d]. If you see this message it means that queue size needs to be increased, or amount of log events decreased.", queue.size()));
					addWarn( (backupAppender == null)?"logFaces: fall back is disabled":String.format("logFaces backup appender %s activated; You can later import this data into the logfaces server manually.", backupAppender.getName()));
				}

				// transmition queue is full, delegate to fall back appender if specified 
				if(backupAppender != null)
					backupAppender.doAppend(event);
			}
			else{
				warnOverflow=0;
			}
		} 
		catch(InterruptedException e){
		}
		catch(Exception e){
			addWarn("logfaces appender failed to append: ",e);
		}
	}

	protected static InetAddress getAddressByName(String host){
		try{
			return InetAddress.getByName(host);
		}
		catch (Exception e){
			return null;
		}
	}

	class Connector extends Thread {
		boolean shutdown = false;
		public void run() {
			Socket socket;
			while (!shutdown) {
				try {
					sleep(reconnectionDelay);
					socket = new Socket(address, port);
					synchronized (this) {
						writer = new OutputStreamWriter(socket.getOutputStream());
						connector = null;
						break;
					}
				} catch (InterruptedException e) {
					return;
				} catch(Exception e) {
					if(++nofFailures >= nofRetries){
						addWarn(String.format("logFaces: appender unable to connect to %s after %d retries", address, nofRetries));

						// fall back to next host in the list if retries are exhausted
						if(++hostIndex >= hosts.size())
							hostIndex = 0;
						nofFailures = 0;
						connector = null;
						startFailover();
						return;
					}
				}
			}
		}
	}

	protected void startFailover() {
		if(connector == null && nofRetries > 0 && started) {
			address = getAddressByName(hosts.get(hostIndex));
			addWarn("logFaces: appender trying to fall back to " + address);

			connector = new Connector();
			connector.setDaemon(true);
			connector.setPriority(Thread.MIN_PRIORITY);
			connector.start();
		}
	}

	class Dispatcher extends Thread{
		boolean shutdown = false;
		boolean running = false;
		public void run(){
			ILoggingEvent event;
			running = true;
			while(true){
				try {
					if(writer == null){
						sleep(200);
						continue;
					}

					event = queue.poll(shutdowdnTimeout, TimeUnit.MILLISECONDS);
					if(event == null && !shutdown)
						continue;
					if(event == null && shutdown)
						break;
				}catch (InterruptedException e){
					break;
				}
				catch(Exception e){
					if(shutdown)
						break;
					addError("logFaces appender queue taking failed:" + e.getMessage());
					continue;
				}

				try{
					if(event != null){
						writer.write(layout.doLayout(event));
						writer.flush();
					}
				}
				catch(IOException e){
					writer = null;
					addError("logFaces appender socket write failed: " + e.getMessage());
					if(shutdown)
						break;

					// put it back into queue for re-transmitt
					if(event != null)
						queue.offer(event);
					startFailover();
				}
				catch(Exception e){
					addError("logFaces appender general purpose failure: " + e.getMessage());
					if(shutdown)
						break;
				}
			}
			
			addInfo("logFaces appender dispatcher thread ends");
		}
	}

	public void setRemoteHost(String host) {
		remoteHost = host;
		String split[] = remoteHost.split(",");
		hosts.addAll(Arrays.asList(split));
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	public void setLocationInfo(boolean locationInfo) {
		this.locationInfo = locationInfo;
		layout.setLocationInfo(locationInfo);
	}

	public boolean getLocationInfo() {
		return locationInfo;
	}

	public void setApplication(String lapp) {
		this.application = lapp;
	}

	public String getApplication() {
		return application;
	}

	public int getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(int queueSize) {
		this.queueSize = queueSize;
	}

	public void setReconnectionDelay(int delay) {
		this.reconnectionDelay = delay < DEFAULT_RECONNECTION_DELAY ? DEFAULT_RECONNECTION_DELAY : delay;
	}

	public int getReconnectionDelay() {
		return reconnectionDelay;
	}

	public int getNofRetries() {
		return nofRetries;
	}

	public void setNofRetries(int nofRetries) {
		this.nofRetries = nofRetries;
	}

	public long getOfferTimeout() {
		return offerTimeout;
	}

	public void setOfferTimeout(long offerTimeout) {
		this.offerTimeout = offerTimeout;
	}

	public long getShutdowdnTimeout() {
		return shutdowdnTimeout;
	}

	public void setShutdowdnTimeout(long timeout) {
		this.shutdowdnTimeout = timeout < DEFAULT_SHUTDOWN_TIMEOUT ? DEFAULT_SHUTDOWN_TIMEOUT : timeout;
	}
	
	public void setDelegateMarker(boolean delegateMarker) {
		layout.setDelegateMarker(delegateMarker);
	}

	//
	// custom implementation of AppenderAttachable
	// we allow only single appender to be attached to this appender
	// whatever new appender is attached/detached - it will be
	// treated as a backupAppender automatically
	//
	@Override
	public void addAppender(Appender<ILoggingEvent> appender) {
		backupAppender = appender;
	}

	@Override
	public void detachAndStopAllAppenders() {
		if(backupAppender != null){
			backupAppender.stop();
			backupAppender = null;
		}
	}

	@Override
	public boolean detachAppender(Appender<ILoggingEvent> appender) {
		if(backupAppender != null){
			backupAppender.stop();
			backupAppender = null;
			return true;
		}
		return false;
	}

	@Override
	public boolean detachAppender(String name) {
		if(backupAppender != null){
			backupAppender.stop();
			backupAppender = null;
			return true;
		}
		return false;
	}

	@Override
	public Appender<ILoggingEvent> getAppender(String name) {
		return backupAppender;
	}

	@Override
	public boolean isAttached(Appender<ILoggingEvent> appender) {
		if(appender != null && appender.equals(backupAppender))
			return true;
		return false;
	}

	@Override
	public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
		return null;
	}
}
