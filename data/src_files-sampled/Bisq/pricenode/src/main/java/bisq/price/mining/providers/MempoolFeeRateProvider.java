package bisq.price.mining.providers;

import bisq.price.PriceController;
import bisq.price.mining.FeeRate;
import bisq.price.mining.FeeRateProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

abstract class MempoolFeeRateProvider extends FeeRateProvider {

    private static final int DEFAULT_MAX_BLOCKS = 2;
    private static final int DEFAULT_REFRESH_INTERVAL = 2;

    private static final String MEMPOOL_HOSTNAME_KEY_1 = "bisq.price.mining.providers.mempoolHostname.1";
    private static final String MEMPOOL_HOSTNAME_KEY_2 = "bisq.price.mining.providers.mempoolHostname.2";
    private static final String MEMPOOL_HOSTNAME_KEY_3 = "bisq.price.mining.providers.mempoolHostname.3";
    private static final String MEMPOOL_HOSTNAME_KEY_4 = "bisq.price.mining.providers.mempoolHostname.4";
    private static final String MEMPOOL_HOSTNAME_KEY_5 = "bisq.price.mining.providers.mempoolHostname.5";

    private static final RestTemplate restTemplate = new RestTemplate();

    private final int maxBlocks;

    protected Environment env;

    public MempoolFeeRateProvider(Environment env) {
        super(Duration.ofMinutes(refreshInterval(env)));
        this.env = env;
        this.maxBlocks = maxBlocks(env);
    }

    protected FeeRate doGet() {
        try {
            return getEstimatedFeeRate();
        }
        catch (Exception e) {
            log.error("Error retrieving bitcoin mining fee estimation: " + e.getMessage());
        }

        return new FeeRate("BTC", MIN_FEE_RATE_FOR_TRADING, MIN_FEE_RATE_FOR_WITHDRAWAL, Instant.now().getEpochSecond());
    }

    private FeeRate getEstimatedFeeRate() {
        Set<Map.Entry<String, Long>> feeRatePredictions = getFeeRatePredictions();
        long estimatedFeeRate = feeRatePredictions.stream()
                .filter(p -> p.getKey().equalsIgnoreCase("halfHourFee"))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(r -> Math.max(r, MIN_FEE_RATE_FOR_TRADING))
                .map(r -> Math.min(r, MAX_FEE_RATE))
                .orElse(MIN_FEE_RATE_FOR_TRADING);
        long minimumFee = feeRatePredictions.stream()
                .filter(p -> p.getKey().equalsIgnoreCase("minimumFee"))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(r -> Math.multiplyExact(r, 2)) .orElse(MIN_FEE_RATE_FOR_WITHDRAWAL);
        log.info("Retrieved estimated mining fee of {} sat/vB and minimumFee of {} sat/vB from {}", estimatedFeeRate, minimumFee, getMempoolApiHostname());
        return new FeeRate("BTC", estimatedFeeRate, minimumFee, Instant.now().getEpochSecond());
    }

    private Set<Map.Entry<String, Long>> getFeeRatePredictions() {
        return restTemplate.exchange(
            RequestEntity
                .get(UriComponentsBuilder
                    .fromUriString("https://" + getMempoolApiHostname() + "/api/v1/fees/recommended")
                    .build().toUri())
                .build(),
            new ParameterizedTypeReference<Map<String, Long>>() { }
        ).getBody().entrySet();
    }

    protected abstract String getMempoolApiHostname();

    private static Optional<String[]> args(Environment env) {
        return Optional.ofNullable(
            env.getProperty(CommandLinePropertySource.DEFAULT_NON_OPTION_ARGS_PROPERTY_NAME, String[].class));
    }

    private static int maxBlocks(Environment env) {
        return args(env)
            .filter(args -> args.length >= 1)
            .map(args -> Integer.valueOf(args[0]))
            .orElse(DEFAULT_MAX_BLOCKS);
    }

    private static long refreshInterval(Environment env) {
        return args(env)
            .filter(args -> args.length >= 2)
            .map(args -> Integer.valueOf(args[1]))
            .orElse(DEFAULT_REFRESH_INTERVAL);
    }

    @Primary
    @Component
    @Order(1)
    public static class First extends MempoolFeeRateProvider {

        public First(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_1, "mempool.space");
        }
    }

    @Component
    @Order(2)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_2)
    public static class Second extends MempoolFeeRateProvider {

        public Second(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_2);
        }
    }

    @Component
    @Order(3)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_3)
    public static class Third extends MempoolFeeRateProvider {

        public Third(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_3);
        }
    }

    @Component
    @Order(4)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_4)
    public static class Fourth extends MempoolFeeRateProvider {

        public Fourth(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_4);
        }
    }

    @Component
    @Order(5)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_5)
    public static class Fifth extends MempoolFeeRateProvider {

        public Fifth(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_5);
        }
    }

    @RestController
    class Controller extends PriceController {

        @GetMapping(path = "/getParams")
        public String getParams() {
            return String.format("%s;%s", maxBlocks, refreshInterval.toMillis());
        }
    }
}
