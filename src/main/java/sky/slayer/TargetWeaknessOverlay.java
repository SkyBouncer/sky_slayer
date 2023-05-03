package sky.slayer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class TargetWeaknessOverlay extends Overlay
{
	private final Client client;
	private final SkySlayerConfig config;
	private final SkySlayerPlugin plugin;
	private final ItemManager itemManager;
	private final NPCManager npcManager;

	@Inject
	private TargetWeaknessOverlay(Client client, SkySlayerConfig config, SkySlayerPlugin plugin, ItemManager itemManager, NPCManager npcManager)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.npcManager = npcManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final List<NPC> targets = plugin.getTargets();

		if (targets.isEmpty() || !config.weaknessPrompt())
		{
			return null;
		}

		final Task curTask = Task.getTask(plugin.getTaskName());
		if (curTask == null || curTask.getWeaknessThreshold() < 0 || curTask.getWeaknessItem() < 0)
		{
			return null;
		}

		final int threshold = curTask.getWeaknessThreshold();
		final BufferedImage image = itemManager.getImage(curTask.getWeaknessItem());

		if (image == null)
		{
			return null;
		}

		for (NPC target : targets)
		{
			final int currentHealth = calculateHealth(target);

			if (currentHealth >= 0 && currentHealth <= threshold)
			{
				renderTargetItem(graphics, target, image);
			}
		}

		return null;
	}

	private int calculateHealth(NPC target)
	{
		// Based on OpponentInfoOverlay HP calculation
		if (target == null || target.getName() == null)
		{
			return -1;
		}

		final int healthScale = target.getHealthScale();
		final int healthRatio = target.getHealthRatio();
		final Integer maxHealth = npcManager.getHealth(target.getId());

		if (healthRatio < 0 || healthScale <= 0 || maxHealth == null)
		{
			return -1;
		}

		return (int)((maxHealth * healthRatio / healthScale) + 0.5f);
	}

	private void renderTargetItem(Graphics2D graphics, NPC actor, BufferedImage image)
	{
		final LocalPoint actorPosition = actor.getLocalLocation();
		final int offset = actor.getLogicalHeight() + 40;

		if (actorPosition == null || image == null)
		{
			return;
		}

		final Point imageLoc = Perspective.getCanvasImageLocation(client, actorPosition, image, offset);

		if (imageLoc != null)
		{
			OverlayUtil.renderImageLocation(graphics, imageLoc, image);
		}
	}
}
