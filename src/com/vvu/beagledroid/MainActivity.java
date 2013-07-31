/* * Copyright 2013 Vlad V. Ungureanu <ungureanuvladvictor@gmail.com>. * * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this Github repository and wiki except in * compliance with the License. You may obtain a copy of the License at * *       http://www.apache.org/licenses/LICENSE-2.0 * *  Unless required by applicable law or agreed to in writing, software *  distributed under the License is distributed on an "AS IS" BASIS, *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *  See the License for the specific language governing permissions and *  limitations under the License. */package com.vvu.beagledroid;import android.app.Activity;import android.content.Context;import android.hardware.usb.UsbDevice;import android.hardware.usb.UsbDeviceConnection;import android.hardware.usb.UsbEndpoint;import android.hardware.usb.UsbInterface;import android.hardware.usb.UsbManager;import android.os.Bundle;import android.os.Environment;import android.os.SystemClock;import android.util.Log;import android.view.Menu;import android.view.View;import android.view.View.OnClickListener;import android.widget.Button;import android.widget.TextView;import java.io.File;import java.io.FileInputStream;import java.nio.ByteBuffer;import java.util.Arrays;import java.util.HashMap;import java.util.Iterator;public class MainActivity extends Activity {		static final String TAG = "BBB";	static final byte[] AndroidMac = {(byte)0x9A, (byte)0x1F, (byte)0x85, (byte)0x1C, (byte)0x3D, (byte)0x0E};	static final byte[] BBBIP = {(byte)0xC0, (byte)0xA8, (byte)0x01, (byte)0x03};	static final byte[] AndroidIP = {(byte)0xC0, (byte)0xA8, (byte)0x01, (byte)0x09};	static final byte[] serverName = {(byte)'A', (byte)'n', (byte)'d', (byte)'r', (byte)'o', (byte)'i', (byte)'d', (byte)'\0'};	static final byte[] fileName = {(byte)'M', (byte)'L', (byte)'O', (byte)'\0'};	static final byte[] END = {(byte)'F', (byte)'I', (byte)'N'};	static final byte protocolUDP = (byte) 0x11;	static final short ethARP = (short) 0x0806;	static final short ethIP = (short) 0x0800;	static final short BOOTPS = (short) 67;	static final short BOOTPC = (short) 68;	static final short romxID = 1;	static final int RNDISSize = 44;	static final int ETHSize = 14;	static final int IPSize = 20;	static final int ARPSize = 28;	static final int UDPSize = 8;	static final int BOOTPSize = 300;	static final int TFTPSize = 4;	long soFar = 0;	@Override    protected void onCreate(Bundle savedInstanceState) {        super.onCreate(savedInstanceState);        setContentView(R.layout.activity_main);        Button myMagic = (Button) findViewById(R.id.button1);                myMagic.setOnClickListener(new OnClickListener() {						public void onClick(View v) {				new Thread(new Runnable() {					public void run() {						runRom();						SystemClock.sleep(2000);						runUBoot();						SystemClock.sleep(5000);						runFIT();						SystemClock.sleep(21000);						runWrite();					}				}).start();			}		});}	@Override    public boolean onCreateOptionsMenu(Menu menu) {        // Inflate the menu; this adds items to the action bar if it is present.        getMenuInflater().inflate(R.menu.main, menu);        return true;    }		public void runRom() {		new Thread(new Runnable() {			public void run() {				byte[] buffer = new byte[450];				UsbDevice myDev = null;				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();				while (deviceIterator.hasNext()) {					UsbDevice dev = deviceIterator.next();					if (dev.getProductId() == 24897 && dev.getVendorId() == 1105)						myDev = dev;				}				if (myDev == null) {					Log.d(TAG, "nothing!");					return;				}				debugDevice(myDev);				UsbInterface intf = myDev.getInterface(1);				UsbEndpoint readEP = intf.getEndpoint(0);				UsbEndpoint writeEP = intf.getEndpoint(1);				UsbDeviceConnection connection = null;				if (manager.hasPermission(myDev)) {					connection = manager.openDevice(myDev);				} 				else Log.d(TAG, "no permissions");				connection.claimInterface(intf, true);				int tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				byte[] BBBMac = Arrays.copyOfRange(Arrays.copyOfRange(buffer, 						RNDISSize, RNDISSize + ETHSize),6,12);				BOOTP bootpAnswer = new BOOTP(romxID, BBBIP, AndroidIP, AndroidIP, BBBMac, serverName, fileName);				UDP udpAnswer = new UDP(BOOTPS, BOOTPC, (short)BOOTPSize);				IPv4 ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, protocolUDP, AndroidIP, BBBIP);				Ether2 etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);				RNDIS rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + BOOTPSize);				ByteMaker send = new ByteMaker();				byte[] output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), ipAnswer.getByteArray(), udpAnswer.getByteArray(), bootpAnswer.getByteArray());				tmp = connection.bulkTransfer(writeEP, output, output.length, 10);				tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				ARP arpAnswer = new ARP((short)2, AndroidMac, AndroidIP, BBBMac, BBBIP);				etherAnswer.setH_proto(ethARP);				rndisAnswer.updateRNDIS(ETHSize+ARPSize);								output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), arpAnswer.getByteArray());				tmp = connection.bulkTransfer(writeEP, output, output.length, 10);				tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);								try {					File myFile = new File(Environment.getExternalStorageDirectory().getPath() + "/BBB/u-boot-spl.bin");					FileInputStream fIn = new FileInputStream(myFile);					byte[] temporary = new byte[512];					int block = 1;					int count = fIn.read(temporary, 0, 512);					while(count != -1) {						TFTP tftpAnswer = new TFTP((short)3, (short)block);						udpAnswer = new UDP((short)0x45, (short)0x4D2, (short) (TFTPSize + count));						ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, protocolUDP, AndroidIP, BBBIP);						etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);						rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + TFTPSize + count);						tftpAnswer.setBlk_numer((short)block);												output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), ipAnswer.getByteArray(), udpAnswer.getByteArray(), tftpAnswer.getByteArray(), temporary);												byte[] result = send.stripSize(output, RNDISSize + ETHSize + IPSize + UDPSize + TFTPSize + count);						tmp = connection.bulkTransfer(writeEP, result, result.length, 200);						buffer = new byte[450];						tmp = connection.bulkTransfer(readEP, buffer, 450, 200);						temporary = new byte[512];						block++;						count = fIn.read(temporary, 0, 512);					}					fIn.close();				} catch (Exception e) {					Log.d(TAG, e.getMessage());					return;				}			}		}).start();	}	public void runUBoot() {		new Thread(new Runnable() {			public void run() {				byte[] buffer = new byte[450];				UsbDevice myDev = null;				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();				while (deviceIterator.hasNext()) {					UsbDevice dev = deviceIterator.next();					if (dev.getProductId() == 42146 && dev.getVendorId() == 1317)						myDev = dev;				}				if (myDev == null) {					Log.d(TAG, "nothing!");					return;				}				debugDevice(myDev);				UsbInterface intf = myDev.getInterface(1);				UsbEndpoint readEP = intf.getEndpoint(0);				UsbEndpoint writeEP = intf.getEndpoint(1);				UsbDeviceConnection connection = null;				if (manager.hasPermission(myDev)) {					connection = manager.openDevice(myDev);				} 				else Log.d(TAG, "no permissions");				connection.claimInterface(intf, true);				int tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				byte[] BBBMac = Arrays.copyOfRange(Arrays.copyOfRange(buffer, 						RNDISSize, RNDISSize + ETHSize),6,12);				byte[] bootp = Arrays.copyOfRange(buffer, RNDISSize + ETHSize + 												IPSize + UDPSize, RNDISSize + ETHSize + 												IPSize + UDPSize + BOOTPSize);				byte[] xid = Arrays.copyOfRange(bootp, 4, 8);				BOOTP bootpAnswer = new BOOTP(ByteBuffer.wrap(xid).getInt(), BBBIP, AndroidIP, 												AndroidIP, BBBMac, serverName, fileName);				UDP udpAnswer = new UDP(BOOTPS, BOOTPC, (short)BOOTPSize);				IPv4 ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, 													protocolUDP, AndroidIP, BBBIP);				Ether2 etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);				RNDIS rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + BOOTPSize);				ByteMaker send = new ByteMaker();				byte[] output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), 												ipAnswer.getByteArray(), udpAnswer.getByteArray(), 												bootpAnswer.getByteArray());				tmp = connection.bulkTransfer(writeEP, output, output.length, 10);				tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				ARP arpAnswer = new ARP((short)2, AndroidMac, AndroidIP, BBBMac, BBBIP);				etherAnswer.setH_proto(ethARP);				rndisAnswer.updateRNDIS(ETHSize+ARPSize);								output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), arpAnswer.getByteArray());				tmp = connection.bulkTransfer(writeEP, output, output.length, 10);				tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				byte[] udp = Arrays.copyOfRange(buffer, RNDISSize +ETHSize + IPSize, RNDISSize +ETHSize + IPSize + UDPSize);				byte[] dstPort = Arrays.copyOfRange(udp, 0, 2);				byte[] srcPort = Arrays.copyOfRange(udp, 2, 4);				short sPort = ByteBuffer.wrap(srcPort).getShort();				short dPort = ByteBuffer.wrap(dstPort).getShort();								try {					File myFile = new File(Environment.getExternalStorageDirectory().getPath() + "/BBB/u-boot.img");					FileInputStream fIn = new FileInputStream(myFile);					byte[] temporary = new byte[512];					int block = 1;					int count = fIn.read(temporary, 0, 512);					while(count != -1) {						TFTP tftpAnswer = new TFTP((short)3, (short)block);						udpAnswer = new UDP(sPort, dPort, (short) (TFTPSize + count));						ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, protocolUDP, AndroidIP, BBBIP);						etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);						rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + TFTPSize + count);						tftpAnswer.setBlk_numer((short)block);												output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), 												ipAnswer.getByteArray(), udpAnswer.getByteArray(), 												tftpAnswer.getByteArray(), temporary);												byte[] result = send.stripSize(output, RNDISSize + ETHSize + IPSize + UDPSize + TFTPSize + count);						tmp = connection.bulkTransfer(writeEP, result, result.length, 200);						buffer = new byte[450];						tmp = connection.bulkTransfer(readEP, buffer, 450, 200);						temporary = new byte[512];						block++;						count = fIn.read(temporary, 0, 512);					}					fIn.close();				} catch (Exception e) {					Log.d(TAG, e.getMessage());					return;				}			}		}).start();	}	public void runFIT() {		new Thread(new Runnable() {			public void run() {				byte[] buffer = new byte[450];				UsbDevice myDev = null;				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();				while (deviceIterator.hasNext()) {					UsbDevice dev = deviceIterator.next();					if (dev.getProductId() == 42146 && dev.getVendorId() == 1317)						myDev = dev;				}				if (myDev == null) {					Log.d(TAG, "nothing!");					return;				}				debugDevice(myDev);				UsbInterface intf = myDev.getInterface(1);				UsbEndpoint readEP = intf.getEndpoint(0);				UsbEndpoint writeEP = intf.getEndpoint(1);				UsbDeviceConnection connection = null;				if (manager.hasPermission(myDev)) {					connection = manager.openDevice(myDev);				} 				connection.claimInterface(intf, true);				int tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				byte[] BBBMac = Arrays.copyOfRange(Arrays.copyOfRange(buffer, 						RNDISSize, RNDISSize + ETHSize),6,12);								UDP udpAnswer = new UDP(BOOTPS, BOOTPC, (short)BOOTPSize);				IPv4 ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, 													protocolUDP, AndroidIP, BBBIP);				Ether2 etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);				RNDIS rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + BOOTPSize);				ByteMaker send = new ByteMaker();				ARP arpAnswer = new ARP((short)2, AndroidMac, AndroidIP, BBBMac, BBBIP);				etherAnswer.setH_proto(ethARP);				rndisAnswer.updateRNDIS(ETHSize+ARPSize);								byte[] output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), arpAnswer.getByteArray());				tmp = connection.bulkTransfer(writeEP, output, output.length, 10);				tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				byte[] udp = Arrays.copyOfRange(buffer, RNDISSize +ETHSize + IPSize, RNDISSize +ETHSize + IPSize + UDPSize);				byte[] dstPort = Arrays.copyOfRange(udp, 0, 2);				byte[] srcPort = Arrays.copyOfRange(udp, 2, 4);				short sPort = ByteBuffer.wrap(srcPort).getShort();				short dPort = ByteBuffer.wrap(dstPort).getShort();								try {					File myFile = new File(Environment.getExternalStorageDirectory().getPath() + "/BBB/maker.itb");					FileInputStream fIn = new FileInputStream(myFile);					byte[] temporary = new byte[512];					int block = 1;					int count = fIn.read(temporary, 0, 512);					while(count != -1) {						TFTP tftpAnswer = new TFTP((short)3, (short)block);						udpAnswer = new UDP(sPort, dPort, (short) (TFTPSize + count));						ipAnswer = new IPv4((short) (udpAnswer.getLen() + (short)IPSize), (short)0, protocolUDP, AndroidIP, BBBIP);						etherAnswer = new Ether2(BBBMac, AndroidMac, ethIP);						rndisAnswer = new RNDIS(ETHSize + IPSize + UDPSize + TFTPSize + count);						tftpAnswer.setBlk_numer((short)block);												output = send.converter(rndisAnswer.getByteArray(), etherAnswer.getByteArray(), 												ipAnswer.getByteArray(), udpAnswer.getByteArray(), 												tftpAnswer.getByteArray(), temporary);												byte[] result = send.stripSize(output, RNDISSize + ETHSize + IPSize + UDPSize + TFTPSize + count);						tmp = connection.bulkTransfer(writeEP, result, result.length, 200);						buffer = new byte[450];						tmp = connection.bulkTransfer(readEP, buffer, 450, 200);						temporary = new byte[512];						block++;						count = fIn.read(temporary, 0, 512);					}					fIn.close();				} catch (Exception e) {					Log.d(TAG, e.getMessage());					return;				}				connection.close();			}		}).start();	}	public void runSerial() {		new Thread(new Runnable() {			public void run() {				byte[] buffer = new byte[512];				UsbDevice myDev = null;				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();				while (deviceIterator.hasNext()) {					UsbDevice dev = deviceIterator.next();					if (dev.getProductId() == 42151 && dev.getVendorId() == 1317)						myDev = dev;				}				if (myDev == null) {					Log.d(TAG, "nothing!");					return;				}				UsbInterface intf = myDev.getInterface(1);				UsbEndpoint readEP = intf.getEndpoint(0);				UsbDeviceConnection connection = null;				if (manager.hasPermission(myDev)) {					connection = manager.openDevice(myDev);				} 				connection.claimInterface(intf, true);				int tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				while (tmp < 0) tmp = connection.bulkTransfer(readEP, buffer, 450, 10);				Log.d(TAG, HexDump.dumpHexString(buffer, 0, tmp));			}		}).start();	}	public void debugDevice(UsbDevice device) {	        Log.i(TAG,"Model: " + device.getDeviceName());	        Log.i(TAG,"ID: " + device.getDeviceId());	        Log.i(TAG,"Class: " + device.getDeviceClass());	        Log.i(TAG,"Protocol: " + device.getDeviceProtocol());	        Log.i(TAG,"Vendor ID " + device.getVendorId());	        Log.i(TAG,"Product ID: " + device.getProductId());	        Log.i(TAG,"Interface count: " + device.getInterfaceCount());	        Log.i(TAG,"---------------------------------------");	   // Get interface details	        for (int index = 0; index < device.getInterfaceCount(); index++)	        {	        UsbInterface mUsbInterface = device.getInterface(index);	        Log.i(TAG,"  *****     *****");	        Log.i(TAG,"  Interface index: " + index);	        Log.i(TAG,"  Interface ID: " + mUsbInterface.getId());	        Log.i(TAG,"  Inteface class: " + mUsbInterface.getInterfaceClass());	        Log.i(TAG,"  Interface protocol: " + mUsbInterface.getInterfaceProtocol());	        Log.i(TAG,"  Endpoint count: " + mUsbInterface.getEndpointCount());	    // Get endpoint details 	            for (int epi = 0; epi < mUsbInterface.getEndpointCount(); epi++)	        {	            UsbEndpoint mEndpoint = mUsbInterface.getEndpoint(epi);	            Log.i(TAG,"    ++++   ++++   ++++");	            Log.i(TAG,"    Endpoint index: " + epi);	            Log.i(TAG,"    Attributes: " + mEndpoint.getAttributes());	            Log.i(TAG,"    Direction: " + mEndpoint.getDirection());	            Log.i(TAG,"    Number: " + mEndpoint.getEndpointNumber());	            Log.i(TAG,"    Interval: " + mEndpoint.getInterval());	            Log.i(TAG,"    Packet size: " + mEndpoint.getMaxPacketSize());	            Log.i(TAG,"    Type: " + mEndpoint.getType());	            }	       }	}	public void runWrite() {		new Thread(new Runnable() {			public void run() {				UsbDevice myDev = null;				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();				while (deviceIterator.hasNext()) {					UsbDevice dev = deviceIterator.next();					if (dev.getProductId() == 42151 && dev.getVendorId() == 1317)						myDev = dev;				}				if (myDev == null) {					Log.d(TAG, "nothing!");					return;				}				UsbInterface intf = myDev.getInterface(1);				UsbEndpoint writeEP = intf.getEndpoint(1);				UsbEndpoint readEP = intf.getEndpoint(0);				UsbDeviceConnection connection = null;				if (manager.hasPermission(myDev)) {					connection = manager.openDevice(myDev);				} 				if (connection == null) {					Log.d(TAG, "conn null");					return;				}				try {					connection.claimInterface(intf, true);				}				catch (Exception e) {					Log.d(TAG, e.getMessage());					return;				}				int tmp;				try {					File myFile = new File(Environment.getExternalStorageDirectory().getPath() + "/BBB/flash.img.xz");					FileInputStream fIn = new FileInputStream(myFile);					byte[] name = myFile.getName().getBytes();					tmp = -1;					while(tmp<0) tmp = connection.bulkTransfer(writeEP, name, name.length, 10);					byte[] size = ByteBuffer.allocate(8).putLong(myFile.length()).array();					tmp = -1;					while(tmp < 0) tmp = connection.bulkTransfer(writeEP, size, size.length, 10);					byte[] temporary = new byte[1024];					int count = fIn.read(temporary, 0, 1024);					final long total = myFile.length();					byte[] reader = new byte[10];					tmp = connection.bulkTransfer(readEP, reader, 5, 200);					while (tmp < 0) tmp = connection.bulkTransfer(readEP, reader, 5, 10);					//Log.d(TAG, HexDump.dumpHexString(reader, 0, tmp));					while(count != -1) {						int tmP = connection.bulkTransfer(writeEP, temporary, count, 0);						while(tmP < 0) tmP = connection.bulkTransfer(writeEP, temporary, count, 10);						tmp = connection.bulkTransfer(readEP, reader, 3, 0);						while (tmp < 0) tmp = connection.bulkTransfer(readEP, reader, 3, 10);						if (tmP >0) soFar += tmP;						Log.d(TAG, ""+soFar);						runOnUiThread(new Runnable() {							@Override							public void run() {								TextView textView = (TextView) findViewById(R.id.textView1);								textView.setText((soFar*100)/total+"%");							}						});						temporary = new byte[1024];						count = fIn.read(temporary, 0, 1024);					}					SystemClock.sleep(2000);					tmp = -1;					while(tmp < 0) tmp = connection.bulkTransfer(writeEP, END, 3, 10);					fIn.close();					soFar = 0;				} catch (Exception e) {					Log.d(TAG, e.getMessage());					return;				}			}		}).start();	}	}