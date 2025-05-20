/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

@SreeHome(importResources = { "EmbeddedVS1.zip", "ExpRefVS.zip" })
public class DistributedTableCacheKeyTest {
   @Test
   void testEmbeddedTable() throws Exception {
      ObjectMapper objectMapper = RuntimeSheetCache.createObjectMapper();
      RuntimeViewsheet rvs1 = vs1Resource.getRuntimeViewsheet();
      RuntimeViewsheet rvs2 = new RuntimeViewsheet(rvs1.saveState(objectMapper), objectMapper);

      // shouldn't be the same reference but check anyway in case the above logic changes
      Assertions.assertNotSame(rvs1, rvs2);

      String rvs1TableKey = getCacheKey(rvs1, "TableView1", true);
      String rvs2TableKey = getCacheKey(rvs2, "TableView1", true);

      // table key of the restored runtime viewsheet should be equal
      Assertions.assertEquals(rvs1TableKey, rvs2TableKey);

      // table key should not be marked for local cache only
      Assertions.assertFalse(rvs1TableKey.contains(DataKey.LOCAL_CACHE_ONLY));

      rvs1TableKey = getCacheKey(rvs1, "TableView1", false);
      rvs2TableKey = getCacheKey(rvs2, "TableView1", false);

      // data cache key of the restored runtime viewsheet should be equal
      Assertions.assertEquals(rvs1TableKey, rvs2TableKey);
   }

   @Test
   void testExpressionRefTable() throws Exception {
      RuntimeViewsheet rvs = vs2Resource.getRuntimeViewsheet();
      String tableCacheKey = getCacheKey(rvs, "TableView1", true);

      // table key should be marked for local cache only
      Assertions.assertTrue(tableCacheKey.contains(DataKey.LOCAL_CACHE_ONLY));

      DataKey dataKey = getDataKey(rvs, "TableView1");

      // check if data key localCacheOnly is true
      Assertions.assertTrue(dataKey.isLocalCacheOnly());
   }

   private String getCacheKey(RuntimeViewsheet rvs, String vsAssemblyName, boolean onlyTable) throws Exception {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly vsTable = (TableVSAssembly) vs.getAssembly(vsAssemblyName);
      Worksheet ws = box.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(vsTable.getTableName());

      if(onlyTable) {
         StringWriter sw = new StringWriter();
         PrintWriter pw = new PrintWriter(sw);
         table.printKey(pw);
         return sw.toString();
      }
      else {
         DataKey key = AssetDataCache.getCacheKey(table, box.getAssetQuerySandbox(), null,
                                                  AssetQuerySandbox.RUNTIME_MODE, false);
         return key.toString();
      }
   }

   private DataKey getDataKey(RuntimeViewsheet rvs, String vsAssemblyName) throws Exception {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly vsTable = (TableVSAssembly) vs.getAssembly(vsAssemblyName);
      Worksheet ws = box.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(vsTable.getTableName());
      return AssetDataCache.getCacheKey(table, box.getAssetQuerySandbox(), null,
                                        AssetQuerySandbox.RUNTIME_MODE, false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent(String assetId) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(assetId);
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension vs1Resource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(VS1_ASSET_ID), controllers);

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension vs2Resource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(VS2_ASSET_ID), controllers);

   private static final String VS1_ASSET_ID = "1^128^__NULL__^EmbeddedVS1^host-org";
   private static final String VS2_ASSET_ID = "1^128^__NULL__^ExpRefVS^host-org";
}
