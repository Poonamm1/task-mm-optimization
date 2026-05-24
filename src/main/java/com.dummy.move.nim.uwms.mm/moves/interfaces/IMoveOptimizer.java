package com.dummy.move.nim.uwms.mm.moves.interfaces;

import com.dummy.move.nim.uwms.mm.moves.models.OptimizerRequest;
import com.dummy.move.nim.uwms.mm.moves.models.OptimizerResult;

public interface IMoveOptimizer {
  OptimizerResult prioritize(OptimizerRequest req, CongestionProvider congestion);
}
