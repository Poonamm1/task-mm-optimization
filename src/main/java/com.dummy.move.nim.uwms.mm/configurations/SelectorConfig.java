package com.dummy.move.nim.uwms.mm.configurations;

import com.dummy.move.nim.uwms.mm.moves.interfaces.IMoveSelector;
import com.dummy.move.nim.uwms.mm.moves.services.GreedyMoveSelectorI;
import com.dummy.move.nim.uwms.mm.moves.services.OptimizerSelectionProps;
import com.dummy.move.nim.uwms.mm.moves.services.OrtoolsMoveSelectorI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SelectorConfig {
  @Bean
  public IMoveSelector moveSelector(OptimizerSelectionProps props) {
    if (props.isUseOrtools()) {
      try {
        return new OrtoolsMoveSelectorI(
            props.getCongestedAisleThreshold(), props.getCongestedAisleMax());
      } catch (Throwable t) {
        // native lib missing or failed: fallback to greedy
        return new GreedyMoveSelectorI(
            props.getCongestedAisleThreshold(), props.getCongestedAisleMax());
      }
    } else {
      return new GreedyMoveSelectorI(
          props.getCongestedAisleThreshold(), props.getCongestedAisleMax());
    }
  }
}
