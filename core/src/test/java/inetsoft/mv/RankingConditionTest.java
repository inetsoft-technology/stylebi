/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv;

import inetsoft.mv.data.MVQuery;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.MVInfo;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test cases that test parameterized ranking conditions in materialized views.
 */
// ignite error: CacheInvalidStateException: Failed to execute the cache operation (all partition owners have left the grid, partition data has been lost)
@Disabled("Ignite causing problems")
@SreeHome(importResources = "RankingConditionTest.zip", materialize = RankingConditionTest.ASSET_ID)
public class RankingConditionTest {
   @Test
   public void testOneGroupTopN() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      VSTableLens table = sandbox.getVSTableLens("OneGroup_TopN", false);
      table.moreRows(XTable.EOT);
      List<MVInfo> mvInfos = MVQuery.getMVInfos(table);
      assertNotNull(mvInfos);
      assertFalse(mvInfos.isEmpty());

      Object[][] expected = new Object[][] {
         { "Category", "Total" },
         { "Business", 8093500D },
         { "Hardware", 4972310D }
      };

      XTableUtil.assertEquals(table, expected);
   }

   @Test
   public void testTwoGroupsOuterTopN() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      VSTableLens table = sandbox.getVSTableLens("TwoGroups_OuterTopN", false);
      table.moreRows(XTable.EOT);
      List<MVInfo> mvInfos = MVQuery.getMVInfos(table);
      assertNotNull(mvInfos);
      assertFalse(mvInfos.isEmpty());

      Object[][] expected = new Object[][] {
         { "Category", "Name", "Total" },
         { "Business", "InsideView", 184750D },
         { "Business", "MeToo AppServer", 4446000D },
         { "Business", "Xconnect Server", 3462750D },
         { "Hardware", "17 Inch LCD", 378420D },
         { "Hardware", "19 inch LCD", 504060D },
         { "Hardware", "NetStorage", 3941550D },
         { "Hardware", "Wireless Keyboard", 71960D },
         { "Hardware", "Wireless Mouse", 76320D }
      };

      XTableUtil.assertEquals(table, expected);
   }

   @Test
   public void testTwoGroupsInnerTopN() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      VSTableLens table = sandbox.getVSTableLens("TwoGroups_InnerTopN", false);
      table.moreRows(XTable.EOT);
      List<MVInfo> mvInfos = MVQuery.getMVInfos(table);
      assertNotNull(mvInfos);
      assertFalse(mvInfos.isEmpty());

      Object[][] expected = new Object[][] {
         { "Category", "Name", "Total" },
         { "Business", "MeToo AppServer", 4446000D },
         { "Business", "Xconnect Server", 3462750D },
         { "Educational", "Animal World", 105020D },
         { "Educational", "Math for Me", 415480D },
         { "Games", "Fast Go Game", 309600D },
         { "Games", "Web Bridge", 182175D },
         { "Graphics", "Fancy Menus", 98245D },
         { "Graphics", "Mega Icons", 813450D },
         { "Hardware", "19 inch LCD", 504060D },
         { "Hardware", "NetStorage", 3941550D },
         { "Office Tools", "Info Folder", 628425D },
         { "Office Tools", "WebCalendar", 430280D },
         { "Personal", "Sync Me", 245485D },
         { "Personal", "True Action", 582672D }
      };

//      Util.printTable(table);
      XTableUtil.assertEquals(table, expected);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);

      Map<String, String[]> parameters = new HashMap<>();
      parameters.put("topn", new String[] { "2" });
      event.setParameters(parameters);

      return event;
   }

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);

   public static final String ASSET_ID = "1^128^__NULL__^TEST_RankingCondition";
}
