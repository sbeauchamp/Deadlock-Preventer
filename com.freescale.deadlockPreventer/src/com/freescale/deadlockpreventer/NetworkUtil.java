/*******************************************************************************
 * Copyright (c) 2010 Freescale Semiconductor.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package com.freescale.deadlockpreventer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class NetworkUtil {

	public static ArrayList<String> readStringArray(DataInputStream input) throws IOException {
		String str = readString(input);
		int count = Integer.parseInt(str);
		
		str = readString(input);
		int totalBytes = Integer.parseInt(str);
		byte[] bytes = new byte[totalBytes];
		int bytesRead = input.read(bytes);
		while (bytesRead < bytes.length)
			bytesRead += input.read(bytes, bytesRead, bytes.length - bytesRead);
		ByteArrayInputStream byteInput = new ByteArrayInputStream(bytes);
		ArrayList<String> array = new ArrayList<String>();
		for (int i = 0; i < count; i++)
			array.add(readString(byteInput));
		return array;
	}
	
	public static void writeStringArray(OutputStream output, ArrayList<String> array) throws IOException {
		writeString(output, Integer.toString(array.size()));
		int totalBytes = 0;
		for (String item : array)
			totalBytes += calculateStringOutput(item);
		writeString(output, Integer.toString(totalBytes));
		ByteArrayOutputStream byteOuputStream = new ByteArrayOutputStream();
		for (String item : array)
			writeString(byteOuputStream, item);
		byteOuputStream.flush();
		output.write(byteOuputStream.toByteArray());
	}
	
	public static String readString(InputStream input) throws IOException {
		char c = readCharacter(input);
		while (c != '<')
			c = readCharacter(input);
		
		StringBuffer buffer = new StringBuffer();
		c = readCharacter(input);
		while (c != '>') {
			buffer.append(c);
			c = readCharacter(input);
		}
		int length = Integer.parseInt(buffer.toString());
		c = readCharacter(input);
		if (c != '<')
			throw new IOException("unexpected character: " + c);

		StringBuffer string = new StringBuffer();
		while (length--  > 0) {
			c = readCharacter(input);
			string.append(c);
		}

		c = readCharacter(input);
		if (c != '>')
			throw new IOException("unexpected character: " + c);
			
		return string.toString();
	}
	
	public static char readCharacter(InputStream input) throws IOException {
		int c = input.read();
		if (c == -1)
			throw new IOException("unexpected eos");
		return (char) c;
	}

	public static void writeString(OutputStream output, String string) throws IOException {
		// if changed, don't forget to update calculateStringOutput()
		output.write(("<" + Integer.toString(string.length()) + ">").getBytes());
		output.write(("<" + string + ">").getBytes());
	}
	
	private static int calculateStringOutput(String string) {
		return Integer.toString(string.length()).getBytes().length + string.getBytes().length + 4;
	}
}
