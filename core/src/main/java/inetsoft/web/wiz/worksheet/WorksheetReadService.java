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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reads a live {@link RuntimeWorksheet} and produces a structured
 * {@link WorksheetModel} DTO suitable for agent consumption.
 *
 * <p>The service has no injected dependencies; it operates purely on the
 * {@link RuntimeWorksheet} passed to {@link #read(RuntimeWorksheet)}.</p>
 */
@Service
public class WorksheetReadService {

   /**
    * Reads all table assemblies in the supplied runtime worksheet and returns
    * a {@link WorksheetModel} describing their columns, joins, filters,
    * aggregates, and sort directives.
    *
    * @param rws the live runtime worksheet; must not be {@code null}
    * @return a fully-populated worksheet model
    */
   public WorksheetModel read(RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      String primaryName = ws.getPrimaryAssemblyName();

      List<WorksheetModel.TableModel> tables = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof TableAssembly t) {
            boolean primary = t.getName().equals(primaryName);
            tables.add(readTable(t, primary));
         }
      }

      return new WorksheetModel(tables);
   }

   // -------------------------------------------------------------------------
   // Table
   // -------------------------------------------------------------------------

   private WorksheetModel.TableModel readTable(TableAssembly t, boolean primary) {
      String name = t.getName();
      String type = tableType(t);
      List<WorksheetModel.ColumnModel> columns = readColumns(t);
      List<WorksheetModel.JoinModel> joins = readJoins(t);
      List<WorksheetModel.FilterModel> preConditions =
         readConditions(t.getPreConditionList());
      List<WorksheetModel.FilterModel> postConditions =
         readConditions(t.getPostConditionList());
      List<WorksheetModel.FilterModel> rankingConditions =
         readConditions(t.getRankingConditionList());
      WorksheetModel.AggregateModel aggregates = readAggregates(t);
      List<WorksheetModel.SortModel> sorts = readSorts(t);

      return new WorksheetModel.TableModel(
         name, type, columns, joins,
         preConditions, postConditions, rankingConditions,
         aggregates, sorts, primary);
   }

   private String tableType(TableAssembly t) {
      if(t instanceof EmbeddedTableAssembly) {
         return "EMBEDDED";
      }
      else if(t instanceof AbstractJoinTableAssembly) {
         return "JOIN";
      }
      else if(t instanceof MirrorTableAssembly) {
         return "MIRROR";
      }
      else if(t instanceof UnpivotTableAssembly) {
         return "UNPIVOT";
      }
      else if(t instanceof RotatedTableAssembly) {
         return "ROTATED";
      }
      else if(t instanceof ConcatenatedTableAssembly) {
         return "CONCAT";
      }
      else {
         return "TABLE";
      }
   }

   // -------------------------------------------------------------------------
   // Columns
   // -------------------------------------------------------------------------

   private List<WorksheetModel.ColumnModel> readColumns(TableAssembly t) {
      ColumnSelection cs = t.getColumnSelection(false);

      if(cs == null) {
         return Collections.emptyList();
      }

      List<WorksheetModel.ColumnModel> columns = new ArrayList<>();
      int count = cs.getAttributeCount();

      for(int i = 0; i < count; i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref == null) {
            continue;
         }

         if(ref instanceof ColumnRef cr) {
            columns.add(readColumn(cr));
         }
      }

      return columns;
   }

   private WorksheetModel.ColumnModel readColumn(ColumnRef ref) {
      String name = ref.getAttribute();
      String type = ref.getTypeNode() != null ? ref.getTypeNode().getType() : null;
      String alias = ref.getAlias();
      String expression = null;

      if(ref.isExpression() && ref.getDataRef() instanceof ExpressionRef exprRef) {
         expression = exprRef.getExpression();
      }

      return new WorksheetModel.ColumnModel(name, type, alias, expression);
   }

   // -------------------------------------------------------------------------
   // Joins
   // -------------------------------------------------------------------------

   private List<WorksheetModel.JoinModel> readJoins(TableAssembly t) {
      if(!(t instanceof AbstractJoinTableAssembly joinTable)) {
         return Collections.emptyList();
      }

      Enumeration<TableAssemblyOperator> operators = joinTable.getOperators();

      if(operators == null) {
         return Collections.emptyList();
      }

      List<WorksheetModel.JoinModel> joins = new ArrayList<>();

      while(operators.hasMoreElements()) {
         TableAssemblyOperator operator = operators.nextElement();

         if(operator == null) {
            continue;
         }

         for(TableAssemblyOperator.Operator op : operator.getOperators()) {
            if(op == null) {
               continue;
            }

            String leftTable = op.getLeftTable();
            String rightTable = op.getRightTable();

            if(leftTable == null || rightTable == null) {
               continue;
            }

            String leftKey = op.getLeftAttribute() != null
               ? op.getLeftAttribute().getAttribute() : null;
            String rightKey = op.getRightAttribute() != null
               ? op.getRightAttribute().getAttribute() : null;
            String opName = op.getName();

            joins.add(new WorksheetModel.JoinModel(leftTable, leftKey, rightTable, rightKey, opName));
         }
      }

      return joins;
   }

   // -------------------------------------------------------------------------
   // Filters / conditions
   // -------------------------------------------------------------------------

   private List<WorksheetModel.FilterModel> readConditions(ConditionListWrapper wrapper) {
      if(wrapper == null || wrapper.isEmpty()) {
         return Collections.emptyList();
      }

      List<WorksheetModel.FilterModel> result = new ArrayList<>();
      int size = wrapper.getConditionSize();
      String pendingJunction = null;

      for(int i = 0; i < size; i++) {
         if(wrapper.isJunctionOperator(i)) {
            JunctionOperator jop = wrapper.getJunctionOperator(i);
            pendingJunction = jop.getJunction() == JunctionOperator.AND ? "AND" : "OR";
         }
         else if(wrapper.isConditionItem(i)) {
            ConditionItem item = wrapper.getConditionItem(i);

            if(item == null) {
               continue;
            }

            DataRef dataRef = item.getAttribute();
            String field = dataRef != null ? dataRef.getAttribute() : null;

            XCondition xc = item.getXCondition();
            String operation = xc != null ? String.valueOf(xc.getOperation()) : null;
            List<String> values = extractValues(xc);

            result.add(new WorksheetModel.FilterModel(field, operation, values, pendingJunction));
            pendingJunction = null;
         }
      }

      return result;
   }

   private List<String> extractValues(XCondition xc) {
      if(!(xc instanceof Condition c)) {
         return Collections.emptyList();
      }

      int count = c.getValueCount();
      List<String> values = new ArrayList<>(count);

      for(int i = 0; i < count; i++) {
         Object v = c.getValue(i);
         values.add(v != null ? v.toString() : null);
      }

      return values;
   }

   // -------------------------------------------------------------------------
   // Aggregates
   // -------------------------------------------------------------------------

   private WorksheetModel.AggregateModel readAggregates(TableAssembly t) {
      AggregateInfo info = t.getAggregateInfo();

      if(info == null || info.isEmpty()) {
         return null;
      }

      // Groups
      GroupRef[] groupRefs = info.getGroups();
      List<String> groups = new ArrayList<>(groupRefs.length);

      for(GroupRef gr : groupRefs) {
         groups.add(gr.getName());
      }

      // Aggregates
      AggregateRef[] aggRefs = info.getAggregates();
      List<WorksheetModel.AggregateModel.AggregateRefModel> aggregates =
         new ArrayList<>(aggRefs.length);

      for(AggregateRef ar : aggRefs) {
         String column = ar.getAttribute();
         AggregateFormula formula = ar.getFormula();
         String formulaName = formula != null ? formula.getName() : null;
         aggregates.add(new WorksheetModel.AggregateModel.AggregateRefModel(column, formulaName));
      }

      return new WorksheetModel.AggregateModel(groups, aggregates);
   }

   // -------------------------------------------------------------------------
   // Sorts
   // -------------------------------------------------------------------------

   private List<WorksheetModel.SortModel> readSorts(TableAssembly t) {
      SortInfo sortInfo = t.getSortInfo();

      if(sortInfo == null || sortInfo.getSortCount() == 0) {
         return Collections.emptyList();
      }

      SortRef[] sortRefs = sortInfo.getSorts();
      List<WorksheetModel.SortModel> sorts = new ArrayList<>(sortRefs.length);

      for(SortRef sr : sortRefs) {
         String field = sr.getAttribute();
         String order = sr.getOrder() == XConstants.SORT_ASC ? "ASC" : "DESC";
         sorts.add(new WorksheetModel.SortModel(field, order));
      }

      return sorts;
   }
}
