package com.dummy.move.nim.uwms.mm.configurations;

import com.dummy.move.nim.uwms.mm.moves.models.Weights;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WeightsConfig {
  @Value("${optimizer.weights.normal.wPriority:2.0}")
  private double n_p;

  @Value("${optimizer.weights.normal.wDistance:1.25}")
  private double n_d;

  @Value("${optimizer.weights.normal.wAge:0.6}")
  private double n_a;

  @Value("${optimizer.weights.normal.wType:0.4}")
  private double n_t;

  @Value("${optimizer.weights.normal.wCongestion:0.8}")
  private double n_c;

  @Value("${optimizer.weights.normal.sameAreaBonus:0.1}")
  private double n_s;

  @Value("${optimizer.weights.coldStart.wPriority:2.2}")
  private double c_p;

  @Value("${optimizer.weights.coldStart.wDistance:0.0}")
  private double c_d;

  @Value("${optimizer.weights.coldStart.wAge:0.8}")
  private double c_a;

  @Value("${optimizer.weights.coldStart.wType:0.5}")
  private double c_t;

  @Value("${optimizer.weights.coldStart.wCongestion:0.8}")
  private double c_c;

  @Value("${optimizer.weights.coldStart.sameAreaBonus:0.1}")
  private double c_s;

  public Weights normal() {
    Weights w = new Weights();
    w.wPriority = n_p;
    w.wDistance = n_d;
    w.wAge = n_a;
    w.wType = n_t;
    w.wCongestion = n_c;
    w.sameWarehouseAreaBonus = n_s;
    return w;
  }

  public Weights coldStart() {
    Weights w = new Weights();
    w.wPriority = c_p;
    w.wDistance = c_d;
    w.wAge = c_a;
    w.wType = c_t;
    w.wCongestion = c_c;
    w.sameWarehouseAreaBonus = c_s;
    return w;
  }
}
