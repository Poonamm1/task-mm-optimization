package com.dummy.move.nim.uwms.mm.configurations;

import com.dummy.move.nim.uwms.mm.moves.services.OptimizerSelectionProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropsConfig {
  @Bean
  OptimizerSelectionProps optimizerSelectionProps() {
    return new OptimizerSelectionProps();
  }
}
