package com.microbot.hwid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class VanillaDeviceId
{
	private static final String FALLBACK_DEVICE_ID = "12345678-0000-0000-0000-123456789012";

	private VanillaDeviceId()
	{
	}

	public static String getDeviceId(int osType)
	{
		String command;
		String deviceId = FALLBACK_DEVICE_ID;
		switch (osType)
		{
			case 1:
				command = "wmic csproduct get UUID";
				break;
			case 2:
				command = "system_profiler SPHardwareDataType | awk '/UUID/ { print $3; }'";
				break;
			case 3:
				command = "cat /etc/machine-id";
				break;
			default:
				return "Unknown";
		}

		BufferedReader outputReader = null;

		try
		{
			Process cmdProcess = Runtime.getRuntime().exec(command);
			outputReader = new BufferedReader(new InputStreamReader(cmdProcess.getInputStream()));
			StringBuilder output = new StringBuilder();

			String line;
			while ((line = outputReader.readLine()) != null)
			{
				output.append(line).append("\n");
			}

			if (osType == 1)
			{
				deviceId = output.substring(output.indexOf("\n"), output.length()).trim();
			}
			else if (osType == 2)
			{
				int uuidEndIndex = output.indexOf("UUID: ") + 36;
				deviceId = output.substring(output.indexOf("UUID: "), uuidEndIndex).replace("UUID: ", "");
			}
			else
			{
				if (output.length() == 33)
				{
					output = new StringBuilder(output.substring(0, output.length() - 1));
				}

				if (output.length() == 32)
				{
					output.insert(20, "-");
					output.insert(16, "-");
					output.insert(12, "-");
					output.insert(8, "-");
					deviceId = output.toString();
				}
				else
				{
					deviceId = FALLBACK_DEVICE_ID;
				}
			}
		}
		catch (Exception ignored)
		{
		}
		finally
		{
			try
			{
				if (outputReader != null)
				{
					outputReader.close();
				}
			}
			catch (IOException ignored)
			{
			}
		}

		return deviceId;
	}
}
