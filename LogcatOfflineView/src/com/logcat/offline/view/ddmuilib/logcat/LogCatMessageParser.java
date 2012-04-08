/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.logcat.offline.view.ddmuilib.logcat;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmuilib.logcat.LogCatMessage;
import com.logcat.offline.UIThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse raw output of {@code adb logcat -v long} to {@link LogCatMessage} objects.
 */
public final class LogCatMessageParser {
    
    private static Set<ILogCatMessageEventListener> mLogCatMessageListeners;
    
    private static LogCatMessageParser logCatMessageParser;

    /**
     * This pattern is meant to parse the first line of a log message with the option
     * 'logcat -v long'. The first line represents the date, tag, severity, etc.. while the
     * following lines are the message (can be several lines).<br>
     * This first line looks something like:<br>
     * {@code "[ 00-00 00:00:00.000 <pid>:0x<???> <severity>/<tag>]"}
     * <br>
     * Note: severity is one of V, D, I, W, E, A? or F. However, there doesn't seem to be
     *       a way to actually generate an A (assert) message. Log.wtf is supposed to generate
     *       a message with severity A, however it generates the undocumented F level. In
     *       such a case, the parser will change the level from F to A.<br>
     * Note: the fraction of second value can have any number of digit.<br>
     * Note: the tag should be trimmed as it may have spaces at the end.
     */
    private static final Pattern sLogHeaderPattern = Pattern.compile(
            "^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)"
          + "\\s+(\\d*):\\s*(\\S+)\\s([VDIWEAF])/(.*)\\]$");
    
	private enum PatternType {
		LOGCAT_BRIEF, LOGCAT_V_LONG, LOGCAT_V_TIME, LOGCAT_V_THREADTIME, UNKNOWN,
	};
    
    private LogCatMessageParser(){
    }

    public static LogCatMessageParser getInstance(){
    	if (logCatMessageParser == null){
    		logCatMessageParser = new LogCatMessageParser();
    		mLogCatMessageListeners = new HashSet<ILogCatMessageEventListener>();
    	}
    	return logCatMessageParser;
    }
    
    private PatternType PatternRecognition(String str){
    	Matcher matcher = sLogHeaderPattern.matcher(str);
    	if (matcher.matches()) {
    		return PatternType.LOGCAT_V_LONG;
    	}
    	matcher = p_LOGCAT_V_TIME.matcher(str);
    	if (matcher.matches()) {
    		return PatternType.LOGCAT_V_TIME;
    	}
    	matcher = p_LOGCAT_BRIEF.matcher(str);
    	if (matcher.matches()) {
    		return PatternType.LOGCAT_BRIEF;
    	}
    	matcher = p_LOGCAT_V_THREADTIME.matcher(str);
    	if (matcher.matches()) {
    		return PatternType.LOGCAT_V_THREADTIME;
    	}
    	return PatternType.UNKNOWN;
    }
 
    public void parseLogFile(String filePath, int panelID){
    	if (filePath == null || "".equals(filePath)){
    		return;
    	}
    	File file = new File(filePath);
    	if (!file.exists()){
    		return;
    	}
    	System.gc();
		try {
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			List<String> linesList = new ArrayList<String>();
			PatternType logType = PatternType.UNKNOWN;
			while (br.ready()){
				String strLine = br.readLine();
				
				if(logType == PatternType.UNKNOWN){
					logType = PatternRecognition(strLine);
				}
				if(logType != PatternType.UNKNOWN){
					linesList.add(strLine);
				}
			}
			
			List<LogCatMessage> logMessage;
			switch (logType) {
			case LOGCAT_V_LONG:
				logMessage = process_LOGCAT_V_LONG(linesList);
				break;

			case LOGCAT_V_TIME:
				logMessage = process_LOGCAT_V_TIME(linesList);
				break;
			case LOGCAT_V_THREADTIME:
				logMessage = process_LOGCAT_V_THREADTIME(linesList);
				break;
			case LOGCAT_BRIEF:
				logMessage = process_LOGCAT_BRIEF(linesList);
				break;

			case UNKNOWN:
			default:
				return;
			}
			sendMessageReceivedEvent(
					logMessage, panelID);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    //I/MediaUploader(22541): No need to wake up
    private static final Pattern p_LOGCAT_BRIEF = Pattern.compile(
            "^([VDIWEAF])/(.*?)\\((\\s*\\d+)\\):\\s+(.*)$");
    private List<LogCatMessage> process_LOGCAT_BRIEF(List<String> linesList) {
		LogLevel curLogLevel = LogLevel.WARN;
		String curPid = "?";
		String curTid = "?";
		String curTag = "?";
		String curTime = "?";
		String curMesssage = "?";
		List<LogCatMessage> messages = new ArrayList<LogCatMessage>();
		for (String line : linesList) {
			if (line.length() == 0) {
                continue;
            }
			Matcher matcher = p_LOGCAT_BRIEF.matcher(line);
			if (matcher.matches()) {
				curLogLevel = LogLevel.getByLetterString(matcher.group(1));
				curTag = matcher.group(2).trim();
				curMesssage = matcher.group(4);
				/*
				 * LogLevel doesn't support messages with severity "F".
				 * Log.wtf() is supposed to generate "A", but generates "F".
				 */
				if (curLogLevel == null && matcher.group(1).equals("F")) {
					curLogLevel = LogLevel.ASSERT;
				}
				if (curLogLevel == null) {
					continue;
				}

				LogCatMessage m = new LogCatMessage(curLogLevel, curPid,
						curTid, curTag, curTime, curMesssage);
				messages.add(m);
			}
		}
		return messages;
	}
    
    //04-08 12:57:40.370    89   103 I Installer: connecting...
    private static final Pattern p_LOGCAT_V_THREADTIME = Pattern.compile(
            "^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)" 
            + "\\s*(\\d+)\\s*(\\d+)"
            + "\\s([VDIWEAF])\\s(.*?):\\s+(.*)$");
    private List<LogCatMessage> process_LOGCAT_V_THREADTIME(List<String> linesList) {
		LogLevel curLogLevel = LogLevel.WARN;
		String curPid = "?";
		String curTid = "?";
		String curTag = "?";
		String curTime = "?";
		String curMesssage = "?";
		List<LogCatMessage> messages = new ArrayList<LogCatMessage>();
		for (String line : linesList) {
			if (line.length() == 0) {
                continue;
            }
			Matcher matcher = p_LOGCAT_V_THREADTIME.matcher(line);
			if (matcher.matches()) {
				curTime = matcher.group(1);
				curPid = matcher.group(2).trim();
				curTid = matcher.group(3).trim();
				curLogLevel = LogLevel.getByLetterString(matcher.group(4));
				curTag = matcher.group(5).trim();
				curMesssage = matcher.group(6);
				/*
				 * LogLevel doesn't support messages with severity "F".
				 * Log.wtf() is supposed to generate "A", but generates "F".
				 */
				if (curLogLevel == null && matcher.group(4).equals("F")) {
					curLogLevel = LogLevel.ASSERT;
				}
				if (curLogLevel == null) {
					continue;
				}

				LogCatMessage m = new LogCatMessage(curLogLevel, curPid,
						curTid, curTag, curTime, curMesssage);
				messages.add(m);
			}
		}
		return messages;
	}
    
    private static final Pattern p_LOGCAT_V_TIME = Pattern.compile(
            "^(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)" 
          + "\\s([VDIWEAF])/(.*?)\\((\\s*\\d+)\\):\\s+(.*)$");
	private List<LogCatMessage> process_LOGCAT_V_TIME(List<String> linesList) {
		LogLevel curLogLevel = LogLevel.WARN;
		String curPid = "?";
		String curTid = "?";
		String curTag = "?";
		String curTime = "?";
		String curMesssage = "?";
		List<LogCatMessage> messages = new ArrayList<LogCatMessage>();
		for (String line : linesList) {
			if (line.length() == 0) {
                continue;
            }
			Matcher matcher = p_LOGCAT_V_TIME.matcher(line);
			if (matcher.matches()) {
				curTime = matcher.group(1);
				curLogLevel = LogLevel.getByLetterString(matcher.group(2));
				curTag = matcher.group(3).trim();
				curPid = matcher.group(4).trim();
				curMesssage = matcher.group(5);
				curTid = "";
				/*
				 * LogLevel doesn't support messages with severity "F".
				 * Log.wtf() is supposed to generate "A", but generates "F".
				 */
				if (curLogLevel == null && matcher.group(2).equals("F")) {
					curLogLevel = LogLevel.ASSERT;
				}
				if (curLogLevel == null) {
					continue;
				}

				LogCatMessage m = new LogCatMessage(curLogLevel, curPid,
						curTid, curTag, curTime, curMesssage);
				messages.add(m);
			}
		}
		return messages;
	}

	private List<LogCatMessage> process_LOGCAT_V_LONG(List<String> linesList) {
		LogLevel curLogLevel = LogLevel.WARN;
		String curPid = "?";
		String curTid = "?";
		String curTag = "?";
		String curTime = "?";
		List<LogCatMessage> messages = new ArrayList<LogCatMessage>();
		for (String line : linesList) {
			if (line.length() == 0) {
                continue;
            }
			Matcher matcher = sLogHeaderPattern.matcher(line);
			if (matcher.matches()) {
				curTime = matcher.group(1);
				curPid = matcher.group(2);
				curTid = matcher.group(3);
				curLogLevel = LogLevel.getByLetterString(matcher.group(4));
				curTag = matcher.group(5).trim();

				/*
				 * LogLevel doesn't support messages with severity "F".
				 * Log.wtf() is supposed to generate "A", but generates "F".
				 */
				if (curLogLevel == null && matcher.group(4).equals("F")) {
					curLogLevel = LogLevel.ASSERT;
				}
			} else {
				LogCatMessage m = new LogCatMessage(curLogLevel, curPid,
						curTid, curTag, curTime, line);
				messages.add(m);
			}
		}
		return messages;
	}

	public void parseLogFolder(String folderPath){
    	if (folderPath == null || "".equals(folderPath)){
    		return;
    	}
    	File fileFolder = new File(folderPath);
    	if (!fileFolder.exists() || !fileFolder.isDirectory()){
    		return;
    	}

    	System.gc();
    	File[] files = fileFolder.listFiles();
    	for(File file : files){
    		if (file.getName().toLowerCase().indexOf("main") != -1){
    			parseLogFile(file.getAbsolutePath(), UIThread.PANEL_ID_MAIN);
    		} else if (file.getName().toLowerCase().indexOf("event") != -1){
    			parseLogFile(file.getAbsolutePath(), UIThread.PANEL_ID_EVENTS);
    		} else if (file.getName().toLowerCase().indexOf("radio") != -1){
    			parseLogFile(file.getAbsolutePath(), UIThread.PANEL_ID_RADIO);
    		}
    	}
    }
    
    /**
     * Add to list of message event listeners.
     * @param l listener to notified when messages are received from the device
     */
    public void addMessageReceivedEventListener(ILogCatMessageEventListener l) {
        mLogCatMessageListeners.add(l);
    }

    public void removeMessageReceivedEventListener(ILogCatMessageEventListener l) {
        mLogCatMessageListeners.remove(l);
    }

    private void sendMessageReceivedEvent(List<LogCatMessage> messages, int panelID) {
        for (ILogCatMessageEventListener l : mLogCatMessageListeners) {
            l.messageReceived(messages, panelID);
        }
    }
}
