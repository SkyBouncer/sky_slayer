package sky.slayer;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SkySlayerConfig.GROUP_NAME)
public interface SkySlayerConfig extends Config
{
	String GROUP_NAME = "SkySlayer";
	String TASK_NAME_KEY = "taskName";
	String AMOUNT_KEY = "amount";
	String TASK_LOC_KEY = "taskLocation";
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
}
