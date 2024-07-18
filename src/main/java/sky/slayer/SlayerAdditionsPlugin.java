/*
 * Plugin for additional slayer features.
 * Based on the original slayer plugin from RuneLite.
 */

package sky.slayer;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.game.npcoverlay.NpcOverlayService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

@PluginDescriptor(
	name = "Slayer Additions",
	description = "Slayer additions",
	tags = {"slayer", "highlight", "overlay", "minimap", "tasks"}
)
@Slf4j
public class SlayerAdditionsPlugin extends Plugin
{
	private static final String TURAEL = "Turael";
	private static final String Aya = "Aya";
	private static final String SPRIA = "Spria";

	private static final Pattern SLAYER_ASSIGN_MESSAGE = Pattern.compile(".*(?:Your new task is to kill \\d+) (?<name>.+)(?:.)");
	private static final Pattern SLAYER_CURRENT_MESSAGE = Pattern.compile(".*(?:You're still hunting) (?<name>.+)(?:[,;] you have \\d+ to go.)");

	@Inject
	private Client client;

	@Inject
	private SlayerAdditionsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private NpcOverlayService npcOverlayService;

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> targets = new ArrayList<>();

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int amount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int initialAmount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String taskLocation;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String taskName;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String slayerMaster;

	private boolean loginFlag;
	private final List<Pattern> targetNames = new ArrayList<>();

	public final Function<NPC, HighlightedNpc> slayerAdditionsHighlighter = (n) ->
	{
		boolean shouldHighlight = config.highlightTurael() && (slayerMaster.equals(TURAEL) || slayerMaster.equals(Aya) || slayerMaster.equals(SPRIA));
		if (targets.contains(n) && (config.highlightMinimap() || shouldHighlight))
		{
			Color color = config.getTargetColor();
			return HighlightedNpc.builder()
					.npc(n)
					.highlightColor(color)
					.fillColor(ColorUtil.colorWithAlpha(color, color.getAlpha() / 12))
					.outline(shouldHighlight && config.getHighlightMode() == HighlightMode.Outline)
					.hull(shouldHighlight && config.getHighlightMode() == HighlightMode.Hull)
					.tile(shouldHighlight && config.getHighlightMode() == HighlightMode.Tile)
					.trueTile(shouldHighlight && config.getHighlightMode() == HighlightMode.Truetile)
					.render(npc -> !npc.isDead())
					.build();
		}

		return null;
	};

	@Override
	protected void startUp()
	{
		npcOverlayService.registerHighlighter(slayerAdditionsHighlighter);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginFlag = true;
			clientThread.invoke(this::updateTask);
		}
	}

	@Override
	protected void shutDown()
	{
		npcOverlayService.unregisterHighlighter(slayerAdditionsHighlighter);
		targets.clear();
	}

	@Provides
	SlayerAdditionsConfig provideSlayerConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerAdditionsConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case HOPPING:
			case LOGGING_IN:
				taskName = "";
				amount = 0;
				loginFlag = true;
				targets.clear();
				break;
		}
	}

	private void saveSlayerMaster(String master)
	{
		slayerMaster = master;
		configManager.setRSProfileConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY, master);
	}

	private void removeSlayerMaster()
	{
		slayerMaster = "";
		configManager.unsetRSProfileConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		if (isTarget(npc))
		{
			targets.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		targets.remove(npc);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		int varpId = varbitChanged.getVarpId();
		if (varpId == VarPlayer.SLAYER_TASK_SIZE || varpId == VarPlayer.SLAYER_TASK_LOCATION || varpId == VarPlayer.SLAYER_TASK_CREATURE)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	private void updateTask()
	{
		int amount = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
		if (amount > 0)
		{
			String storedSlayerMaster = configManager.getRSProfileConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY);
			slayerMaster = storedSlayerMaster == null ? "" : storedSlayerMaster;

			int taskId = client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE);
			String taskName;
			if (taskId == 98)
			{
				int structId = client.getEnum(EnumID.SLAYER_TASK).getIntValue(client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
				taskName = client.getStructComposition(structId).getStringValue(ParamID.SLAYER_TASK_NAME);
			}
			else
			{
				taskName = client.getEnum(EnumID.SLAYER_TASK_CREATURE).getStringValue(taskId);
			}

			int areaId = client.getVarpValue(VarPlayer.SLAYER_TASK_LOCATION);
			String taskLocation = null;
			if (areaId > 0)
			{
				taskLocation = client.getEnum(EnumID.SLAYER_TASK_LOCATION).getStringValue(areaId);
			}

			if (loginFlag || !Objects.equals(taskName, this.taskName) || !Objects.equals(taskLocation, this.taskLocation))
			{
				setTask(taskName, amount, taskLocation);
			}
			else if (amount != this.amount)
			{
				this.amount = amount;
			}
		}
		else if (this.amount > 0)
		{
			resetTask();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		loginFlag = false;
		Widget npcName = client.getWidget(ComponentID.DIALOG_NPC_NAME);
		Widget npcDialog = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
		if (npcDialog != null && npcName != null && (npcName.getText().equals(TURAEL) || npcName.getText().equals(Aya) || npcName.getText().equals(SPRIA)))
		{
			String npcText = Text.sanitizeMultilineText(npcDialog.getText());
			final Matcher mAssign = SLAYER_ASSIGN_MESSAGE.matcher(npcText);
			final Matcher mCurrent = SLAYER_CURRENT_MESSAGE.matcher(npcText);

			if (mAssign.find() || mCurrent.find())
			{
				saveSlayerMaster(npcName.getText());
				npcOverlayService.rebuild();
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(SlayerAdditionsConfig.GROUP_NAME))
		{
			return;
		}

		npcOverlayService.rebuild();
	}

	boolean isTarget(NPC npc)
	{
		if (targetNames.isEmpty())
		{
			return false;
		}

		final NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			return false;
		}

		final String name = composition.getName()
			.replace('\u00A0', ' ')
			.toLowerCase();

		for (Pattern target : targetNames)
		{
			final Matcher targetMatcher = target.matcher(name);
			if (targetMatcher.find() && (ArrayUtils.contains(composition.getActions(), "Attack")
					// Pick action is for zygomite-fungi
					|| ArrayUtils.contains(composition.getActions(), "Pick")))
			{
				return true;
			}
		}
		return false;
	}

	private void rebuildTargetNames(Task task)
	{
		targetNames.clear();

		if (task != null)
		{
			Arrays.stream(task.getTargetNames())
				.map(SlayerAdditionsPlugin::targetNamePattern)
				.forEach(targetNames::add);

			targetNames.add(targetNamePattern(taskName.replaceAll("s$", "")));
		}
	}

	private static Pattern targetNamePattern(final String targetName)
	{
		return Pattern.compile("(?:\\s|^)" + targetName + "(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	}

	private void rebuildTargetList()
	{
		targets.clear();

		for (NPC npc : client.getNpcs())
		{
			if (isTarget(npc))
			{
				targets.add(npc);
			}
		}
	}

	void resetTask()
	{
		removeSlayerMaster();
		setTask("", 0, null);
	}

	private void setTask(String name, int amt, String location)
	{
		taskName = name;
		amount = amt;
		taskLocation = location;

		Task task = Task.getTask(name);
		rebuildTargetNames(task);
		rebuildTargetList();
		npcOverlayService.rebuild();
	}
}
