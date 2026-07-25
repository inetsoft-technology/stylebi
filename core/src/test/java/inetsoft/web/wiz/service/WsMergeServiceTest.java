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
package inetsoft.web.wiz.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for a live bug: when two charts merged into the same dashboard both bind
 * to the same physical table (matched by {@code SourceInfo}, e.g. two charts both reading
 * {@code product_template}) but one chart's own copy selects more columns than the other's,
 * {@link WsMergeService#mergeColumns} only added the extra columns to the merged table's PUBLIC
 * column selection -- never its PRIVATE (actually-selected/fetched) selection. The very next
 * {@code resetColumnSelection()} call during query construction regenerates the public selection
 * FROM the private one, silently discarding the merge and reverting the shared table back to only
 * the narrower chart's columns. Confirmed live: a dashboard merging a "category" chart (whose own
 * copy of the product_template table selects just 2 columns) with a "product" chart (whose own
 * copy additionally selects a JSON name column used by a downstream JS-expression calc) ended up
 * with the JS expression's dependency silently missing, evaluating to null for every row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsMergeServiceTest {
   private final WsMergeService service = new WsMergeService();

   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName,
                                                            String... columns)
   {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.product_template");
      table.setSourceInfo(si);
      ColumnSelection cs = new ColumnSelection();

      for(String name : columns) {
         AttributeRef ref = new AttributeRef(null, name);
         ref.setDataType(XSchema.STRING);
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(XSchema.STRING);
         cs.addAttribute(col);
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   @Test
   void mergingATableWithExtraColumnsAddsThemToBothPublicAndPrivateSelection() {
      // dashWS already has a narrower chart's own copy of the shared physical table merged in.
      Worksheet dashWS = new Worksheet();
      dashWS.addAssembly(physicalTable(dashWS, "PT", "pt_id", "categ_id"));

      // A second chart's own worksheet binds to the SAME physical source, but selects one more
      // column (the JSON name column a downstream calc depends on).
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTable(vizWS, "PT", "pt_id", "categ_id", "product_name_json"));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      // ensureBaseHasPrevMirror renames the pre-existing "PT" to "PT_base" once a prevMirror is
      // created over it -- that renamed table is where mergeColumns adds the extra column.
      TableAssembly merged = (TableAssembly) dashWS.getAssembly("PT_base");
      assertNotNull(merged, "expected the existing 'PT' to be promoted to 'PT_base'");

      ColumnSelection privateCols = merged.getColumnSelection(false);
      assertNotNull(privateCols.getAttribute("product_name_json"),
         "merged column must be added to the PRIVATE selection, not just public -- otherwise " +
         "the next resetColumnSelection() silently drops it again");

      ColumnSelection publicCols = merged.getColumnSelection(true);
      assertNotNull(publicCols.getAttribute("product_name_json"));

      // "PT" (the prevMirror created over the renamed base) is the table downstream joins
      // actually reference -- it needs the SAME fix independently: ensureBaseHasPrevMirror's own
      // prevMirror-refresh step only updated the public selection until this fix, so "PT" is
      // where the bug reproduced live even after mergeColumns alone was fixed.
      TableAssembly prevMirror = (TableAssembly) dashWS.getAssembly("PT");
      assertNotNull(prevMirror, "expected a prevMirror named 'PT' to be created");

      // A mirror's PRIVATE selection references its base's columns by OUTER ATTRIBUTE --
      // qualified by the base's name ("PT_base.product_name_json"), matching the qualified shape
      // of its sibling columns ("PT_base.pt_id") -- not the base's bare column name.
      ColumnSelection mirrorPrivateCols = prevMirror.getColumnSelection(false);
      assertNotNull(mirrorPrivateCols.getAttribute("PT_base.product_name_json"),
         "the prevMirror's PRIVATE selection must also get the merged column (outer-attribute " +
         "qualified, matching its siblings), not just public");

      ColumnSelection mirrorPublicCols = prevMirror.getColumnSelection(true);
      assertNotNull(mirrorPublicCols.getAttribute("product_name_json"));
   }
}
