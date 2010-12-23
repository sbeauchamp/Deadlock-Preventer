package com.freescale.deadlockpreventer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;

public class NetworkUtil {

	public static String[] readStringArray(DataInputStream input) throws IOException {
		String str = readString(input);
		int count = Integer.parseInt(str);
		String[] array = new String[count];
		for (int i = 0; i < count; i++)
			array[i] = readString(input);
		return array;
	}

	public static void writeStringArray(PrintStream output, String[] array) {
		writeString(output, Integer.toString(array.length));
		for (String item : array)
			writeString(output, item);
	}

	public static String readString(DataInputStream input) throws IOException {
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
	
	public static char readCharacter(DataInputStream input) throws IOException {
		int c = input.read();
		if (c == -1)
			throw new IOException("unexpected eos");
		return (char) c;
	}

	public static void writeString(PrintStream output, String string) {
		output.print("<" + Integer.toString(string.length()) + ">");
		output.print("<" + string + ">");
	}
}
