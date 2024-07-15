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
package inetsoft.report.composition;


import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;

import java.io.*;
import java.util.*;

/**
 * ToolTipGenerator is used to generate the tooltips.
 */
public class ToolTipGenerator {
   /**
    * Constructor.
    */
   public ToolTipGenerator(TableAssembly assembly) {
      this.assembly = assembly;
   }

   /**
    * Generate the tool tip.
    */
   public ToolTipContainer generateToolTip() {
      createSourceTip();
      createConditionTip();
      createAggregateTip();
      createJoinTip();
      createSortTip();
      return this.con;
   }

   /**
    * Generate condition tool tip.
    */
   private void createConditionTip() {
      List<ConditionList> conds = new ArrayList<>();
      ConditionListWrapper wrapper = assembly.getPreConditionList();
      ConditionList list;

      if(wrapper != null) {
         list = wrapper.getConditionList();

         if(list != null) {
            conds.add(list);
         }
      }

      if(assembly instanceof BoundTableAssembly) {
         BoundTableAssembly bassmly = (BoundTableAssembly) assembly;
         ConditionAssembly[] comassmlys = bassmly.getConditionAssemblies();

         for(ConditionAssembly comassmly : comassmlys) {
            list = comassmly.getConditionList();

            if(list != null) {
               conds.add(list);
            }
         }
      }

      list = ConditionUtil.mergeConditionList(conds, JunctionOperator.AND);
      createSpecifiedCondition(list, "Pre-aggregate Conditions");

      wrapper = assembly.getPostConditionList();

      if(wrapper != null) {
         list = wrapper.getConditionList();
         createSpecifiedCondition(list, "Post-aggregate Conditions");
      }

      wrapper = assembly.getRankingConditionList();

      if(wrapper != null) {
         list = wrapper.getConditionList();
         createSpecifiedCondition(list, "Ranking Conditions");
      }
   }

   /**
    * Create specified condition.
    */
   private void createSpecifiedCondition(
      ConditionList conds,
      String condHint)
   {
      if(conds == null || conds.getSize() == 0) {
         return;
      }

      StringWriter swriter = new StringWriter();
      PrintWriter writer = new LineLimitPrintWriter(swriter);
      writer.println(catalog.getString(condHint) + ":");

      for(int i = 0; i < conds.getConditionSize(); i++) {
         HierarchyItem item = conds.getItem(i);
         writeIndent(writer, item.getLevel() + 1);

         if(item instanceof JunctionOperator) {
            JunctionOperator op = (JunctionOperator) item;
            writer.println(op.getJunction() == JunctionOperator.AND ?
                              catalog.getString("And") :
                              catalog.getString("Or"));
         }
         else if(item instanceof ConditionItem) {
            writer.print(((ConditionItem) item).getAttribute().toView());
            XCondition xcond = ((ConditionItem) item).getXCondition();

            if(xcond instanceof DateRangeAssembly) {
               xcond = ((DateRangeAssembly) xcond).getDateRange();
            }

            writer.println(formatConditionItem(xcond));
         }
      }

      writer.flush();
      String condTip = swriter.toString();

      if(condTip != null && condTip.length() > 0) {
         String condition = con.getCondition() == null ? condTip :
            (con.getCondition() + condTip);
         String[] conditions = condition.split("\n");

         if(conditions.length > LineLimitPrintWriter.LINELIMIT) {
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < LineLimitPrintWriter.LINELIMIT; i++) {
               sb.append(conditions[i]).append("\n");
            }

            sb.append("  ..." + "\n\n");
            condition = sb.toString();
         }

         con.setCondition(condition);
      }
   }

   /**
    * Generate the aggregation tool tip.
    */
   private void createAggregateTip() {
      AggregateInfo ainfo = assembly.getAggregateInfo();

      if(ainfo == null) {
         return;
      }

      if(ainfo.getGroupCount() == 0 && ainfo.getAggregateCount() == 0) {
         return;
      }

      StringWriter swriter = new StringWriter();
      PrintWriter writer = new LineLimitPrintWriter(swriter);
      writer.println(catalog.getString("Group") + ":");
      GroupRef[] grefs = ainfo.getGroups();

      for(GroupRef gref : grefs) {
         writeIndent(writer);
         writer.println(gref.toView());
      }

      AggregateRef[] arefs = ainfo.getAggregates();
      writer.println(catalog.getString("Aggregate") + ":");

      for(AggregateRef aref : arefs) {
         writeIndent(writer);
         writer.print(getAggregateTip(aref));

         if(aref.isPercentage()) {
            writer.print(' ');
            writer.println(catalog.getString("Percentage"));
         }

         writer.println();
      }

      writer.flush();
      String aggTip = swriter.toString();

      if(aggTip != null && aggTip.length() > 0) {
         con.setAggregate(swriter.toString());
      }
   }

   /**
    * Return localized aggregate.
    */
   private String getAggregateTip(AggregateRef aref) {
      AggregateFormula formula = aref.getFormula();

      if(formula == null) {
         return aref.getDataRef().toView();
      }

      String formulaStr = catalog.getString(formula.getFormulaName());

      if(formula.isTwoColumns() && aref.getSecondaryColumn() != null) {
         return formulaStr + "(" + aref.getDataRef().toView() + ", " +
            aref.getSecondaryColumn().toView() + ")";
      }

      return formulaStr + "(" + aref.getDataRef().toView() + ")";
   }

   /**
    * Generate the join tool tip.
    */
   private void createJoinTip() {
      if(!(assembly instanceof AbstractJoinTableAssembly)) {
         return;
      }

      AbstractJoinTableAssembly jtable = (AbstractJoinTableAssembly) assembly;
      StringWriter swriter = new StringWriter();
      PrintWriter writer = new LineLimitPrintWriter(swriter);
      Enumeration tbls = jtable.getOperatorTables();

      while(tbls.hasMoreElements()) {
         String[] tables = (String[]) tbls.nextElement();
         TableAssemblyOperator top = jtable.getOperator(tables[0], tables[1]);
         TableAssemblyOperator.Operator[] ops = top.getOperators();

         for(TableAssemblyOperator.Operator op : ops) {
            // @by stephenwebster, For bug1428655718393
            // if at least one op is not a merge or cross join, it should
            // show the tooltip.  Removed code which only checked the first
            // operator on the assembly.
            if(op.isMergeJoin() || op.isCrossJoin()) {
               continue;
            }

            DataRef ldef = op.getLeftAttribute();
            DataRef rdef = op.getRightAttribute();

            if(ldef == null || rdef == null) {
               continue;
            }

            String lname = ldef.getName();
            String rname = rdef.getName();

            if(!lname.contains(".")) {
               lname = tables[0] + "." + lname;
               rname = tables[1] + "." + rname;
            }

            writer.println(lname + " " + op.getName2() + " " + rname);
         }
      }

      writer.flush();
      String joinTip = swriter.toString();

      if(joinTip != null && joinTip.length() > 0) {
         con.setJoin(joinTip);
      }
   }

   /**
    * Generate the source tool tip.
    */
   private void createSourceTip() {
      if(!(assembly instanceof BoundTableAssembly)) {
         return;
      }

      BoundTableAssembly jtable = (BoundTableAssembly) assembly;
      SourceInfo sinfo = jtable.getSourceInfo();

      if(sinfo == null || sinfo.isEmpty()) {
         return;
      }

      con.setSource(sinfo.toView());
   }

   /**
    * Write indent with the default level 1.
    */
   private void writeIndent(PrintWriter writer) {
      writeIndent(writer, 1);
   }

   /**
    * Write indent with the specified level.
    */
   private void writeIndent(PrintWriter writer, int level) {
      for(int i = 0; i < level * INDENT; i++) {
         writer.write(" ");
      }
   }

   /**
    * Format the condition item.
    */
   private String formatConditionItem(XCondition cond) {
      int op = cond.getOperation();
      cond.getType();
      String str = getValuesString(cond);
      String ranking = "";

      // fix bug1271670572591 by skyfeng, add data ref for top and bottom.
      if(cond instanceof RankingCondition) {
         DataRef data = ((RankingCondition) cond).getDataRef();

         if(data != null) {
            ranking = " of " + data.toString();
         }
      }

      if(op == Condition.EQUAL_TO) {
         str = "=" + str;
      }
      else if(op == Condition.GREATER_THAN) {
         str = ">" + (cond.isEqual() ? "=" : "") + str;
      }
      else if(op == Condition.LESS_THAN) {
         str = "<" + (cond.isEqual() ? "=" : "") + str;
      }
      else if(op == Condition.ONE_OF) {
         if(isSubQuery(str)) {
            str = " IN " + str;
         }
         else {
            str = " IN" + " (" + str + ")";
         }
      }
      else if(op == Condition.BETWEEN) {
         String[] twoItems = str.split(",");
         str = " Between " + twoItems[0] + " and " + twoItems[1];
      }
      else if(op == Condition.STARTING_WITH) {
         if(XSchema.STRING.equals(cond.getType()) ||
            XSchema.CHAR.equals(cond.getType()))
         {
            str = str.substring(1, str.length() - 1);
         }

         str = " Like " + "'" + str + "%'";
      }
      else if(op == Condition.LIKE) {
         if(XSchema.STRING.equals(cond.getType()) ||
            XSchema.CHAR.equals(cond.getType()))
         {
            str = str.substring(1, str.length() - 1);
         }

         str = " Like " + "'" + str + "'";
      }
      else if(op == Condition.CONTAINS) {
         if(XSchema.STRING.equals(cond.getType()) ||
            XSchema.CHAR.equals(cond.getType()))
         {
            str = str.substring(1, str.length() - 1);
         }

         str = " Like " + "'%" + str + "%'";
      }
      else if(op == Condition.NULL) {
         str = " Is NULL";
      }
      else if(op == Condition.DATE_IN) {
         str = " Is in range " + str;
      }
      else if(op == Condition.TOP_N) {
         str = " Is top " + str + ranking;
      }
      else if(op == Condition.BOTTOM_N) {
         str = " Is bottom " + str + ranking;
      }

      if(cond.isNegated()) {
         str = " Not (" + str + ")";
      }

      return str;
   }

   /**
    * Get the values as string.
    */
   private String getValuesString(XCondition xcond) {
      if(xcond instanceof DateCondition) {
         return ((DateCondition) xcond).getLabel();
      }
      else if(xcond instanceof Condition) {
         Condition cond = (Condition) xcond;
         List values = cond.getValues();
         String type = cond.getType();
         StringBuilder strbuild = new StringBuilder();

         if(values.size() == 1 && values.get(0) instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) values.get(0);
            DataRef ref = sub.getAttribute();
            DataRef mref = sub.getMainAttribute();
            DataRef sref = sub.getSubAttribute();
            String stableName = sub.getQuery();
            strbuild.append("(select ");
            strbuild.append(stableName).append(".").append(ref.getAttribute());
            strbuild.append(" from ").append(stableName);

            if(sref != null && mref != null) {
               strbuild.append(" where ").append(stableName).append(".")
                  .append(sref.getAttribute());
               strbuild.append("=");
               strbuild.append(assembly.getName()).append(".").append(mref.getAttribute());
            }

            strbuild.append(")");
            return strbuild.toString();
         }

         for(int i = 0; i < values.size(); i++) {
            String value = type.equals(XSchema.BOOLEAN) ?
               catalog.getString(Condition.getValueString(values.get(i))) :
               Condition.getValueString(values.get(i));

            if(XSchema.STRING.equals(cond.getType()) ||
               XSchema.CHAR.equals(cond.getType()))
            {
               value = "'" + value + "'";
            }

            strbuild.append(value);

            if(i < values.size() - 1) {
               strbuild.append(",");
            }
         }

         return strbuild.toString();
      }
      else if(xcond instanceof RankingCondition) {
         return ((RankingCondition) xcond).getN() + "";
      }

      return "";
   }

   private void createSortTip() {
      SortInfo sortInfo = assembly.getSortInfo();

      if(sortInfo != null && !sortInfo.isEmpty()) {
         StringWriter swriter = new StringWriter();
         PrintWriter writer = new LineLimitPrintWriter(swriter);
         writer.println(catalog.getString("Sort") + ":");

         for(SortRef sortRef : sortInfo.getSorts()) {
            writeIndent(writer);

            if(sortRef.getOrder() == XConstants.SORT_ASC) {
               writer.print("\u2191 "); // up arrow
            }
            else if(sortRef.getOrder() == XConstants.SORT_DESC) {
               writer.print("\u2193 "); // down arrow
            }

            writer.println(sortRef.toView());
         }

         String sortTip = swriter.toString();

         if(sortTip != null && sortTip.length() > 0) {
            con.setSort(sortTip);
         }
      }
   }

   /**
    * Indicate the str is a subquery.
    */
   private boolean isSubQuery(String str) {
      return str.contains("select ") && str.contains(" from ");
   }

   /**
    * LineLimitPrintWriter is used to write limited lines.
    */
   private static class LineLimitPrintWriter extends PrintWriter {
      /**
       * Limit line number.
       */
      private static int LINELIMIT = 10;

      /**
       * Constructor.
       */
      LineLimitPrintWriter(Writer out) {
         super(out);
      }

      @Override
      public void print(String s) {
         if(lineCount > LINELIMIT ||
            lineCount == LINELIMIT && !("...").equals(s))
         {
            return;
         }

         if(s != null && s.length() > 100) {
            s = s.substring(0, 97) + "...";
         }

         super.print(s);
      }

      @Override
      public void println(String s) {
         if(lineCount > LINELIMIT) {
            return;
         }
         else if(lineCount == LINELIMIT) {
            super.println("...");
            lineCount++;
            return;
         }

         if(s != null && s.length() > 100) {
            s = s.substring(0, 97) + "...";
         }

         super.println(s);
         lineCount++;
      }

      private int lineCount = 0;
   }

   private int INDENT = 2;
   private ToolTipContainer con = new ToolTipContainer();
   private TableAssembly assembly;
   private Catalog catalog = Catalog.getCatalog();
}