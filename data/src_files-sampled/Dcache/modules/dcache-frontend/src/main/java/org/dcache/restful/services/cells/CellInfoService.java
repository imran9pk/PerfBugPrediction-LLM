package org.dcache.restful.services.cells;

import org.dcache.cells.json.CellData;

public interface CellInfoService {

    String[] getAddresses();

    CellData getCellData(String address);
}
