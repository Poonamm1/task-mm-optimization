package com.dummy.move.nim.uwms.mm;

import com.dummy.move.nim.uwms.mm.moves.interfaces.CongestionProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UwmsMMOptimizerApplication {
  public static void main(String[] args) {
    SpringApplication.run(UwmsMMOptimizerApplication.class, args);
  }

  @Bean
  public CongestionProvider congestionProvider() {
    return CongestionProvider.noOp();
  }
}
