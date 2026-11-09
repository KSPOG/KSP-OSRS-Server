package net.runelite.client.plugins.microbot.inventorysetups.ui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import javax.swing.JTextField;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsDisplayAttributes;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsValidName;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventorySetupsNameActionsTest
{
	private static final int MAX_LENGTH = 10;

	@Test
	public void nullReplacementRemovesSelection()
	{
		final JTextField field = buildField("Setup name");
		field.select(5, 10);

		field.replaceSelection(null);

		assertEquals("Setup", field.getText());
	}

	@Test
	public void pasteIsStillCutToMaxLength()
	{
		final JTextField field = buildField("Setup");
		field.setCaretPosition(5);

		field.replaceSelection(" name that is too long");

		assertEquals("Setup name", field.getText());
	}

	private static JTextField buildField(String name)
	{
		final InventorySetupsDisplayAttributes datum = mock(InventorySetupsDisplayAttributes.class);
		when(datum.getName()).thenReturn(name);
		final InventorySetupsValidName validName = mock(InventorySetupsValidName.class);
		final InventorySetupsNameActions<InventorySetupsDisplayAttributes> actions = new InventorySetupsNameActions<>(
			datum, null, null, validName, null, MAX_LENGTH, Color.BLACK, false, new MouseAdapter()
		{
		});
		final JTextField field = actions.getNameInput().getTextField();
		field.setEditable(true);
		return field;
	}
}
