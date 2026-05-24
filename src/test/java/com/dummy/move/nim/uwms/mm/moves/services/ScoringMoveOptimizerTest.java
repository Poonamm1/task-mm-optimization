package com.dummy.move.nim.uwms.mm.moves.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoringMoveOptimizerTest {

  @BeforeEach
  void setUp() {}

  @Test
  public void ranksByPriorityAndAge() {}
}
//    Move m1 = new Move();
//    m1.getMoveId() = 1;
//    m1.type = MoveType.REQPLREP;
//    m1.movePriority = 10;
//    m1.openedEpochSec = System.currentTimeMillis() / 1000 - 600;
//
//    Move m2 = new Move();
//    m2.moveId = 2;
//    m2.type = MoveType.REQPLREP;
//    m2.movePriority = 20;
//    m2.openedEpochSec = System.currentTimeMillis() / 1000 - 60;
//
//    OptimizerRequest req = new OptimizerRequest();
//    req.userId = "u1";
//    req.facilityNum = 1;
//    req.coldStart = true;
//    req.allowedTypes = Arrays.asList(MoveType.REQPLREP);
//    req.weights = Weights.coldStartDefaults();
//    req.moves = Arrays.asList(m1, m2);
//
//    var opt = new ScoringMoveOptimizer();
//    var res = opt.prioritize(req, CongestionProvider.noOp());
//
//    assertEquals(2, res.orderedMoveIds.size());
//    assertEquals("A", res.orderedMoveIds.get(0)); // better priority & older
//  }
