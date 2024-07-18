package sky.slayer;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerAdditionsConfig.GROUP_NAME)
public interface SlayerAdditionsConfig extends Config
{
	String GROUP_NAME = "SlayerAdditions";
	String SLAYER_MASTER_NAME_KEY = "slayerMaster";

	@ConfigItem(
			position = 0,
			keyName = "highlightTurael",
			name = "Highlight Turael",
			description = "Highlight tasks from turael"
	)
	default boolean highlightTurael()
	{
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = "highlightMinimap",
			name = "Highlight Minimap",
			description = "Highlight tasks on the minimap"
	)
	default boolean highlightMinimap()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
			position = 2,
			keyName = "targetColor",
			name = "Target color",
			description = "Color of the highlighted targets"
	)
	default Color getTargetColor() {
		return Color.RED;
	}

	@ConfigItem(
			position = 3,
			keyName = "highlightMode",
			name = "Highlight mode",
			description = "How to highlight the targets"
	)
	default HighlightMode getHighlightMode() {
		return HighlightMode.Outline;
	}
}
