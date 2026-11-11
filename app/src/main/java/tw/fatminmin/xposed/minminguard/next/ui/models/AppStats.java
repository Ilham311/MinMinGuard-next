package tw.fatminmin.xposed.minminguard.next.ui.models;

import java.util.Collections;
import java.util.List;

/**
 * Statistik satu aplikasi, dibaca dari {@code content://.../stats}.
 */
public class AppStats
{
    private final String packageName;
    private final int blockNum;
    private final long lastSeen;
    private final List<String> networks;

    public AppStats(String packageName, int blockNum, long lastSeen, List<String> networks)
    {
        this.packageName = packageName;
        this.blockNum = blockNum;
        this.lastSeen = lastSeen;
        this.networks = networks == null ? Collections.<String>emptyList() : networks;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public int getBlockNum()
    {
        return blockNum;
    }

    public long getLastSeen()
    {
        return lastSeen;
    }

    public String getNetworksLabel()
    {
        if (networks.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < networks.size(); i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(networks.get(i));
        }
        return sb.toString();
    }

}
