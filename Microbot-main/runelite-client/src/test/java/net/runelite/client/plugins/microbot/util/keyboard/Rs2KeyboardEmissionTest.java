package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2KeyboardEmissionTest
{
	private final List<KeyEvent> received = new ArrayList<>();
	private final List<Long> receivedAt = new ArrayList<>();
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		Canvas canvas = new Canvas();
		canvas.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				record(e);
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				record(e);
			}

			@Override
			public void keyTyped(KeyEvent e)
			{
				record(e);
			}
		});

		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		previousClient = swapStatic("client", client);

		InputArbiter.resetForTest();
		PointerState.reset();
		Rs2Keyboard.releaseHeldKeys();
		received.clear();
		receivedAt.clear();
	}

	@After
	public void after() throws Exception
	{
		InputArbiter.resetForTest();
		PointerState.reset();
		Rs2Keyboard.releaseHeldKeys();
		swapStatic("client", previousClient);
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test
	public void eachCharacterIsPressedTypedAndReleased()
	{
		Rs2Keyboard.typeString("a1");

		assertEquals(6, received.size());
		assertKey(received.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a');
		assertKey(received.get(1), KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'a');
		assertKey(received.get(2), KeyEvent.KEY_RELEASED, KeyEvent.VK_A, 'a');
		assertKey(received.get(3), KeyEvent.KEY_PRESSED, KeyEvent.VK_1, '1');
		assertKey(received.get(4), KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '1');
		assertKey(received.get(5), KeyEvent.KEY_RELEASED, KeyEvent.VK_1, '1');
	}

	@Test
	public void theKeyIsHeldDownForAHumanDuration()
	{
		Rs2Keyboard.typeString("a");

		long held = receivedAt.get(2) - receivedAt.get(0);
		assertTrue("held " + held + "ms", held >= Rs2Keyboard.KEY_HOLD_MIN_MS - 5);
	}

	@Test
	public void eventTimestampsAreNeverInTheFuture()
	{
		Rs2Keyboard.typeString("ab");
		Rs2Keyboard.keyPress(KeyEvent.VK_F1);

		for (int i = 0; i < received.size(); i++)
		{
			assertTrue("event " + i + " stamped ahead of its delivery",
				received.get(i).getWhen() <= receivedAt.get(i));
		}
	}

	@Test
	public void anUppercaseCharacterIsWrappedInShift()
	{
		Rs2Keyboard.typeString("B");

		assertEquals(5, received.size());
		assertKey(received.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
		assertKey(received.get(1), KeyEvent.KEY_PRESSED, KeyEvent.VK_B, 'B');
		assertKey(received.get(2), KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'B');
		assertKey(received.get(3), KeyEvent.KEY_RELEASED, KeyEvent.VK_B, 'B');
		assertKey(received.get(4), KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);

		for (int i = 0; i < 4; i++)
		{
			assertTrue("event " + i + " must carry shift", (received.get(i).getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
		}
		assertEquals(0, received.get(4).getModifiersEx() & InputEvent.SHIFT_DOWN_MASK);
		assertFalse(Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT));
	}

	@Test
	public void aShiftTheCallerHoldsIsNeitherPressedAgainNorReleased()
	{
		Rs2Keyboard.holdShift();
		received.clear();

		Rs2Keyboard.typeString("@");

		assertEquals(3, received.size());
		assertKey(received.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_2, '@');
		assertTrue(Rs2Keyboard.isKeyHeld(KeyEvent.VK_SHIFT));
	}

	@Test
	public void aCharacterWithoutAKeyIsTypedOnly()
	{
		Rs2Keyboard.typeString("\u00e9");

		assertEquals(1, received.size());
		assertKey(received.get(0), KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\u00e9');
	}

	@Test
	public void aNonPrintableKeyPressIsHeldBeforeRelease()
	{
		Rs2Keyboard.keyPress(KeyEvent.VK_F1);

		assertEquals(2, received.size());
		assertKey(received.get(0), KeyEvent.KEY_PRESSED, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED);
		assertKey(received.get(1), KeyEvent.KEY_RELEASED, KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED);
		assertTrue(receivedAt.get(1) - receivedAt.get(0) >= Rs2Keyboard.KEY_HOLD_MIN_MS - 5);
		assertFalse(Rs2Keyboard.isKeyHeld(KeyEvent.VK_F1));
	}

	@Test
	public void keyCodesFollowAUsLayout()
	{
		assertEquals(KeyEvent.VK_Q, Rs2Keyboard.keyCodeFor('Q'));
		assertEquals(KeyEvent.VK_0, Rs2Keyboard.keyCodeFor(')'));
		assertEquals(KeyEvent.VK_SLASH, Rs2Keyboard.keyCodeFor('?'));
		assertEquals(KeyEvent.VK_BACK_QUOTE, Rs2Keyboard.keyCodeFor('~'));
		assertTrue(Rs2Keyboard.needsShift('?'));
		assertFalse(Rs2Keyboard.needsShift('/'));
		assertFalse(Rs2Keyboard.needsShift(' '));
	}

	private void record(KeyEvent event)
	{
		received.add(event);
		receivedAt.add(System.currentTimeMillis());
	}

	private static void assertKey(KeyEvent event, int id, int keyCode, char keyChar)
	{
		assertEquals(id, event.getID());
		assertEquals(keyCode, event.getKeyCode());
		assertEquals(keyChar, event.getKeyChar());
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
