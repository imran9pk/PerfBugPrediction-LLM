package com.github.benmanes.caffeine.cache.simulator.policy.sketch.climbing.gradient;

import static java.util.Locale.US;

import java.util.List;

import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.policy.sketch.climbing.AbstractClimber;
import com.google.common.primitives.Ints;
import com.typesafe.config.Config;

public final class Stochastic extends AbstractClimber {
  private final Acceleration acceleration;
  private final int stepSize;
  private final double beta;

  private double velocity;

  public Stochastic(Config config) {
    StochasticSettings settings = new StochasticSettings(config);
    int maximumSize = Ints.checkedCast(settings.maximumSize());
    sampleSize = (int) (settings.percentSample() * maximumSize);
    stepSize = (int) (settings.percentPivot() * maximumSize);
    acceleration = settings.acceleration();
    beta = settings.beta();
  }

  @Override
  protected double adjust(double hitRate) {
    double currentMissRate = (1 - hitRate);
    double previousMissRate = (1 - previousHitRate);
    double gradient = currentMissRate - previousMissRate;

    switch (acceleration) {
      case NONE:
        return stepSize * gradient;
      case MOMENTUM:
        velocity = (beta * velocity) + (1 - beta) * gradient;
        return stepSize * velocity;
      case NESTEROV:
        double previousVelocity = velocity;
        velocity = (beta * velocity) + stepSize * gradient;
        return -(beta * previousVelocity) + ((1 + beta) * velocity);
    }
    throw new IllegalStateException("Unknown acceleration type: " + acceleration);
  }

  enum Acceleration { NONE, MOMENTUM, NESTEROV }

  static final class StochasticSettings extends BasicSettings {
    static final String BASE_PATH = "hill-climber-window-tiny-lfu.stochastic-gradient-descent.";

    public StochasticSettings(Config config) {
      super(config);
    }
    public List<Double> percentMain() {
      return config().getDoubleList("hill-climber-window-tiny-lfu.percent-main");
    }
    public double percentPivot() {
      return config().getDouble(BASE_PATH + "percent-pivot");
    }
    public double percentSample() {
      return config().getDouble(BASE_PATH + "percent-sample");
    }
    public Acceleration acceleration() {
      return Acceleration.valueOf(config().getString(BASE_PATH + "acceleration").toUpperCase(US));
    }
    public double beta() {
      return config().getDouble(BASE_PATH + "beta");
    }
  }
}
