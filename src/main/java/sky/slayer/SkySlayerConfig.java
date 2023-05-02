package sky.slayer;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Units;

@ConfigGroup(SkySlayerConfig.GROUP_NAME)
public interface SkySlayerConfig extends Config
{
	String GROUP_NAME = "SkySlayer";

	// Key names for stored task values
	String TASK_NAME_KEY = "taskName";
	String SLAYER_MASTER_NAME_KEY = "slayerMaster";
	String AMOUNT_KEY = "amount";
	String INIT_AMOUNT_KEY = "initialAmount";
	String TASK_LOC_KEY = "taskLocation";
	String STREAK_KEY = "streak";
	String POINTS_KEY = "points";

	@ConfigItem(
		position = 1,
		keyName = "infobox",
		name = "Task InfoBox",
		description = "Display task information in an InfoBox"
	)
	default boolean showInfobox()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "itemoverlay",
		name = "Count on Items",
		description = "Display task count remaining on slayer items"
	)
	default boolean showItemOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "superiornotification",
		name = "Superior foe notification",
		description = "Toggles notifications on superior foe encounters"
	)
	default boolean showSuperiorNotification()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "statTimeout",
		name = "InfoBox Expiry",
		description = "Set the time until the InfoBox expires"
	)
	@Units(Units.MINUTES)
	default int statTimeout()
	{
		return 5;
	}

	@ConfigItem(
		position = 5,
		keyName = "highlightHull",
		name = "Highlight hull",
		description = "Configures whether the NPC hull should be highlighted"
	)
	default boolean highlightHull()
	{
		return false;
	}

	@ConfigItem(
		position = 6,
		keyName = "highlightTile",
		name = "Highlight tile",
		description = "Configures whether the NPC tile should be highlighted"
	)
	default boolean highlightTile()
	{
		return false;
	}

	@ConfigItem(
		position = 7,
		keyName = "highlightOutline",
		name = "Highlight outline",
		description = "Configures whether or not the NPC outline should be highlighted"
	)
	default boolean highlightOutline()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		position = 8,
		keyName = "targetColor",
		name = "Target color",
		description = "Color of the highlighted targets"
	)
	default Color getTargetColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		position = 9,
		keyName = "weaknessPrompt",
		name = "Show Monster Weakness",
		description = "Show an overlay on a monster when it is weak enough to finish off (Only Lizards, Gargoyles & Rockslugs)"
	)
	default boolean weaknessPrompt()
	{
		return true;
	}

	@ConfigItem(
		position = 10,
		keyName = "taskCommand",
		name = "Task Command",
		description = "Configures whether the slayer task command is enabled<br> !task"
	)
	default boolean taskCommand()
	{
		return true;
	}

	@ConfigItem(
			position = 11,
			keyName = "highlightTurael",
			name = "Highlight Turael",
			description = "Highlight tasks from turael"
	)
	default boolean highlightTurael()
	{
		return true;
	}

	@ConfigItem(
			position = 12,
			keyName = "highlightMinimap",
			name = "Highlight Minimap",
			description = "Highlight tasks on the minimap"
	)
	default boolean highlightMinimap()
	{
		return true;
	}
}
