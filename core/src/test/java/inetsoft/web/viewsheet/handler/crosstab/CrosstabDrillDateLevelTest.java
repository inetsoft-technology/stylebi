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

package inetsoft.web.viewsheet.handler.crosstab;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for "sorting a MonthOfYear/DayOfMonth cell after Expand Hierarchy does
 * nothing".
 *
 * Expand Hierarchy creates each new date level by cloning the dimension of the previous
 * level and lowering its date level. The clone also inherits the previous level in the
 * transient VSDimensionRef#oldRuntimeDateLevel field, so the first
 * VSDimensionRef#update() after the drill saw the level as "changed" and latched
 * runtimeDateLevelChange() = true (the flag is only cleared by setDateLevel()).
 *
 * CrossBaseVSAssemblyInfo#removeUselessChildRefs() then stripped those levels out of the
 * CrosstabTree on every execution, and the rebuild in CrosstabTree#updateHierarchy() ran
 * the "collapsed ref" branch of updateChildRef(), which copies the previous level's
 * options -- including the sort order -- over the dimension. The order set by
 * BaseTableSortColumnService therefore never survived to the query. Both flags are
 * transient, which is why the sort worked again after the viewsheet was saved and
 * reopened.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabDrillDateLevelTest {
   /**
    * Drilling must not be mistaken for a dynamic date level change on any of the levels
    * it creates.
    */
   @Test
   void expandHierarchyDoesNotFlagRuntimeDateLevelChange() {
      List<DataRef> refs = expandHierarchy().refs();

      // Year, QuarterOfYear, MonthOfYear, DayOfMonth
      assertTrue(refs.size() > 2, "expected the date hierarchy to expand, got " + names(refs));

      for(DataRef ref : refs) {
         VSDimensionRef dim = (VSDimensionRef) ref;
         // run the runtime update twice: update0() sets the flag on the design ref, and
         // the runtime clone only picks it up on the following pass.
         dim.update(null, columns());
         dim.update(null, columns());

         assertFalse(dim.runtimeDateLevelChange(),
                     dim.getFullName() + " must not report a runtime date level change");
      }
   }

   /**
    * With the level no longer flagged, the hierarchy stays in the CrosstabTree across
    * executions, so re-registering it does not copy the parent level's sort order over a
    * drilled level.
    */
   @Test
   void drilledLevelKeepsItsOwnSortOrder() {
      Hierarchy hierarchy = expandHierarchy();
      List<DataRef> refs = hierarchy.refs();
      CrosstabTree tree = hierarchy.tree();

      for(DataRef ref : refs) {
         ((VSDimensionRef) ref).update(null, columns());
         ((VSDimensionRef) ref).update(null, columns());
      }

      // mirror CrossBaseVSAssemblyInfo.removeUselessChildRefs(): a level that reports a
      // runtime date level change is dropped from the hierarchy on every execution
      for(DataRef ref : refs) {
         VSDimensionRef dim = (VSDimensionRef) ref;

         if(dim.runtimeDateLevelChange()) {
            tree.removeChildRef(dim.getFullName());
         }
      }

      // give the levels alternating sort orders, then let the parent -> child links be
      // re-registered the way CrosstabTree.updateHierarchy() does on every execution
      for(int i = 0; i < refs.size(); i++) {
         ((VSDimensionRef) refs.get(i))
            .setOrder(i % 2 == 0 ? XConstants.SORT_ASC : XConstants.SORT_DESC);
      }

      for(int i = 0; i < refs.size() - 1; i++) {
         VSDimensionRef parent = (VSDimensionRef) refs.get(i);
         VSDimensionRef child = (VSDimensionRef) refs.get(i + 1);
         int order = child.getOrder();

         tree.updateChildRef(parent.getFullName(), child);

         assertEquals(order, child.getOrder(),
                      child.getFullName() + " sort order must not be overwritten by " +
                         parent.getFullName());
      }
   }

   private Hierarchy expandHierarchy() {
      ColumnSelection columns = columns();
      VSDimensionRef year = new VSDimensionRef();
      year.setGroupColumnValue(DATE_COLUMN);
      year.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");

      // the runtime refs are regenerated from the design ref on every execution; it takes
      // two passes before the clone carries the design ref's tracked runtime date level,
      // which is what a drill then inherits.
      year.update(null, columns);
      List<DataRef> updated = year.update(null, columns);
      assertEquals(1, updated.size());
      VSDimensionRef rtYear = (VSDimensionRef) updated.get(0);

      List<DataRef> refs = new ArrayList<>();
      refs.add(rtYear);
      Set<String> childFields = new HashSet<>();
      childFields.add(rtYear.getFullName());

      CrosstabTree tree = new CrosstabTree();
      new CrosstabDrillHandler(null, null, null)
         .drillDownChild(tree, null, null, refs, childFields, rtYear, true, false,
                         (cube, dim) -> true, false);

      return new Hierarchy(refs, tree);
   }

   private ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      ColumnRef col = new ColumnRef(new AttributeRef(null, DATE_COLUMN));
      col.setDataType(XSchema.DATE);
      columns.addAttribute(col);

      return columns;
   }

   private static String names(List<DataRef> refs) {
      StringBuilder sb = new StringBuilder();

      for(DataRef ref : refs) {
         sb.append(sb.length() == 0 ? "" : ", ").append(((VSDimensionRef) ref).getFullName());
      }

      return sb.toString();
   }

   private record Hierarchy(List<DataRef> refs, CrosstabTree tree) {}

   private static final String DATE_COLUMN = "Date";
}
