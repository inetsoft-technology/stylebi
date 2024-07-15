/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.service.database;

import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.controller.database.RuntimePartitionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(MockitoExtension.class)
public class PhysicalGraphServiceTests {

   @BeforeEach
   void setup() {
      graphService = new PhysicalGraphService();
   }

   @ParameterizedTest(name = "Check move some nodes.")
   @CsvSource({"'case1', false", "'case1', true"})
   public void  testShrinkUnfold(String caseMapKey, boolean moved) {
      Map<String, Rectangle> caseMap = CASES.get(caseMapKey);
      RuntimePartitionService.RuntimeXPartition rp = buildRuntimePartition(caseMap);
      XPartition originalPartition = (XPartition) rp.getPartition().clone();
      XPartition runtimePartition = rp.getPartition();
      graphService.shrinkColumnHeight(rp);

      if(moved) {
         rp.addMovedTable("table4");
         rp.addMovedTable("table5");
      }

      graphService.unfoldColumnHeight(rp, originalPartition);

      checkIt(originalPartition, moved, runtimePartition);
   }

   @ParameterizedTest(name = "Check move all nodes.")
   @ValueSource(strings = { "case2" })
   public void case2(String caseMapKey) {
      Map<String, Rectangle> caseMap = CASES.get(caseMapKey);
      RuntimePartitionService.RuntimeXPartition rp = buildRuntimePartition(caseMap);
      XPartition originalPartition = (XPartition) rp.getPartition().clone();
      XPartition runtimePartition = rp.getPartition();

      graphService.shrinkColumnHeight(rp);

      // mark moved
      caseMap.keySet().forEach(rp::addMovedTable);

      graphService.unfoldColumnHeight(rp, originalPartition);

      checkIt(originalPartition, true, runtimePartition);
   }

   @ParameterizedTest(name = "Check process nodes that no top node and locate at middle level")
   @ValueSource(strings = {"case3"})
   public void case3(String caseMapKey) {
      Map<String, Rectangle> caseMap = CASES.get(caseMapKey);
      RuntimePartitionService.RuntimeXPartition rp = buildRuntimePartition(caseMap);
      XPartition originalPartition = (XPartition) rp.getPartition().clone();
      XPartition runtimePartition = rp.getPartition();

      // shrink
      graphService.shrinkColumnHeight(rp);

      // check SA.CONTACTS should be above SA.REGIONS(and SA.CATEGORIES)
      String middleTable = "SA.CONTACTS";
      String bottomTable = "SA.REGIONS";
      Rectangle middle = runtimePartition.getBounds(middleTable);
      Rectangle oldBottom = runtimePartition.getBounds(bottomTable);
      Assertions.assertTrue(middle.y < oldBottom.y,
         "When " + bottomTable + " has been shrink, Although " + middleTable + " does " +
            "not have top node, it should at least stay above " + bottomTable + " after shrink.");

      // unfold
      graphService.unfoldColumnHeight(rp, originalPartition);

      middle = runtimePartition.getBounds(middleTable);
      oldBottom = runtimePartition.getBounds(bottomTable);
      Assertions.assertTrue(middle.y < oldBottom.y,
         "When " + bottomTable + " has been shrink, Although " + middleTable + " does " +
            "not have top node, it should at least stay above " + bottomTable + " after unfold.");
   }

   /*
    * +--------------+-------------+------------+
    * |              |             |  (Target)  |
    * +--------------+-------------+------------+
    * |  SA.REGIONS  |             |SA.CUSTOMERS|
    * +--------------+-------------+------------+
    * |              |             |SA.CONTACTS |
    * +--------------+-------------+------------+
    */
   @ParameterizedTest(name = "Check move a node(SA.REGIONS) to above other nodes.(Target)")
   @CsvSource({"'case4', SA.REGIONS"})
   public void testMovingTop(String caseMapKey, String moveNodeName) {
      Map<String, Rectangle> caseMap = CASES.get(caseMapKey);
      RuntimePartitionService.RuntimeXPartition rp = buildRuntimePartition(caseMap);
      XPartition runtimePartition = rp.getPartition();
      XPartition originalPartition = (XPartition) runtimePartition.clone();

      // shrink
      graphService.shrinkColumnHeight(rp);

      Rectangle box = caseMap.get(moveNodeName);
      box.y = 100;
      box.x = 300;
      rp.setBounds(moveNodeName, box);

      // check reopen position
      XPartition checkPartition = (XPartition) runtimePartition.clone();

      // unfold
      graphService.unfoldColumnHeight(rp, originalPartition);

      // reopen shrink
      graphService.shrinkColumnHeight(rp);

      checkIt(checkPartition, true, runtimePartition);
   }

   private XPartition buildPartition(Map<String, Rectangle> tables) {
      XPartition partition = new XPartition();

      tables.forEach((key, value) -> partition.addTable(key, value.getBounds()));

      return partition;
   }

   private RuntimePartitionService.RuntimeXPartition buildRuntimePartition(
      Map<String, Rectangle> tables)
   {
      return new RuntimePartitionService.RuntimeXPartition(
         buildPartition(tables), UUID.randomUUID().toString(), "test-ignore");
   }

   // for debug
   private void printTableBounds(XPartition partition) {
      Enumeration<XPartition.PartitionTable> tables = partition.getTables();
      StringBuilder sb = new StringBuilder();

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable table = tables.nextElement();
         Rectangle bounds = partition.getBounds(table);
         sb.append("Table: " + table.getName()).append(", Bounds: ")
            .append(bounds.toString()).append("\n");
      }

      System.out.println(sb.toString());
      LOGGER.info(sb.toString());
   }

   private void checkIt(XPartition originalPartition, boolean moved, final XPartition partition) {
      Enumeration<XPartition.PartitionTable> tables = originalPartition.getTables();

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable table = tables.nextElement();
         String tableName = table.getName();
         int errorLimit = moved ? DEVIATION_LIMIT : 0;
         Rectangle bounds = originalPartition.getBounds(tableName);
         Rectangle runtimeBounds = partition.getBounds(tableName);

         Assertions.assertTrue(Math.abs(runtimeBounds.y - bounds.y) <= errorLimit,
            "Shrink/Unfold columns height error is too large(y) for " + tableName);

         // x should no changed.
         Assertions.assertTrue(Math.abs(runtimeBounds.x - bounds.x) == 0,
            "Shrink/Unfold columns height error is too large(x) for " + tableName);
      }
   }

   private PhysicalGraphService graphService;

   private static final int DEVIATION_LIMIT = 5;
   private static final Map<String, Map<String, Rectangle>> CASES = new ConcurrentHashMap<>();

   static {
      Map<String, Rectangle> caseMap = new HashMap<>();

      caseMap.put("table1", new Rectangle(34, 34, 71, 34));
      caseMap.put("table2", new Rectangle(169, 70, 83, 46));
      caseMap.put("table3", new Rectangle(29, 80, 71, 102));
      caseMap.put("table4", new Rectangle(10, 360, 65, 46));
      caseMap.put("table5", new Rectangle(10, 206, 89, 130));

      CASES.put("case1", caseMap);

      caseMap = new HashMap<>();

      caseMap.put("SA.REGIONS", new Rectangle(10, 324, 65, 46));
      caseMap.put("SA.ORDER_DETAILS", new Rectangle(10, 138, 101, 60));
      caseMap.put("SA.CUSTOMERS", new Rectangle(10, 10, 77, 130));
      caseMap.put("SA.PRODUCTS", new Rectangle(10, 196, 89, 130));
      caseMap.put("SA.ORDERS", new Rectangle(137, 10, 71, 102));
      caseMap.put("SA.RETURNS", new Rectangle(10, 368, 71, 102));

      CASES.put("case2", caseMap);

      caseMap = new HashMap<>();

      caseMap.put("SA.REGIONS", new Rectangle(327, 163, 55, 46));
      caseMap.put("SA.CUSTOMERS", new Rectangle(8, 11, 78, 130));
      caseMap.put("SA.CONTACTS", new Rectangle(164, 112, 65, 74));
      caseMap.put("SA.CATEGORIES", new Rectangle(14, 179, 80, 46));
      caseMap.put("SA.ORDERS", new Rectangle(315, 10, 65, 102));
      caseMap.put("SA.RETURNS", new Rectangle(165, 231, 67, 102));

      CASES.put("case3", caseMap);

      caseMap = new HashMap<>();

      caseMap.put("SA.REGIONS", new Rectangle(10, 200, 50, 50));
      caseMap.put("SA.CUSTOMERS", new Rectangle(300, 200, 50, 50));
      caseMap.put("SA.CONTACTS", new Rectangle(300, 400, 50, 50));

      CASES.put("case4", caseMap);
   }

   private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalGraphServiceTests.class);
}
