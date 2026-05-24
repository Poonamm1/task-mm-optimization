package com.dummy.move.nim.uwms.mm.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {

  @Bean
  public Clock systemClock() {
    // Use system UTC clock for consistency
    return Clock.systemUTC();
  }
}
