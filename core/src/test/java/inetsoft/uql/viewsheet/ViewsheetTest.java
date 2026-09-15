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
package inetsoft.uql.viewsheet;

import inetsoft.sree.security.ResourceAction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for {@link Viewsheet#reloadBaseWorksheet}. Before this widening, it only
 * ever populated {@link Viewsheet#getBaseWorksheet()} for a worksheet-type base entry -- a
 * logical-model or physical-table {@code wentry} silently no-op'd (the guard returned early),
 * even though {@link Viewsheet#isDirectSource()}/the private {@code getWorksheet} helper already
 * know how to resolve those two types (used, before this change, only by
 * {@link Viewsheet#repopulateWorksheet}/{@link Viewsheet#update}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetTest {

   private static AssetEntry columnEntry(String entity, String attribute, String dtype) {
      AssetEntry col = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN,
                                      entity + "/" + attribute, null);
      col.setProperty("entity", entity);
      col.setProperty("attribute", attribute);
      col.setProperty("dtype", dtype);
      return col;
   }

   @Test
   void reloadBaseWorksheetStillHandlesPlainWorksheet() throws Exception {
      AssetEntry wentry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Viewsheet vs = new Viewsheet(wentry);

      Worksheet ws = new Worksheet();
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getSheet(eq(wentry), any(), eq(true), eq(AssetContent.ALL))).thenReturn(ws);

      vs.reloadBaseWorksheet(rep, mock(Principal.class));

      assertNotNull(vs.getBaseWorksheet(), "the pre-existing worksheet-type case must keep working");
   }

   @Test
   void reloadBaseWorksheetPopulatesDirectSourceForLogicalModel() throws Exception {
      AssetEntry wentry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, "MyDataSource/MyModel", null);
      wentry.setProperty("prefix", "MyDataSource");
      wentry.setProperty("source", "MyModel");
      wentry.setProperty("type", String.valueOf(XSourceInfo.MODEL));
      Viewsheet vs = new Viewsheet(wentry);

      // isLogicModel() resolution goes two levels deep: entries of the model itself, then
      // entries of each of THOSE (Viewsheet.createTableAssembly, entry.isLogicModel() branch).
      AssetEntry tableLevelEntry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, "MyDataSource/MyModel/Orders", null);
      AssetEntry columnLevelEntry = columnEntry("Orders", "ORDER_ID", "string");

      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getEntries(eq(wentry), any(), eq(ResourceAction.READ)))
         .thenReturn(new AssetEntry[] { tableLevelEntry });
      when(rep.getEntries(eq(tableLevelEntry), any(), eq(ResourceAction.READ)))
         .thenReturn(new AssetEntry[] { columnLevelEntry });

      vs.reloadBaseWorksheet(rep, mock(Principal.class));

      Worksheet resultWs = vs.getBaseWorksheet();
      assertNotNull(resultWs, "a logical-model base entry must populate a synthesized worksheet");
      assertNotNull(resultWs.getAssembly("MyModel"),
         "the synthesized worksheet should carry one table assembly named after the model");
   }

   @Test
   void reloadBaseWorksheetPopulatesDirectSourceForPhysicalTable() throws Exception {
      AssetEntry wentry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.PHYSICAL_TABLE, "Examples/Orders", null);
      wentry.setProperty("prefix", "Examples");
      wentry.setProperty("source", "Orders");
      wentry.setProperty("type", String.valueOf(XSourceInfo.PHYSICAL_TABLE));
      Viewsheet vs = new Viewsheet(wentry);

      AssetEntry columnLevelEntry = columnEntry("Orders", "ORDER_ID", "string");

      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getEntries(eq(wentry), any(), eq(ResourceAction.READ)))
         .thenReturn(new AssetEntry[] { columnLevelEntry });

      vs.reloadBaseWorksheet(rep, mock(Principal.class));

      Worksheet resultWs = vs.getBaseWorksheet();
      assertNotNull(resultWs, "a physical-table base entry must populate a synthesized worksheet");
      assertNotNull(resultWs.getAssembly("Orders"),
         "the synthesized worksheet should carry one table assembly named after the table");
   }

   /**
    * Regression for VCA-005: a categorical-colour pin, once made with {@code shareColors:true},
    * could not be removed through any documented tool -- every write path merges the viewsheet's
    * existing pins forward and can only add/overwrite, never shrink. The fix (in
    * {@code VisualFrameAliases.categoricalColor}) lets the {@code CoreTool.NULL} ("null") sentinel
    * reach {@link Tool#getColorFromHexString}, which already returns a real Java {@code null} for
    * it. This proves the other half of that mechanism, already in place and untouched by the fix:
    * {@link Viewsheet#setDimensionColors} genuinely drops a {@code null}-valued entry when
    * rebuilding a column's pins, rather than throwing or re-adding it, while every other key in the
    * same write survives.
    */
   @Test
   void setDimensionColorsGenuinelyDropsANullSentinelEntry() {
      AssetEntry wentry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Viewsheet vs = new Viewsheet(wentry);

      vs.setDimensionColors("Category", Map.of("Furniture", Color.RED, "Office", Color.BLUE));
      assertEquals(2, vs.getDimensionColors("Category").size());

      // Map.of() rejects null values outright, but the sentinel's whole point is to carry one --
      // a mutable map is required to hold it at all.
      Map<String, Color> update = new java.util.HashMap<>();
      update.put("Furniture", Tool.getColorFromHexString(CoreTool.NULL));
      update.put("Office", Color.BLUE);
      vs.setDimensionColors("Category", update);

      Map<String, Color> after = vs.getDimensionColors("Category");
      assertEquals(1, after.size(),
                   "the null-sentinel-mapped key must be dropped, not re-added");
      assertFalse(after.containsKey("Furniture"));
      assertTrue(after.containsKey("Office"));
   }
}
