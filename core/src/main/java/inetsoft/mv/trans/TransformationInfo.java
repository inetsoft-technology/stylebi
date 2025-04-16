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

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;

/**
 * This class is used to record the transformations that are performed on the
 * assemblies. It is used for information purpose only.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class TransformationInfo implements Comparable<TransformationInfo> {
   /**
    * Top level transformation.
    */
   public static final int TOP = 0;
   /**
    * Normal level transformation.
    */
   public static final int NORMAL = 1;
   /**
    * Detail level transformation.
    */
   public static final int DETAIL = 2;

   /**
    * Create a transformation record.
    */
   private TransformationInfo(String desc, int level) {
      this.desc = desc;
      this.level = level;
   }

   /**
    * Get a description of this fault.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Get the message level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Return the string description of this fault.
    */
   public String toString() {
      return desc;
   }

   public static String multiDistinctCount() {
      return "Only one distinct count formula field is supported in " +
             "distributed MV for assemblies using the same source table.";
   }

   public static String namedGroup(DataRef ref) {
      return "Field '" + ref.getName() + "' with a named group is not combinable";
   }

   public static String formulaNotCombinable(DataRef ref, AggregateFormula f) {
      return "Field '" + ref.getName() + "' with formula '" +
             f.getLabel() + "' is not combinable.";
   }

   /**
    * Transforming the calc fields for a table assembly.
    */
   public static TransformationInfo calcFields(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Push MV to the sub-table of table with calc fields: "
         + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Transforming the range selections for a table assembly.
    */
   public static TransformationInfo rangeSelection(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Transfer all range selections from data block: " + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Transforming all named selections for a table assembly.
    */
   public static TransformationInfo namedSelection(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Transfer all named selections from data block: " + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Transforming all named groups for a table assembly.
    */
   public static TransformationInfo namedGroup(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Transfer all named groups from data block: " + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Pulling runtime selections down to its base table assembly.
    */
   public static TransformationInfo selectionDown(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Transfer all selections down to child block: " + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Pulling down its aggregates to its base table assembly.
    */
   public static TransformationInfo aggregateDown(String block) {
      block = AbstractTransformer.normalizeBlockName(block);
      String desc = "Transfer all aggregates down to child block: " + block;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Pulling up its selections up from children to parent nodes.
    */
   public static TransformationInfo selectionUp(String child) {
      child = AbstractTransformer.normalizeBlockName(child);
      String desc = "Transfer all selections from child block: " + child;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Pulling up all selections up to parent node.
    */
   public static TransformationInfo selectionUpToParent(String parent) {
      parent = AbstractTransformer.normalizeBlockName(parent);
      String desc = "Transfer all selections up to block: " + parent;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Move up selection from children.
    */
   public static TransformationInfo moveUp(WSColumn selcol) {
      String desc = "Moved selection to the parent: " + selcol;
      return new TransformationInfo(desc, NORMAL);
   }

   /**
    * Warning users that there is sql formula.
    */
   public static TransformationInfo containsSQLFormula(Assembly tbl,
      DataRef ref)
   {
      String tbln = AbstractTransformer.normalizeBlockName(tbl.getName());
      String desc = "Formula '" + ref.getName() + "' in Table '" + tbl +
         "' contains unsupported SQL syntax. " +
         "Please modify expression to use standard SQL or JavaScript syntax.";
      return new TransformationInfo(desc, TOP);
   }

   /**
    * Warning users that the mv conditions defined in current table is
    * not suitable, because there is not selection below/on this table.
    */
   public static TransformationInfo mvConditionUnderSelection(String table) {
      table = AbstractTransformer.normalizeBlockName(table);
      String desc = "MV conditions defined in '" + table + "' is not suitable, "
                    + "as no selection exists on or below this table.";
      return new TransformationInfo(desc, DETAIL);
   }

   /**
    * Warn users about MV conditions that will not be applied because the table is above
    * the MV table
    */
   public static TransformationInfo mvConditionsIgnored(String table) {
      table = AbstractTransformer.normalizeBlockName(table);
      String desc = "MV conditions in table '" + table +
         "' are defined above the MV table and will not be applied.";
      return new TransformationInfo(desc, DETAIL);
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TransformationInfo)) {
         return false;
      }

      TransformationInfo info2 = (TransformationInfo) obj;
      return Tool.equals(this.desc, info2.desc) && this.level == info2.level;
   }

   /**
    * Compare this info with another object.
    */
   @Override
   public int compareTo(TransformationInfo other) {
      if(other == null) {
         return -1;
      }

      return level - other.level;
   }

   private String desc;
   private int level;
}
