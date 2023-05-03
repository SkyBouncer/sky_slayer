package sky.slayer;

import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.NPC;

public interface SkySlayerPluginService
{
	/**
	 * Get targets for current slayer task
	 *
	 * @return pattern list of target npc
	 */
	List<NPC> getTargets();

	@Nullable
	String getTask();

	@Nullable
	String getTaskLocation();

	int getInitialAmount();

	int getRemainingAmount();
}