package sky.slayer;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;

@Singleton
class SkySlayerPluginServiceImpl implements SkySlayerPluginService
{
	private final SkySlayerPlugin plugin;

	@Inject
	private SkySlayerPluginServiceImpl(final SkySlayerPlugin plugin)
	{
		this.plugin = plugin;
	}

	@Override
	public List<NPC> getTargets()
	{
		return plugin.getTargets();
	}

	@Override
	public String getTask()
	{
		return plugin.getTaskName();
	}

	@Override
	public String getTaskLocation()
	{
		return plugin.getTaskLocation();
	}

	@Override
	public int getInitialAmount()
	{
		return plugin.getInitialAmount();
	}

	@Override
	public int getRemainingAmount()
	{
		return plugin.getAmount();
	}
}