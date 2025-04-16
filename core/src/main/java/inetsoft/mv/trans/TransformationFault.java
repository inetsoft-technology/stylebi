/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.mv.trans;

import inetsoft.uql.asset.Assembly;
import inetsoft.util.Tool;

/**
 * The transformation fault is a condition that prevents successful
 * transformation of a query during optimization. It is used to analyze
 * the query for manual tuning.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class TransformationFault {
   /**
    * Create a fault.
    */
   private TransformationFault(String desc, String reason) {
      this.desc = desc;
      this.reason = reason;
   }

   /**
    * Get a description of this fault.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Get the suggested action to fix this fault.
    */
   public String getReason() {
      return reason;
   }

   /**
    * Can't move up selection for outer join.
    */
   public static TransformationFault outerJoin(String tbl, String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason =
         "The parent node '" + ptbl + "' has incompatible structure: " +
         "{outer join}. Outer join is not supported!";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move up selection for merge join.
    */
   public static TransformationFault mergeJoin(String tbl, String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason =
         "The parent node '" + ptbl + "' has incompatible structure: " +
         "{merge join}. Merge join is not supported!";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move selection up to a union node.
    */
   public static TransformationFault moveUpUnion(Assembly tbl, Assembly ptbl) {
      String tbln = AbstractTransformer.normalizeBlockName(tbl.getName());
      String ptbln = AbstractTransformer.normalizeBlockName(ptbl.getName());
      String desc = "Transfer all selections from child block '" +
         tbln + "' FAILED";
      String reason = "The parent node '" + ptbln + "' is a union table. " +
         "Selections in a child table cannot be moved up to the parent when " +
         "the parent is a union table.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move selection down.
    */
   public static TransformationFault moveDownSelection(Assembly tbl,
                                                       Assembly mvtbl) {
      String tbln = AbstractTransformer.normalizeBlockName(tbl.getName());
      String mvtbln = AbstractTransformer.normalizeBlockName(mvtbl.getName());
      String desc = "Transfer all selections down to child block '" +
         tbln + "' FAILED";
      String reason = "Can't move selections down from '" + tbln +
         "' to MV table '" + mvtbln + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move aggregate down.
    */
   public static TransformationFault moveDownAggregate(Assembly tbl,
                                                       Assembly mvtbl) {
      String tbln = AbstractTransformer.normalizeBlockName(tbl.getName());
      String mvtbln = AbstractTransformer.normalizeBlockName(mvtbl.getName());
      String desc = "Transfer all aggregates down to child block '" +
         tbln + "' FAILED";
      String reason = "Can't move aggregate down from '" + tbln +
         "' to MV table '" + mvtbln + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move selection up.
    */
   public static TransformationFault selectionUp(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections up to block '" + tbl + "' FAILED";
      String reason = "It contains selections in child table(s) of '" +
         tbl + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Cube table not support mv.
    */
   public static TransformationFault cubeTable(String table, String vassembly) {
      table = AbstractTransformer.normalizeBlockName(table);
      String desc = "Create mv on table '" + table + "' for viewsheet " +
         "assembly '" + vassembly + "' FAILED.";
      String reason = "The table '" + table + "' is a cube table. A cube table" +
         " cannot support MVs.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Cannot create mv for selection is hidden in parent table.
    */
   public static TransformationFault selectionHiddenOnParent(String tbl,
                                                             String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from '" + tbl + "' to '" + ptbl +
         "' FAILED.";
      String reason = "Selection is hidden in parent table '" + ptbl + "'" +
         " which contains aggregate.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Cannot create mv for selection is hidden in parent table and
    * aggregate may be not combinable.
    */
   public static TransformationFault selectionHiddenAggregate(String tbl,
      String ptbl, boolean combinable)
   {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from '" + tbl + "' to '" + ptbl +
         "' FAILED.";
      String reason = "Selection is hidden in parent table '" + ptbl + "'" +
         " which contains aggregate";

      if(combinable) {
         reason += ".";
      }
      else {
         reason += ",and the aggregate is not combinable.";
      }

      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for selection is hidden.
    */
   public static TransformationFault selectionHidden(String tbl,
                                                     String vassembly)
   {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      vassembly = AbstractTransformer.normalizeBlockName(vassembly);
      String desc = "Create mv at block '" + tbl + "' for viewsheet assembly '"
         + vassembly + "' FAILED";
      String reason = "Cannot create MV on data block '" + tbl +
         "' because the selected column in the data block is hidden.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for selection is hidden.
    */
   public static TransformationFault containsParentHiddenSelection(String tbl,
                                                                   String ptbl,
                                                                   String col)
   {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The selection on data block '" + tbl + "' cannot be " +
         "transferred because the column '" + col + "' in the data block" +
         " is hidden in parent table '" + ptbl + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for selection is hidden.
    */
   public static TransformationFault containsHiddenSelection(String tbl,
                                                             String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The selection on data block '" + tbl + "' cannot be " +
         "transferred because the selected column in the data block is hidden.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Create move up fo child with ranking but parent with condition.
    */
   public static TransformationFault subRankingParentCondition(String tbl,
                                                               String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' up to parent block '" + ptbl + "' FAILED";
      String reason = "Child block '" + tbl + "' with a ranking variable " +
         "condition, and parent block '" + ptbl + "' with conditions.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Create move up fo child with ranking but parent with groups.
    */
   public static TransformationFault subRankingParentGrouped(String tbl,
                                                             String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' up to parent block '" + ptbl + "' FAILED";
      String reason = "Child block '" + tbl + "' with a ranking variable " +
         "condition, and parent block '" + ptbl + "' with a group aggregate.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Create move up fo child with ranking but parent with groups.
    */
   public static TransformationFault subRankingInvisibleInParent(String tbl,
                                                                 String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' up to parent block '" + ptbl + "' FAILED";
      String reason = "Child block '" + tbl + "' ranking variable " +
         "condition column is invisible in parent block '" + ptbl + ".";
      return new TransformationFault(desc, reason);
   }

   /**
    * Create move up for child with ranking and multiple groups.
    */
   public static TransformationFault subRankingGroupedWithVariable(String tbl, String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' up to parent block '" + ptbl + "' FAILED";
      String reason = "Child block '" + tbl + "' with a ranking variable " +
         "condition and multiple group columns.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains ranking condition.
    */
   public static TransformationFault containsRanking(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There are both ranking and variable " +
         "conditions in the table '" + tbl + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains aggregate ranking condition.
    */
   public static TransformationFault containsAggregateRanking(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "It contains a variable aggregate ranking condition" +
         " in the table '" + tbl + "'.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains ranking condition.
    */
   public static TransformationFault containsParentRanking(String tbl,
                                                           String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There is a ranking condition in the parent node '"+ ptbl +
         "'. Moving selections up to a table with a ranking condition is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains post condition.
    */
   public static TransformationFault containsPostCondition(String tbl,
                                                           String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There is a post condition in the parent node '"+ ptbl +
         "'. Moving selections up to a table with a post condition is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move selection up for contains percentage aggregation.
    */
   public static TransformationFault containsParentPercentage(String tbl,
                                                              String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There is a percentage aggregation in the parent node '" + ptbl +
         "'. Moving selections up to a table with a percentage aggregation is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains named group.
    */
   public static TransformationFault containsParentNamedGroup(String tbl,
                                                              String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There is a named group assembly in the parent node '" + ptbl +
         "'. Creating an MV for a table with a named group is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains crosstab.
    */
   public static TransformationFault containsParentCrosstab(String tbl,
                                                            String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The parent node '" + ptbl + "' is a crosstab table. " +
         "Moving selections up to a crosstab table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains rotated table.
    */
   public static TransformationFault containsParentRotated(String tbl,
                                                           String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The parent node '" + ptbl + "' is a rotated table. " +
         "Moving selections up to a rotated table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains rotated table.
    */
   public static TransformationFault containsRotated(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The child node '" + tbl + "' is a rotated table. " +
         "Moving selections up from a rotated table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains unpivot table.
    */
   public static TransformationFault containsParentUnpivot(String tbl,
                                                           String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The parent node '" + ptbl + "' is an unpivot table. " +
         "Moving selections up to an unpivot table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains unpivot table.
    */
   public static TransformationFault containsUnpivot(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The child node '" + tbl + "' is an unpivot table. " +
         "Moving selections up from an unpivot table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains named group.
    */
   public static TransformationFault containsNamedGroup(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "There is a named group assembly in the child block '" + tbl +
         "'. Creating an MV for a table with a named group is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains crosstab.
    */
   public static TransformationFault containsCrosstab(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The block '" + tbl + "' is a crosstab table. " +
         "Moving selections up from a crosstab table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move up selection for contains max rows.
    */
   public static TransformationFault containsMaxRows(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from block '" + tbl +
         "' FAILED";
      String reason = "The max rows is defined in the block '" + tbl +
         "'. Moving selections up from a table with max rows defined is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for aggregate selection.
    */
   public static TransformationFault aggregateSelection(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The selection column is aggregated. " +
         "Creating an MV for an aggregated selection is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for nested selection.
    */
   public static TransformationFault nestedSelection(String tbl,
                                                     String ptbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      ptbl = AbstractTransformer.normalizeBlockName(ptbl);
      String desc = "Transfer all selections from child block '" + tbl +
         "' FAILED";
      String reason = "The parent node '" + ptbl + "' contains a nested selection. " +
         "Moving selections up to a table that contains a nested selection is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create mv for contains embedded table.
    */
   public static TransformationFault containsEmbedded(String tbl,
                                                      String vassembly)
   {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      vassembly = AbstractTransformer.normalizeBlockName(vassembly);
      String desc = "Create mv at '" + tbl + "' for viewsheet assembly '"
         + vassembly + "' FAILED";
      String reason = "The block '" + tbl + "' is an embedded table. " +
         "Embedded tables cannot be used to create an MV, as they may be changed at runtime.";
      return new TransformationFault(desc, reason);
   }

   public static TransformationFault containsQueryVariable(String tbl, String vassembly) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      vassembly = AbstractTransformer.normalizeBlockName(vassembly);
      String desc = "Create mv at '" + tbl + "' for viewsheet assembly '"
         + vassembly + "' FAILED";
      String reason = "The block '" + tbl +
         "' references a physical query with a variable which is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't move selection up to a concatenated node.
    */
   public static TransformationFault moveUpConcatenated(Assembly tbl,
                                                        Assembly ptbl) {
      String tbln = AbstractTransformer.normalizeBlockName(tbl.getName());
      String ptbln = AbstractTransformer.normalizeBlockName(ptbl.getName());
      String desc = "Transfer all selections from child block '" + tbln +
         "' FAILED";
      String reason = "The parent node '" + ptbln + "' is a concatenated table. " +
         "Moving selections up to a concatenated table is not supported.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create ws mv for embedded table.
    */
   public static TransformationFault containsEmbeddedTable(String tbl, String wsName) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      wsName = AbstractTransformer.normalizeBlockName(wsName);
      String desc = "Create mv at '" + tbl + "' for worksheet '"
         + wsName + "' FAILED";
      String reason = "The block '" + tbl + "' is an embedded table. " +
         "Embedded tables do not support MVs, as they may be changed at runtime.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create ws mv for cube table.
    */
   public static TransformationFault containsCubeTable(String tbl, String wsName) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      wsName = AbstractTransformer.normalizeBlockName(wsName);
      String desc = "Create mv at '" + tbl + "' for worksheet '"
         + wsName + "' FAILED";
      String reason = "The block '" + tbl + "' is a cube table. " +
         "Cube tables do not support MVs.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Can't create ws mv for bound tables that are a dependent of tables that contain sql formulas
    */
   public static TransformationFault containsSQLFormula(String boundTable, String sqlFormulaTable,
                                                        String wsName)
   {
      boundTable = AbstractTransformer.normalizeBlockName(boundTable);
      wsName = AbstractTransformer.normalizeBlockName(wsName);
      String desc = "Create mv at '" + boundTable + "' for worksheet '"
         + wsName + "' FAILED";
      String reason = "The block '" + boundTable + "' is a dependent of '" + sqlFormulaTable +
         "' which contains a SQL formula. Please move the SQL formula to the bound table or use " +
         "a javascript formula instead in order for it to be a candidate for materialization.";
      return new TransformationFault(desc, reason);
   }

   public static TransformationFault containsInputDynamicTable(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "MV is not supported for table: " + tbl;
      String reason = "Table is the target of input field binding and can be changed at runtime.";
      return new TransformationFault(desc, reason);
   }

   public static TransformationFault containsPostAggrCalc(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "MV is not supported for table: " + tbl;
      String reason = "Table contains aggregate calculated field.";
      return new TransformationFault(desc, reason);
   }

   public static TransformationFault containsMaxRowsWithSubSelection(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "MV is not supported for table: " + tbl;
      String reason = "Table has max rows defined with selection defined on sub tables.";
      return new TransformationFault(desc, reason);
   }

   public static TransformationFault transformedWithMVUpdate(String tbl) {
      tbl = AbstractTransformer.normalizeBlockName(tbl);
      String desc = "MV is not supported for table: " + tbl;
      String reason = "A transformed (rotated/unpivot/crosstab) table has MV update condition.";
      return new TransformationFault(desc, reason);
   }

   /**
    * Return the string description of this fault.
    */
   public String toString() {
      return "TransformationFault[Description: " + desc + ", reason: " +
         reason + "]";
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TransformationFault)) {
         return false;
      }

      TransformationFault fault2 = (TransformationFault) obj;
      return Tool.equals(this.desc, fault2.desc) &&
             Tool.equals(this.reason, fault2.reason);
   }

   private String desc = "";
   private String reason = "";
}
