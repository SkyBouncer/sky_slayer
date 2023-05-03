package sky.slayer;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Binder;
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
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
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
	name = "SkySlayer",
	description = "Slayer enhancements",
	tags = {"combat", "notifications", "overlay", "tasks"}
)
@Slf4j
public class SkySlayerPlugin extends Plugin
{
	private static final String TURAEL = "Turael";
	private static final String SPRIA = "Spria";

	// NPC messages
	private static final Pattern SLAYER_ASSIGN_MESSAGE = Pattern.compile(".*(?:Your new task is to kill \\d+) (?<name>.+)(?:.)");
	private static final Pattern SLAYER_CURRENT_MESSAGE = Pattern.compile(".*(?:You're still hunting) (?<name>.+)(?:[,;] you have \\d+ to go.)");

	@Inject
	private Client client;

	@Inject
	private SkySlayerConfig config;

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

	public final Function<NPC, HighlightedNpc> isAdditionalTarget = (n) ->
	{
		if (targets.contains(n) && (config.highlightMinimap() || config.highlightTurael() && (slayerMaster.equals(TURAEL) || slayerMaster.equals(SPRIA))))
		{
			Color color = config.getTargetColor();
			return HighlightedNpc.builder()
					.npc(n)
					.highlightColor(color)
					.fillColor(ColorUtil.colorWithAlpha(color, color.getAlpha() / 12))
					.outline(config.highlightTurael() && (slayerMaster.equals(TURAEL) || slayerMaster.equals(SPRIA)))
					.render(npc -> !npc.isDead())
					.build();
		}

		return null;
	};

	@Override
	protected void startUp()
	{
		npcOverlayService.registerHighlighter(isAdditionalTarget);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginFlag = true;
			clientThread.invoke(this::updateTask);
		}

		slayerMaster = configManager.getRSProfileConfiguration(SkySlayerConfig.GROUP_NAME, SkySlayerConfig.SLAYER_MASTER_NAME_KEY);
		if (slayerMaster == null){
			slayerMaster = "";
		}
	}

	@Override
	protected void shutDown()
	{
		npcOverlayService.unregisterHighlighter(isAdditionalTarget);
		targets.clear();
	}

	@Provides
	SkySlayerConfig provideSlayerConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkySlayerConfig.class);
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

	private void setProfileConfig(String key, Object value)
	{
		if (value != null)
		{
			configManager.setRSProfileConfiguration(SkySlayerConfig.GROUP_NAME, key, value);
		}
		else
		{
			configManager.unsetRSProfileConfiguration(SkySlayerConfig.GROUP_NAME, key);
		}
	}

	private void save()
	{
		setProfileConfig(SkySlayerConfig.AMOUNT_KEY, amount);
		setProfileConfig(SkySlayerConfig.TASK_NAME_KEY, taskName);
		setProfileConfig(SkySlayerConfig.TASK_LOC_KEY, taskLocation);
		setProfileConfig(SkySlayerConfig.SLAYER_MASTER_NAME_KEY, slayerMaster);
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
			int taskId = client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE);
			String taskName;
			if (taskId == 98 /* Bosses, from [proc,helper_slayer_current_assignment] */)
			{
				taskName = client.getEnum(EnumID.SLAYER_TASK_BOSS)
					.getStringValue(client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
			}
			else
			{
				taskName = client.getEnum(EnumID.SLAYER_TASK_CREATURE)
					.getStringValue(taskId);
			}

			int areaId = client.getVarpValue(VarPlayer.SLAYER_TASK_LOCATION);
			String taskLocation = null;
			if (areaId > 0)
			{
				taskLocation = client.getEnum(EnumID.SLAYER_TASK_LOCATION)
					.getStringValue(areaId);
			}

			if (loginFlag || !Objects.equals(taskName, this.taskName) || !Objects.equals(taskLocation, this.taskLocation))
			{
				setTask(taskName, amount, taskLocation);
			}
			else if (amount != this.amount)
			{
				this.amount = amount;
				setProfileConfig(SkySlayerConfig.AMOUNT_KEY, amount);
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

		// Getting slayerMaster
		Widget npcName = client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
		Widget npcDialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
		if (npcDialog != null && npcName != null && (npcName.getText().equals(TURAEL) || npcName.getText().equals(SPRIA)))
		{
			String npcText = Text.sanitizeMultilineText(npcDialog.getText());
			final Matcher mAssign = SLAYER_ASSIGN_MESSAGE.matcher(npcText);
			final Matcher mCurrent = SLAYER_CURRENT_MESSAGE.matcher(npcText);

			if (mAssign.find() || mCurrent.find())
			{
				slayerMaster = npcName.getText();
				setProfileConfig(SkySlayerConfig.SLAYER_MASTER_NAME_KEY, slayerMaster);
				npcOverlayService.rebuild();
			}
		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(SkySlayerConfig.GROUP_NAME))
		{
			return;
		}

		npcOverlayService.rebuild();
	}

	@VisibleForTesting
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
			if (targetMatcher.find()
				&& (ArrayUtils.contains(composition.getActions(), "Attack")
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
				.map(SkySlayerPlugin::targetNamePattern)
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

	@VisibleForTesting
	void resetTask()
	{
		slayerMaster = "";
		setTask("", 0, null);
	}

	private void setTask(String name, int amt, String location)
	{
		taskName = name;
		amount = amt;
		taskLocation = location;
		save();

		Task task = Task.getTask(name);
		rebuildTargetNames(task);
		rebuildTargetList();
		npcOverlayService.rebuild();
	}
}
