/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2004,2005 Dave Brosius <dbrosius@qis.net>
 * Copyright (C) 2004,2005 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

/*
 * UserPreferences.java
 *
 * Created on May 26, 2004, 11:55 PM
 */

package edu.umd.cs.findbugs.config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;

/**
 * User Preferences outside of any one Project.
 * This consists of a class to manage the findbugs.prop file found in the user.dir.
 *
 * @author Dave Brosius
 */
public class UserPreferences {
	private static final int MAX_RECENT_FILES = 9;
	private static final String DETECTOR_THRESHOLD_KEY = "detector_threshold";
	private static final String FILTER_SETTINGS_KEY = "filter_settings";
	private LinkedList<String> recentProjectsList = new LinkedList<String>();
	private HashMap<String, Boolean> detectorEnablementMap = new HashMap<String, Boolean>();
	private ProjectFilterSettings filterSettings;
	private static UserPreferences preferencesSingleton = new UserPreferences();

	public UserPreferences() {
		this.filterSettings = ProjectFilterSettings.createDefault();
	}

	public static UserPreferences getUserPreferences() {
		return preferencesSingleton;
	}

	public void read() {
		File prefFile = new File(System.getProperty("user.home"), "Findbugs.prefs");
		if (!prefFile.exists() || !prefFile.isFile())
			return;
		BufferedInputStream prefStream = null;
		Properties props = new Properties();
		try {
			prefStream = new BufferedInputStream(new FileInputStream(prefFile));
			props.load(prefStream);
		} catch (Exception e) {
			//Report? - probably not
		} finally {
			try {
				if (prefStream != null)
					prefStream.close();
			} catch (IOException ioe) {
			}
		}

		if (props.size() == 0)
			return;
		for (int i = 0; i < MAX_RECENT_FILES; i++) {
			String key = "recent" + i;
			String projectName = (String) props.get(key);
			if (projectName != null)
				recentProjectsList.add(projectName);
		}

		int i = 0;
		while (true) {
			String key = "detector" + i;
			String detectorState = (String) props.get(key);
			if (detectorState == null)
				break;
			int pipePos = detectorState.indexOf("|");
			if (pipePos >= 0) {
				String name = detectorState.substring(0, pipePos);
				String enabled = detectorState.substring(pipePos + 1);
				detectorEnablementMap.put(name, Boolean.valueOf(enabled));
			}
			i++;
		}

		if (props.get(FILTER_SETTINGS_KEY) != null) {
			// Properties contain encoded project filter settings.
			filterSettings = ProjectFilterSettings.fromEncodedString(props.getProperty(FILTER_SETTINGS_KEY));
		} else {
			// Properties contain only minimum warning priority threshold (probably).
			// We will honor this threshold, and enable all bug categories.
			String threshold = (String) props.get(DETECTOR_THRESHOLD_KEY);
			if (threshold != null) {
				try {
					int detectorThreshold = Integer.parseInt(threshold);
					setUserDetectorThreshold(detectorThreshold);
				} catch (NumberFormatException nfe) {
					//Ok to ignore
				}
			}
		}

	}

	public void write() {
		Properties props = new Properties();
		for (int i = 0; i < recentProjectsList.size(); i++) {
			String projectName = recentProjectsList.get(i);
			String key = "recent" + i;
			props.put(key, projectName);
		}

		Iterator it = detectorEnablementMap.entrySet().iterator();
		int i = 0;
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			props.put("detector" + i, entry.getKey() + "|" + String.valueOf(((Boolean) entry.getValue()).booleanValue()));
			i++;
		}

		// Save ProjectFilterSettings
		props.put(FILTER_SETTINGS_KEY, filterSettings.toEncodedString());
		
		// Backwards-compatibility: save minimum warning priority as integer.
		// This will allow the properties file to work with older versions
		// of FindBugs.
		props.put(DETECTOR_THRESHOLD_KEY, String.valueOf(filterSettings.getMinPriorityAsInt()));

		File prefFile = new File(System.getProperty("user.home"), "Findbugs.prefs");
		BufferedOutputStream prefStream = null;
		try {
			prefStream = new BufferedOutputStream(new FileOutputStream(prefFile));
			props.store(prefStream, "FindBugs User Preferences");
			prefStream.flush();
		} catch (IOException e) {
			//Report? -- probably not
		} finally {
			try {
				if (prefStream != null)
					prefStream.close();
			} catch (IOException ioe) {
			}
		}
	}

	public List<String> getRecentProjects() {
		return recentProjectsList;
	}

	public void useProject(String projectName) {
		for (int i = 0; i < recentProjectsList.size(); i++) {
			if (projectName.equals(recentProjectsList.get(i))) {
				recentProjectsList.remove(i);
				recentProjectsList.addFirst(projectName);
				return;
			}
		}
		recentProjectsList.addFirst(projectName);
		if (recentProjectsList.size() > MAX_RECENT_FILES)
			recentProjectsList.removeLast();
	}

	public void removeProject(String projectName) {
		//It really should always be in slot 0, but...
		for (int i = 0; i < recentProjectsList.size(); i++) {
			if (projectName.equals(recentProjectsList.get(i))) {
				recentProjectsList.remove(i);
				break;
			}
		}
	}
	
	/**
	 * Set the enabled/disabled status of given Detector.
	 * 
	 * @param factory the DetectorFactory for the Detector to be enabled/disabled
	 * @param enable  true if the Detector should be enabled,
	 *                false if it should be Disabled
	 */
	public void enableDetector(DetectorFactory factory, boolean enable) {
		detectorEnablementMap.put(factory.getShortName(), enable ? Boolean.TRUE : Boolean.FALSE);
	}
	
	/**
	 * Get the enabled/disabled status of given Detector.
	 * 
	 * @param factory the DetectorFactory of the Detector
	 * @return true if the Detector is enabled, false if not
	 */
	public boolean isDetectorEnabled(DetectorFactory factory) {
		String detectorName = factory.getShortName();
		Boolean enabled = detectorEnablementMap.get(detectorName);
		if (enabled == null) {
			// No explicit preference has been specified for this detector,
			// so use the default enablement specified by the
			// DetectorFactory.
			enabled = factory.isDefaultEnabled() ? Boolean.TRUE : Boolean.FALSE;
			detectorEnablementMap.put(detectorName, enabled);
		}
		return enabled.booleanValue();
	}

	/**
	 * Enable or disable all known Detectors.
	 * 
	 * @param enable true if all detectors should be enabled,
	 *               false if they should all be disabled
	 */
	public void enableAllDetectors(boolean enable) {
		detectorEnablementMap.clear();
		
		DetectorFactoryCollection factoryCollection = DetectorFactoryCollection.instance();
		for (Iterator<DetectorFactory> i = factoryCollection.factoryIterator(); i.hasNext();) {
			DetectorFactory factory = i.next();
			detectorEnablementMap.put(
					factory.getShortName(), enable ? Boolean.TRUE : Boolean.FALSE);
		}
	}
	
	public ProjectFilterSettings getFilterSettings() {
		return this.filterSettings;
	}

	public int getUserDetectorThreshold() {
		return filterSettings.getMinPriorityAsInt();
	}

	public void setUserDetectorThreshold(int threshold) {
		String minPriority = ProjectFilterSettings.getIntPriorityAsString(threshold);
		filterSettings.setMinPriority(minPriority);
	}
}

// vim:ts=4
