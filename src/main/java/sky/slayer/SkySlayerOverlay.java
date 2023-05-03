package sky.slayer;

import com.google.common.collect.ImmutableSet;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.TextComponent;

class SkySlayerOverlay extends WidgetItemOverlay
{
	private final static Set<Integer> SLAYER_JEWELRY = ImmutableSet.copyOf(ItemVariationMapping.getVariations(ItemID.SLAYER_RING_8));

	private final static Set<Integer> ALL_SLAYER_ITEMS = Stream.of(
		ItemVariationMapping.getVariations(ItemID.SLAYER_HELMET).stream(),
		ItemVariationMapping.getVariations(ItemID.SLAYER_RING_8).stream(),
		Stream.of(ItemID.ENCHANTED_GEM, ItemID.ETERNAL_GEM))
		.reduce(Stream::concat)
		.orElseGet(Stream::empty)
		.collect(Collectors.toSet());

	private final SkySlayerConfig config;
	private final SkySlayerPlugin plugin;

	@Inject
	private SkySlayerOverlay(SkySlayerPlugin plugin, SkySlayerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!ALL_SLAYER_ITEMS.contains(itemId))
		{
			return;
		}

		if (!config.showItemOverlay())
		{
			return;
		}

		int amount = plugin.getAmount();
		if (amount <= 0)
		{
			return;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		final Rectangle bounds = widgetItem.getCanvasBounds();
		final TextComponent textComponent = new TextComponent();
		textComponent.setText(String.valueOf(amount));

		// Draw the counter in the bottom left for equipment, and top left for jewelry
		textComponent.setPosition(new Point(bounds.x - 1, bounds.y - 1 + (SLAYER_JEWELRY.contains(itemId)
			? bounds.height
			: graphics.getFontMetrics().getHeight())));
		textComponent.render(graphics);
	}
}
