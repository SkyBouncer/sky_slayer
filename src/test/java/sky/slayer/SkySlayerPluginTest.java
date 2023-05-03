package sky.slayer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SkySlayerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerAdditionsPlugin.class);
		RuneLite.main(args);
	}
}