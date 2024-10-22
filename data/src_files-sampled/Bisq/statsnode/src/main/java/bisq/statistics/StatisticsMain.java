package bisq.statistics;

import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.app.misc.ModuleForAppWithP2p;

import bisq.common.UserThread;
import bisq.common.app.AppModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatisticsMain extends ExecutableForAppWithP2p {
    private static final String VERSION = "1.0.1";
    private Statistics statistics;

    public StatisticsMain() {
        super("Bisq Statsnode", "bisq-statistics", "bisq_statistics", VERSION);
    }

    public static void main(String[] args) {
        log.info("Statistics.VERSION: " + VERSION);
        new StatisticsMain().execute(args);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);

        keepRunning();
    }

    @Override
    protected void addCapabilities() {
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                statistics = new Statistics();
                UserThread.execute(this::onApplicationLaunched);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
    }


    @Override
    protected AppModule getModule() {
        return new ModuleForAppWithP2p(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        statistics.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        super.startApplication();

        statistics.startApplication();
    }
}
