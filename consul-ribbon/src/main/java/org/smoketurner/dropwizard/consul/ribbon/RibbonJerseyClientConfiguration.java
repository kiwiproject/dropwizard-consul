package org.smoketurner.dropwizard.consul.ribbon;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;

public class RibbonJerseyClientConfiguration extends JerseyClientConfiguration {

  @NotNull
  @MinDuration(value = 1, unit = TimeUnit.SECONDS)
  private Duration refreshInterval = Duration.seconds(10);

  @JsonProperty
  public Duration getRefreshInterval() {
    return refreshInterval;
  }

  @JsonProperty
  public void setRefreshInterval(Duration interval) {
    refreshInterval = interval;
  }
}
