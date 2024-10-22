package com.palantir.atlasdb.sweep;

import com.palantir.atlasdb.keyvalue.api.Cell;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface BatchOfCellsToSweep {
    List<CellToSweep> cells();

    long numCellTsPairsExamined();

    Cell lastCellExamined();
}
