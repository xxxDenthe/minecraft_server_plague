package dev.denthe.plaguecore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(PlagueCore.MODID)
public class PlagueCore {
    public static final String MODID = "plaguecore";
    public static final Logger LOG = LogUtils.getLogger();

    public PlagueCore(IEventBus modEventBus, ModContainer container) {
        LOG.info("Plague Core загружается");
    }
}
