package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import lombok.ToString;
import lombok.Value;

@Value
public class DeviceLinkPoll
{
	public enum Status
	{
		LINKED,
		PENDING,
		SLOW_DOWN,
		DENIED,
		EXPIRED,
		TOO_MANY_LINKS
	}

	Status status;
	@ToString.Exclude
	String token;
	String label;

	public static DeviceLinkPoll of(Status status)
	{
		return new DeviceLinkPoll(status, null, null);
	}

	public static DeviceLinkPoll parse(Gson gson, int code, String body) throws IOException
	{
		if (code == 429)
		{
			return of(Status.SLOW_DOWN);
		}
		if (code == 200)
		{
			JsonObject json = json(gson, body);
			String token = string(json, "token");
			if (ClientLinkToken.resolve(null, token).isEmpty())
			{
				throw new IOException("Unexpected link response");
			}
			return new DeviceLinkPoll(Status.LINKED, token, string(json, "label"));
		}
		if (code != 400)
		{
			throw new IOException("Unable to check the link: HTTP " + code);
		}
		String error = string(json(gson, body), "error");
		if (error == null)
		{
			throw new IOException("Unexpected link response");
		}
		switch (error)
		{
			case "authorization_pending":
				return of(Status.PENDING);
			case "slow_down":
				return of(Status.SLOW_DOWN);
			case "access_denied":
				return of(Status.DENIED);
			case "expired_token":
				return of(Status.EXPIRED);
			case "too_many_links":
				return of(Status.TOO_MANY_LINKS);
			default:
				throw new IOException("Unexpected link response");
		}
	}

	static JsonObject json(Gson gson, String body) throws IOException
	{
		try
		{
			JsonObject json = gson.fromJson(body, JsonObject.class);
			if (json == null)
			{
				throw new IOException("Empty link response");
			}
			return json;
		}
		catch (JsonParseException | ClassCastException e)
		{
			throw new IOException("Unexpected link response", e);
		}
	}

	static String string(JsonObject json, String name)
	{
		JsonElement value = json.get(name);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	static int integer(JsonObject json, String name)
	{
		JsonElement value = json.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : 0;
	}
}
