package org.elasticsearch.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.common.transport.TransportAddress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileBasedSeedHostsProvider implements SeedHostsProvider {

    private static final Logger LOGGER = LogManager.getLogger(FileBasedSeedHostsProvider.class);

    public static final String UNICAST_HOSTS_FILE = "unicast_hosts.txt";

    private final Path unicastHostsFilePath;

    public FileBasedSeedHostsProvider(Path configFile) {
        this.unicastHostsFilePath = configFile.resolve(UNICAST_HOSTS_FILE);
    }

    private List<String> getHostsList() {
        if (Files.exists(unicastHostsFilePath)) {
            try (Stream<String> lines = Files.lines(unicastHostsFilePath)) {
                return lines.filter(line -> line.startsWith("#") == false) .collect(Collectors.toList());
            } catch (IOException e) {
                LOGGER.warn(() -> new ParameterizedMessage("failed to read file [{}]", unicastHostsFilePath), e);
                return Collections.emptyList();
            }
        }

        LOGGER.warn("expected, but did not find, a dynamic hosts list at [{}]", unicastHostsFilePath);

        return Collections.emptyList();
    }

    @Override
    public List<TransportAddress> getSeedAddresses(HostsResolver hostsResolver) {
        final List<TransportAddress> transportAddresses = hostsResolver.resolveHosts(getHostsList());
        LOGGER.debug("seed addresses: {}", transportAddresses);
        return transportAddresses;
    }
}
