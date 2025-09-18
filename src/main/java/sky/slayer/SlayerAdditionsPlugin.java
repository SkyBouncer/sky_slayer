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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.DBTableID;

@PluginDescriptor(
	name = "Slayer Additions",
	description = "Slayer additions",
	tags = {"slayer", "highlight", "overlay", "minimap", "tasks"}
)
@Slf4j
public class SlayerAdditionsPlugin extends Plugin
{
	private static final String TURAEL = "Turael";
	private static final String AYA = "Aya";
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

	private final List<NPC> targets = new ArrayList<>();
	private int amount;
	private String taskLocation;
	private String taskName;
	private String slayerMaster;
	private boolean loginFlag;
	private final List<Pattern> targetNames = new ArrayList<>();

	private static final int DIALOG_NPC_GROUP = 231;
	private static final int CHILD_NPC_NAME    = 4;
	private static final int CHILD_NPC_TEXT    = 6;

	public final Function<NPC, HighlightedNpc> slayerAdditionsHighlighter = (n) ->
	{
		boolean shouldHighlight = config.highlightTurael() && (TURAEL.equals(slayerMaster) || AYA.equals(slayerMaster) || SPRIA.equals(slayerMaster));
		if ((shouldHighlight || config.highlightMinimap()) && targets.contains(n))
		{
			Color color = config.getTargetColor();
			HighlightMode mode = config.getHighlightMode();
			return HighlightedNpc.builder()
					.npc(n)
					.highlightColor(color)
					.fillColor(ColorUtil.colorWithAlpha(color, color.getAlpha() / 12))
					.outline(shouldHighlight && mode == HighlightMode.Outline)
					.hull(shouldHighlight && mode == HighlightMode.Hull)
					.tile(shouldHighlight && mode == HighlightMode.Tile)
					.trueTile(shouldHighlight && mode == HighlightMode.Truetile)
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
		configManager.setConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY, master);
	}

	private void removeSlayerMaster()
	{
		slayerMaster = "";
		configManager.unsetConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY);
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
		int varbitId = varbitChanged.getVarbitId();
		if (varpId == VarPlayerID.SLAYER_COUNT
				|| varpId == VarPlayerID.SLAYER_AREA
				|| varpId == VarPlayerID.SLAYER_TARGET
				|| varbitId == VarbitID.SLAYER_TARGET_BOSSID
				|| varpId == VarPlayerID.SLAYER_COUNT_ORIGINAL)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	private void updateTask()
	{
		int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (amount > 0)
		{
			String storedSlayerMaster = configManager.getConfiguration(SlayerAdditionsConfig.GROUP_NAME, SlayerAdditionsConfig.SLAYER_MASTER_NAME_KEY);
			slayerMaster = storedSlayerMaster == null ? "" : storedSlayerMaster;
			
			int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);

			int taskDBRow;
			if (taskId == 98)
			{
				List<Integer> bossRows = client.getDBRowsByValue(
						DBTableID.SlayerTaskSublist.ID,
						DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
						0,
						client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));

				if (bossRows.isEmpty())
				{
					return;
				}
				taskDBRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
			}
			else
			{
				List<Integer> taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
				if (taskRows.isEmpty())
				{
					return;
				}
				taskDBRow = taskRows.get(0);
			}

			String taskName = (String) client.getDBTableField(taskDBRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];

			int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
			String taskLocation = null;
			if (areaId > 0)
			{
				List<Integer> areaRows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
				if (areaRows.isEmpty())
				{
					return;
				}

				taskLocation = (String) client.getDBTableField(areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
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
		Widget npcName = client.getWidget(DIALOG_NPC_GROUP, CHILD_NPC_NAME);
		Widget npcDialog = client.getWidget(DIALOG_NPC_GROUP, CHILD_NPC_TEXT);

		if (npcDialog != null && npcName != null && (npcName.getText().equals(TURAEL) || npcName.getText().equals(AYA) || npcName.getText().equals(SPRIA)))
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
