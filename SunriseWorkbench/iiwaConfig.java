 /**  
 * Copyright (C) 2016 Salvatore Virga - salvo.virga@tum.de, Marco Esposito - marco.esposito@tum.de
 * Technische Universität München
 * Chair for Computer Aided Medical Procedures and Augmented Reality
 * Fakultät für Informatik / I16, Boltzmannstraße 3, 85748 Garching bei München, Germany
 * http://campar.in.tum.de
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * @author Marco Esposito
 * 
 */

package hapticRendering;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.ros.exception.ParameterNotFoundException;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.parameter.ParameterTree;
import org.ros.time.NtpTimeProvider;
import org.ros.time.TimeProvider;
import org.ros.time.WallTimeProvider;

import com.kuka.roboticsAPI.uiModel.IApplicationUI;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKey;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKeyBar;
import com.kuka.roboticsAPI.uiModel.userKeys.IUserKeyListener;
import com.kuka.roboticsAPI.uiModel.userKeys.UserKeyAlignment;
import com.kuka.roboticsAPI.uiModel.userKeys.UserKeyEvent;

public class iiwaConfig extends AbstractNodeMain {

	// Name to use to build the name of the ROS topics
	private static Map<String, String> config = null;
	private static String robotName = null;
	private static String masterIp = null;
	private static String masterPort = null;
	private static String masterUri = null; //< IP address of ROS core to talk to.
	private static String robotIp = null;
	private static boolean staticConfigurationSuccessful = false;
	private static boolean ntpWithHost = false;
	private static TimeProvider timeProvider = null;

	private ConnectedNode node;
	private ParameterTree params;

	// used to wait until we are connected to the ROS master and params are available
	private Semaphore initSemaphore = new Semaphore(0);

	public iiwaConfig() {
		checkConfiguration();
	}

	public static void checkConfiguration() {
		if (!staticConfigurationSuccessful) {
			configure();
			if (!staticConfigurationSuccessful) {
				throw new RuntimeException("Static configuration was not successful");
			}
		}
	}

	private static void parseConfigFile() {
		config = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new InputStreamReader(iiwaConfig.class.getResourceAsStream("config.txt")));
		try {
			String line = null;
			while((line = br.readLine()) != null) {
				String[] lineComponents = line.split(":");
				if (lineComponents.length != 2)
					continue;

				config.put(lineComponents[0].trim(), lineComponents[1].trim());
			}
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	}

	public static void configure() {
		parseConfigFile();
		
		robotName = config.get("robot_name"); // TODO: it would be better to move this to the Sunrise project, so that it's unique for each robot
		System.out.println("robot name: " + robotName);
		
		ntpWithHost  = config.get("ntp_with_host").equals("true");

		// network configuration
		masterIp = config.get("master_ip");
		masterPort = config.get("master_port");
		masterUri = "http://" + masterIp + ":" + masterPort;
		System.out.println("master uri: " + masterUri);

		String[] master_components = masterIp.split("\\.");
		String localhostIp = null;
		Enumeration<NetworkInterface> ifaces = null;
		try {
			ifaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		boolean localhostIpFound = false;
		while(!localhostIpFound && ifaces.hasMoreElements()) {
			NetworkInterface n = (NetworkInterface) ifaces.nextElement();
			Enumeration<InetAddress> ee = n.getInetAddresses();
			while (ee.hasMoreElements()) {
				localhostIp = ((InetAddress) ee.nextElement()).getHostAddress();
				String[] components = localhostIp.split("\\.");

				boolean matches = components[0].equals(master_components[0])
						&& components[1].equals(master_components[1])
						&& components[2].equals(master_components[2]);
				if (matches) {
					localhostIpFound = true;
					break;
				}
			}
		}
		robotIp = localhostIp;
		System.out.println("robot ip: " + robotIp);
		
		staticConfigurationSuccessful = true;
	}
	
	public static String getMasterURI() {
		checkConfiguration();
		return masterUri;
	}
	
	public static String getMasterIp() {
		checkConfiguration();
		return masterIp;
	}
	
	public static String getRobotIp() {
		checkConfiguration();
		return robotIp;
	}
	
	public static String getRobotName() {
		checkConfiguration();
		return robotName;
	}
	
	public static boolean getShouldUseNtp() {
		checkConfiguration();
		return ntpWithHost;
	}

	/**
	 * @see org.ros.node.NodeMain#getDefaultNodeName()
	 */
	@Override
	public GraphName getDefaultNodeName() {
		return GraphName.of(robotName + "/configuration");
	}

	/**
	 * This method is called when the <i>execute</i> method from a <i>nodeMainExecutor</i> is called.<br>
	 * Do <b>NOT</b> manually call this. <p> 
	 * @see org.ros.node.AbstractNodeMain#onStart(org.ros.node.ConnectedNode)
	 */
	@Override
	public void onStart(final ConnectedNode connectedNode) {
		node = connectedNode;
		initSemaphore.release();
	}

	public void waitForInitialization() throws InterruptedException {
		initSemaphore.acquire();
	}

	private ParameterTree getParameterTree() {
		if (initSemaphore.availablePermits() > 0)
			System.out.println("waitForInitialization not called before using parameters!");
		return node.getParameterTree();
	}
	
	public Double getDefaultRelativeJointSpeed() {
		Double defaultRelativeJointSpeed = getDoubleParameter("defaultRelativeJointSpeed");
		if (defaultRelativeJointSpeed == null)
			defaultRelativeJointSpeed = 0.5;
		return defaultRelativeJointSpeed;
	}

	public String getToolName() {
		String toolName = getStringParameter("toolName");
		if (toolName == null)
			toolName = "";
		return toolName;
	}
	
	public boolean getPublishJointStates() {
		Boolean publishStates = getBooleanParameter("publishJointStates");
		if (publishStates == null)
			publishStates = false;
		return publishStates;
	}

	public class ToolbarSpecification {
		public String name;
		public String[] buttonIDs;
	}
	
	public static TimeProvider getTimeProvider() {
		if (timeProvider == null)
			setupTimeProvider();
		return timeProvider;
	}
	
	private static TimeProvider setupTimeProvider() {
		checkConfiguration();
		if (ntpWithHost) {
			try {
				NtpTimeProvider provider = new NtpTimeProvider(InetAddress.getByName(masterIp), Executors.newScheduledThreadPool(1));
				timeProvider = provider;
			} catch (UnknownHostException e) {
				System.err.println("Could not setup NTP time provider!");
			}
		} else {
			timeProvider = new  WallTimeProvider();
		}
		
		return timeProvider;
	}

	public void setupToolbars(IApplicationUI appUI, 
			final iiwaPub publisher, 
			List<IUserKey> generalKeys, 
			List<IUserKeyListener> generalKeyLists, 
			List<IUserKeyBar> generalKeyBars) {
		List<ToolbarSpecification> ts = getToolbarSpecifications();
		if (ts != null) {
			for (final ToolbarSpecification t: ts) {
				IUserKeyBar generalKeyBar = appUI.createUserKeyBar(t.name);

				for (int i = 0; i < t.buttonIDs.length; i++) {
					final String buttonID = t.buttonIDs[i];
					IUserKey generalKey;
					if (buttonID.contains(",")) {
						// double button
						final String[] singleButtonIDs = buttonID.split(",");

						IUserKeyListener generalKeyList = new IUserKeyListener() {
							@Override
							public void onKeyEvent(IUserKey key, com.kuka.roboticsAPI.uiModel.userKeys.UserKeyEvent event) {
								if (event == UserKeyEvent.FirstKeyDown) {
									publisher.publishButtonPressed(t.name+"_"+singleButtonIDs[0]);
								} else if (event == UserKeyEvent.FirstKeyUp) {
									publisher.publishButtonReleased(t.name+"_"+singleButtonIDs[0]);
								} else if (event == UserKeyEvent.SecondKeyDown) {
									publisher.publishButtonPressed(t.name+"_"+singleButtonIDs[1]);
								} else if (event == UserKeyEvent.SecondKeyUp) {
									publisher.publishButtonReleased(t.name+"_"+singleButtonIDs[1]);
								}
							}
						};
						generalKeyLists.add(generalKeyList);

						generalKey = generalKeyBar.addDoubleUserKey(i, generalKeyList, false);
						generalKey.setText(UserKeyAlignment.TopMiddle, singleButtonIDs[0]);
						generalKey.setText(UserKeyAlignment.BottomMiddle, singleButtonIDs[1]);
						generalKeys.add(generalKey);
					} else {
						// single button
						IUserKeyListener generalKeyList = new IUserKeyListener() {
							@Override
							public void onKeyEvent(IUserKey key, com.kuka.roboticsAPI.uiModel.userKeys.UserKeyEvent event) {
								if (event == UserKeyEvent.KeyDown) {
									publisher.publishButtonPressed(t.name+"_"+buttonID);
								} else if (event == UserKeyEvent.KeyUp) {
									publisher.publishButtonReleased(t.name+"_"+buttonID);
								} 
							}
						};
						generalKeyLists.add(generalKeyList);

						generalKey = generalKeyBar.addUserKey(i, generalKeyList, false);
						generalKey.setText(UserKeyAlignment.TopMiddle, buttonID);
						generalKeys.add(generalKey);
					}
				}

				generalKeyBars.add(generalKeyBar);
			}	
			for (IUserKeyBar kb  : generalKeyBars)
				kb.publish();
		}
	}

	// one of the dirtiest things I did in my life. but I can't see a better way
	public List<ToolbarSpecification> getToolbarSpecifications() {
		List<ToolbarSpecification> ret = new ArrayList<ToolbarSpecification>();
		List<?> rawParam = getListParameter("toolbarSpecifications");

		if (rawParam == null)
			return null;

		@SuppressWarnings("unchecked")
		List<String> stringParam = new LinkedList<String>((Collection<? extends String>) rawParam);

		while (stringParam.size() > 0 && (stringParam.get(0)).equals("spec")) {
			ToolbarSpecification ts = new ToolbarSpecification();
			stringParam.remove(0);
			ts.name = (String) stringParam.get(0);
			stringParam.remove(0);
			List<String> buttons = new LinkedList<String>();
			while (stringParam.size() > 0 && !(stringParam.get(0)).equals("spec")) {
				buttons.add(stringParam.get(0));
				stringParam.remove(0);
			}
			if (buttons.size() == 0) // toolbar name but no buttons; TODO: log
				continue;
			ts.buttonIDs = buttons.toArray(new String[buttons.size()]);
			ret.add(ts);
		}

		return ret;
	}
	
	public Double getDoubleParameter(String argname) {
		params = getParameterTree();
		Double ret = null;
		try {
			ret = params.getDouble(robotName + "/" + argname);			
		} catch (ParameterNotFoundException e) {
			// TODO
		}

		return ret;
	}
	
	public Boolean getBooleanParameter(String argname) {
		params = getParameterTree();
		Boolean ret = null;
		try {
			ret = params.getBoolean(robotName + "/" + argname);			
		} catch (ParameterNotFoundException e) {
			// TODO
		}

		return ret;
	}

	public String getStringParameter(String argname) {
		params = getParameterTree();
		String ret = null;
		try {
			ret = params.getString(robotName + "/" + argname);			
		} catch (ParameterNotFoundException e) {
			// TODO
		}

		return ret;
	}

	public List<?> getListParameter(String argname) {
		List<?> args = new LinkedList<String>();  // supports remove
		params = getParameterTree();
		try {
			args = params.getList(robotName + "/" + argname);
			if (args == null) {
				return null;
			}
		} catch (ParameterNotFoundException e) {
			return null;
		}
		return args;
	}

}
