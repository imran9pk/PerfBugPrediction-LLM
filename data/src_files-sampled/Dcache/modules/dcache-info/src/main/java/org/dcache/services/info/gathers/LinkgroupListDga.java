package org.dcache.services.info.gathers;

import diskCacheV111.services.space.message.GetLinkGroupNamesMessage;
import dmg.cells.nucleus.CellPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkgroupListDga extends SkelPeriodicActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkgroupListDga.class);

    private static final double SAFETY_FACTOR = 2.5;

    private final CellPath _spacemanager;
    private final MessageHandlerChain _mhc;

    long _metricLifetime;

    public LinkgroupListDga(CellPath spacemanager, int interval, MessageHandlerChain mhc) {
        super(interval);

        _mhc = mhc;
        _metricLifetime = Math.round(interval * SAFETY_FACTOR);
        _spacemanager = spacemanager;
    }

    @Override
    public void trigger() {
        super.trigger();
        LOGGER.trace("Sending linkgroup list request message");
        _mhc.sendMessage(_metricLifetime, _spacemanager, new GetLinkGroupNamesMessage());
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
