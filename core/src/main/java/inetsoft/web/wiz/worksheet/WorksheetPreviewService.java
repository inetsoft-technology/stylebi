/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.worksheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Returns a sample of data rows from a live {@link RuntimeWorksheet} table without
 * modifying any state.
 *
 * <p>Data is fetched via {@link AssetQuerySandbox#getTableLens} in
 * {@link AssetQuerySandbox#LIVE_MODE}, which executes the underlying query against
 * the live data source.  Callers should keep {@code limit} small (≤ 200) to avoid
 * long-running queries in an agent context.</p>
 */
@Service
public class WorksheetPreviewService {

   /**
    * Query up to {@code limit} rows from the named table in the live worksheet.
    *
    * @param rws       the live runtime worksheet
    * @param tableName the table assembly name to query
    * @param limit     maximum number of data rows to return (header row excluded)
    * @return list of row maps keyed by column name; never {@code null}
    * @throws PairingException if the sandbox is absent, the table is not found,
    *                          or the query execution fails
    */
   public List<Map<String, Object>> preview(RuntimeWorksheet rws,
                                             String tableName,
                                             int limit)
      throws PairingException
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(box == null) {
         throw new PairingException(PairingException.Kind.INTERNAL, "Worksheet query sandbox is not available");
      }

      TableLens lens;

      try {
         lens = box.getTableLens(tableName, AssetQuerySandbox.LIVE_MODE);
      }
      catch(Exception e) {
         throw new PairingException("Failed to execute worksheet query for '"
                                    + tableName + "': " + e.getMessage());
      }

      if(lens == null) {
         throw new PairingException("Table not found or produced no data: " + tableName);
      }

      int colCount = lens.getColCount();

      // Row 0 is the header row in StyleBI's TableLens convention.
      String[] headers = new String[colCount];

      for(int col = 0; col < colCount; col++) {
         Object h = lens.getObject(0, col);
         headers[col] = h != null ? h.toString() : "col" + col;
      }

      List<Map<String, Object>> rows = new ArrayList<>();

      for(int row = 1; rows.size() < limit && lens.moreRows(row); row++) {
         Map<String, Object> rowMap = new LinkedHashMap<>();

         for(int col = 0; col < colCount; col++) {
            rowMap.put(headers[col], lens.getObject(row, col));
         }

         rows.add(rowMap);
      }

      return rows;
   }
}
