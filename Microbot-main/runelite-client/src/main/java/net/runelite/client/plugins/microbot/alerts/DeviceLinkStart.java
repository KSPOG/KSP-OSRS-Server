package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.ToString;
import lombok.Value;
import okhttp3.HttpUrl;

@Value
public class DeviceLinkStart
{
	private static final Pattern DEVICE_CODE = Pattern.compile("mbd_[A-Za-z0-9_-]{43}");
	private static final Pattern USER_CODE = Pattern.compile("[A-Z0-9]{4}-[A-Z0-9]{4}");

	@ToString.Exclude
	String deviceCode;
	String userCode;
	String verificationUrl;
	int expiresIn;
	int interval;

	public static DeviceLinkStart parse(Gson gson, int code, String body) throws IOException
	{
		if (code == 429)
		{
			throw new ThrottledException();
		}
		if (code != 200)
		{
			throw new IOException("Unable to start linking: HTTP " + code);
		}
		JsonObject json = DeviceLinkPoll.json(gson, body);
		String url = DeviceLinkPoll.string(json, "verificationUrl");
		HttpUrl verificationUrl = url == null ? null : HttpUrl.parse(url);
		DeviceLinkStart start = new DeviceLinkStart(
			DeviceLinkPoll.string(json, "deviceCode"),
			DeviceLinkPoll.string(json, "userCode"),
			verificationUrl == null ? null : verificationUrl.toString(),
			DeviceLinkPoll.integer(json, "expiresIn"),
			DeviceLinkPoll.integer(json, "interval"));
		if (start.deviceCode == null || !DEVICE_CODE.matcher(start.deviceCode).matches()
			|| start.userCode == null || !USER_CODE.matcher(start.userCode).matches()
			|| verificationUrl == null || !verificationUrl.isHttps())
		{
			throw new IOException("Unexpected link response");
		}
		return start;
	}

	public static class ThrottledException extends IOException
	{
		ThrottledException()
		{
			super("Too many link requests from this network");
		}
	}
}
