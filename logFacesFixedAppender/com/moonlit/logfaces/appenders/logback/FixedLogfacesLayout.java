/**
 * XML layout to be used with logback library and socket appender
 * Created by Moonlit Software Ltd logfaces team.
 * 
 * All credits go to the authors of logback framework whose source code is re-used.
 * This layout is free software, you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation.
 */

package com.moonlit.logfaces.appenders.logback;

import ch.qos.logback.classic.log4j.XMLLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.moonlit.logfaces.appenders.Transform;
import com.moonlit.logfaces.appenders.Utils;

import java.net.InetAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class FixedLogfacesLayout extends XMLLayout {
	private final int DEFAULT_SIZE = 256;
	private final String MARKER_CONTEXT = "marker";
	private static String REPLACE_REGEX = "[\\p{Cntrl}&&[^\r\n\t]]|[\\ufffe-\\uffff]";
	private boolean delegateMarker, locationInfo;
	private String applicationName, hostName;

	public FixedLogfacesLayout(String applicationName, boolean locationInfo){
		this.applicationName = applicationName;
		this.locationInfo = locationInfo;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} 
		catch(Exception e) {
			try {
				hostName = InetAddress.getLocalHost().getHostAddress();
			} 
			catch(Exception e2) {
			}
		}
	}
	
	public String doLayout(ILoggingEvent event) {
		StringBuilder buf = new StringBuilder(DEFAULT_SIZE);
		buf.append("<log4j:event logger=\"");
		buf.append(event.getLoggerName());
		buf.append("\" timestamp=\"");
		buf.append(event.getTimeStamp());
		buf.append("\" level=\"");
		buf.append(event.getLevel());
		buf.append("\" thread=\"");
		buf.append(event.getThreadName());
		buf.append("\">\r\n");

		buf.append("  <log4j:message><![CDATA[");
		String message = event.getFormattedMessage();
		message = (message != null) ? message.replaceAll(REPLACE_REGEX, "") : "";
		Transform.appendEscapingCDATA(buf, message);
		buf.append("]]></log4j:message>\r\n");

		IThrowableProxy tp = event.getThrowableProxy();
		if (tp != null) {
			buf.append("  <log4j:throwable><![CDATA[");
			buf.append("\r\n");
			String ex = ThrowableProxyUtil.asString(tp);
			ex = (ex != null) ? ex.replaceAll(REPLACE_REGEX, "") : "";
			buf.append(ex);
			buf.append("\r\n");
			buf.append("]]></log4j:throwable>\r\n");
		}

		if(locationInfo) {
			StackTraceElement[] callerDataArray = event.getCallerData();
			if (callerDataArray != null && callerDataArray.length > 0) {
				StackTraceElement immediateCallerData = callerDataArray[0];
				buf.append("  <log4j:locationInfo class=\"");
				buf.append(immediateCallerData.getClassName());
				buf.append("\" method=\"");
				buf.append(Transform.escapeTags(immediateCallerData.getMethodName()));
				buf.append("\" file=\"");
				buf.append(immediateCallerData.getFileName());
				buf.append("\" line=\"");
				buf.append(immediateCallerData.getLineNumber());
				buf.append("\"/>\r\n");
			}
		}

		// porperties
		buf.append("<log4j:properties>\r\n");
		buf.append("<log4j:data name=\"" + Utils.APP_KEY);
		buf.append("\" value=\"" + Transform.escapeTags(applicationName));
		buf.append("\"/>\r\n");

		buf.append("<log4j:data name=\"" + Utils.HOST_KEY);
		buf.append("\" value=\"" + Transform.escapeTags(hostName));
		buf.append("\"/>\r\n");
		
		// if marker should be delegated - do it here
		if(delegateMarker && event.getMarker() != null){
			buf.append("\r\n    <log4j:data");
			buf.append(" name='" + Transform.escapeTags(MARKER_CONTEXT) + "'");
			buf.append(" value='" + Transform.escapeTags(event.getMarker().getName()) + "'");
			buf.append("/>");
		}
		
		Map<String, String> propertyMap = event.getMDCPropertyMap();

		if ((propertyMap != null) && (propertyMap.size() != 0)) {
			Set<Entry<String, String>> entrySet = propertyMap.entrySet();
			for (Entry<String, String> entry : entrySet) {
				buf.append("\r\n    <log4j:data");
				buf.append(" name='" + Transform.escapeTags(entry.getKey()) + "'");
				buf.append(" value='" + Transform.escapeTags(entry.getValue()) + "'");
				buf.append("/>");
			}
		}
		
		buf.append("\r\n  </log4j:properties>");
		buf.append("\r\n</log4j:event>\r\n\r\n");

		return buf.toString();
	}

	public void setDelegateMarker(boolean delegateMarker) {
		this.delegateMarker = delegateMarker;
	}
}
