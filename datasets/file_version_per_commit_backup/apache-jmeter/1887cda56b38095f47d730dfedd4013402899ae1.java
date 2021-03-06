/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.apache.jmeter.protocol.ftp.config.gui;

import java.awt.BorderLayout;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.gui.util.HorizontalPanel;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.protocol.ftp.sampler.FTPSampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;

public class FtpConfigGui extends AbstractConfigGui {

	private JTextField server;

	private JTextField remoteFile;

	private JTextField localFile;

	private JCheckBox binaryMode;
	
	private JCheckBox saveResponseData;
	
	private boolean displayName = true;

	private JRadioButton getBox;

	private JRadioButton putBox;

	public FtpConfigGui() {
		this(true);
	}

	public FtpConfigGui(boolean displayName) {
		this.displayName = displayName;
		init();
	}

	public String getLabelResource() {
		return "ftp_sample_title"; // $NON-NLS-1$
	}

	public void configure(TestElement element) {
		super.configure(element);
		// Note: the element is a ConfigTestElement, so cannot use FTPSampler access methods
		server.setText(element.getPropertyAsString(FTPSampler.SERVER));
		remoteFile.setText(element.getPropertyAsString(FTPSampler.REMOTE_FILENAME));
		localFile.setText(element.getPropertyAsString(FTPSampler.LOCAL_FILENAME));
		binaryMode.setSelected(element.getPropertyAsBoolean(FTPSampler.BINARY_MODE, false));
		saveResponseData.setSelected(element.getPropertyAsBoolean(FTPSampler.SAVE_RESPONSE, false));
		putBox.setSelected(element.getPropertyAsBoolean(FTPSampler.UPLOAD_FILE,false));
	}

	public TestElement createTestElement() {
		ConfigTestElement element = new ConfigTestElement();
		modifyTestElement(element);
		return element;
	}

	/**
	 * Modifies a given TestElement to mirror the data in the gui components.
	 * 
	 * @see org.apache.jmeter.gui.JMeterGUIComponent#modifyTestElement(TestElement)
	 */
	public void modifyTestElement(TestElement element) {
		configureTestElement(element);
		// Note: the element is a ConfigTestElement, so cannot use FTPSampler access methods
		element.setProperty(FTPSampler.SERVER,server.getText());
		element.setProperty(FTPSampler.REMOTE_FILENAME,remoteFile.getText());
		element.setProperty(FTPSampler.LOCAL_FILENAME,localFile.getText());
		element.setProperty(FTPSampler.BINARY_MODE,binaryMode.isSelected());
		element.setProperty(FTPSampler.SAVE_RESPONSE, saveResponseData.isSelected());
		element.setProperty(FTPSampler.UPLOAD_FILE,putBox.isSelected());
	}

	private JPanel createServerPanel() {
		JLabel label = new JLabel(JMeterUtils.getResString("server"));

		server = new JTextField(10);
		label.setLabelFor(server);

		JPanel serverPanel = new JPanel(new BorderLayout(5, 0));
		serverPanel.add(label, BorderLayout.WEST);
		serverPanel.add(server, BorderLayout.CENTER);
		return serverPanel;
	}

	private JPanel createLocalFilenamePanel() {
		JLabel label = new JLabel(JMeterUtils.getResString("ftp_local_file"));

		localFile = new JTextField(10);
		label.setLabelFor(localFile);

		JPanel filenamePanel = new JPanel(new BorderLayout(5, 0));
		filenamePanel.add(label, BorderLayout.WEST);
		filenamePanel.add(localFile, BorderLayout.CENTER);
		return filenamePanel;
	}

	private JPanel createRemoteFilenamePanel() {
		JLabel label = new JLabel(JMeterUtils.getResString("ftp_remote_file"));

		remoteFile = new JTextField(10);
		label.setLabelFor(remoteFile);

		JPanel filenamePanel = new JPanel(new BorderLayout(5, 0));
		filenamePanel.add(label, BorderLayout.WEST);
		filenamePanel.add(remoteFile, BorderLayout.CENTER);
		return filenamePanel;
	}

	private JPanel createOptionsPanel(){

		ButtonGroup group = new ButtonGroup();

		getBox = new JRadioButton(JMeterUtils.getResString("ftp_get"));
		group.add(getBox);
		getBox.setSelected(true);

		putBox = new JRadioButton(JMeterUtils.getResString("ftp_put"));
		group.add(putBox);

		binaryMode = new JCheckBox(JMeterUtils.getResString("ftp_binary_mode"));
		saveResponseData = new JCheckBox(JMeterUtils.getResString("ftp_save_response_data"));
		
		
		JPanel optionsPanel = new HorizontalPanel();
		optionsPanel.add(getBox);
		optionsPanel.add(putBox);
		optionsPanel.add(binaryMode);
		optionsPanel.add(saveResponseData);
		return optionsPanel;		
	}
	private void init() {
		setLayout(new BorderLayout(0, 5));

		if (displayName) {
			setBorder(makeBorder());
			add(makeTitlePanel(), BorderLayout.NORTH);
		}

		// MAIN PANEL
		VerticalPanel mainPanel = new VerticalPanel();
		mainPanel.add(createServerPanel());
		mainPanel.add(createRemoteFilenamePanel());
		mainPanel.add(createLocalFilenamePanel());
		mainPanel.add(createOptionsPanel());

		add(mainPanel, BorderLayout.CENTER);
	}
}
