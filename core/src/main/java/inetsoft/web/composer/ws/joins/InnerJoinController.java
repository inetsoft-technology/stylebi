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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;
import inetsoft.web.composer.ws.TableModeController;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.WSFocusCompositeTableCommand;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides an endpoint for creating and editing the inner join tables.
 */
@Controller
public class InnerJoinController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/ws/edit-inner-join")
   public void editInnerJoin(
      @Payload WSInnerJoinEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final TableAssemblyOperator noperator = new TableAssemblyOperator();

      for(TableAssemblyOperatorModel operator : event.getOperators()) {
         noperator.addOperator(WorksheetEventUtil.convertOperator(ws, operator));
      }

      if(event.getTableName() != null) {
         final RelationalJoinTableAssembly joinTableAssembly =
            (RelationalJoinTableAssembly) ws.getAssembly(event.getTableName());
         editExistingJoinTable(rws, joinTableAssembly,
                               noperator, false);
         WorksheetEventUtil.refreshColumnSelection(
            rws, joinTableAssembly.getName(), false);
         WorksheetEventUtil.loadTableData(
            rws, joinTableAssembly.getAbsoluteName(), true, true);
         WorksheetEventUtil.refreshAssembly(
            rws, joinTableAssembly.getAbsoluteName(), true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         AssetEventUtil.refreshTableLastModified(
            ws, joinTableAssembly.getAbsoluteName(), true);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/drop-table-into-join-schema")
   public void dragTableIntoJoinSchema(
      @Payload WSDropTableIntoJoinSchemaEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final String joinTable = event.getJoinTable();
      final String dragTable = event.getDroppedTable();
      checkContainsSubtable(rws, joinTable, dragTable);
      final SchemaTableInfo info = new SchemaTableInfo(event.getLeft(), event.getTop());
      final JoinMetaInfo joinInfo = joinSourceAndTargetTables(
         dragTable, joinTable, rws, info, false, commandDispatcher);
      validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/dialog/inner-join-dialog/inner-join")
   public void joinTables(
      @Payload WSJoinTablesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      rws.cloneWS();
      final String[] tables = event.getTables();

      if(tables.length < 2) {
         return;
      }

      String joinTable = tables[0];

      for(int i = 1; i < tables.length; i++) {
         checkContainsSubtable(rws, joinTable, tables[i]);
      }

      JoinMetaInfo joinInfo = null;

      for(int i = 1; i < tables.length; i++) {
         final JoinMetaInfo currInfo =
            joinSourceAndTargetTables(tables[i], joinTable, rws, null, false, commandDispatcher);

         if(i == 1 && currInfo != null) {
            joinInfo = currInfo;
         }

         joinTable = joinInfo == null ? null : joinInfo.joinTable.getAbsoluteName();
      }

      validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/join-tables")
   public void joinTablePair(
      @Payload WSJoinTablePairEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      rws.cloneWS();
      final String sourceTable = event.getLeftTable();
      final String targetTable = event.getRightTable();
      checkContainsSubtable(rws, targetTable, sourceTable);
      final JoinMetaInfo joinInfo = joinSourceAndTargetTables(
         sourceTable, targetTable, rws, null, event.isJoinTarget(), commandDispatcher);
      validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   private void checkContainsSubtable(RuntimeWorksheet rws, String targetTable, String sourceTable) {
      final Assembly assembly = rws.getWorksheet().getAssembly(targetTable);

      if(!(assembly instanceof RelationalJoinTableAssembly)) {
         return;
      }

      if(JoinUtil.tableContainsSubtable((CompositeTableAssembly) assembly, sourceTable)) {
         throw new MessageException(catalog.getString(
            "common.table.recursiveJoin"));
      }
   }

   private void validateWSAndDispatchCommands(
      CommandDispatcher commandDispatcher, RuntimeWorksheet rws,
      JoinMetaInfo joinInfo, Principal principal) throws Exception
   {
      rws.getWorksheet().checkDependencies();
      dispatchCommands(joinInfo, rws, commandDispatcher, principal);
   }

   private void dispatchCommands(
      JoinMetaInfo joinInfo, RuntimeWorksheet rws,
      CommandDispatcher commandDispatcher, Principal principal) throws Exception
   {
      if(joinInfo == null || joinInfo.joinTable == null) {
         return;
      }

      final RelationalJoinTableAssembly jtable = joinInfo.joinTable;
      AssetEventUtil.initColumnSelection(rws, jtable);

      final AbstractTableAssembly targetTable = joinInfo.targetTable;
      final AbstractTableAssembly sourceTable = joinInfo.sourceTable;
      TableModeController.setDefaultTableMode(jtable, rws.getAssetQuerySandbox());
      AssetEventUtil.layoutResultantTable(targetTable, sourceTable, jtable);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      WorksheetEventUtil.createAssembly(rws, jtable, commandDispatcher, principal);
      WSFocusCompositeTableCommand focusJoinTableCommand =
         WSFocusCompositeTableCommand.builder()
            .compositeTableName(jtable.getAbsoluteName())
            .build();
      commandDispatcher.sendCommand(focusJoinTableCommand);
   }

   // add source to target if target is a join table, or create a new join table otherwise.
   // @param joinTarget true to force to join to target instead of adding to join table.
   // @return join table name
   private JoinMetaInfo joinSourceAndTargetTables(
      String sourceName, String targetName, RuntimeWorksheet rws,
      SchemaTableInfo schemaTableInfo, boolean joinTarget, CommandDispatcher dispatcher)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      AbstractTableAssembly sourceTable = (AbstractTableAssembly) ws.getAssembly(sourceName);
      AbstractTableAssembly targetTable = (AbstractTableAssembly) ws.getAssembly(targetName);

      if(sourceTable == null || targetTable == null) {
         return null;
      }

      JoinMetaInfo joinInfo = null;
      TableAssemblyOperator noperator;
      ArrayList<AbstractTableAssembly> innerJoinTables = new ArrayList<>();
      RelationalJoinTableAssembly targetJoinTable = null;
      boolean autojoined = false;

      // target table is join table
      if(targetTable instanceof RelationalJoinTableAssembly && !joinTarget) {
         targetJoinTable = (RelationalJoinTableAssembly) targetTable;
         targetJoinTable.clearCache();
         rws.getAssetQuerySandbox().resetTableLens(targetJoinTable.getAbsoluteName());

         for(TableAssembly table : targetJoinTable.getTableAssemblies()) {
            if(table instanceof AbstractTableAssembly) {
               innerJoinTables.add((AbstractTableAssembly) table);
            }
         }
      }
      else {
         innerJoinTables.add(targetTable);
      }

      if(targetJoinTable != null) {
         noperator = getOperatorsOfJoinTable(targetJoinTable);
      }
      else {
         noperator = new TableAssemblyOperator();
      }

      outer: for(AbstractTableAssembly innerJoinTable : innerJoinTables) {
         AbstractTableAssembly leftTable, rightTable;

         if(targetJoinTable != null) {
            leftTable = innerJoinTable;
            rightTable = sourceTable;
         }
         else {
            leftTable = sourceTable;
            rightTable = innerJoinTable;
         }

         ArrayList<ColumnRef> leftRefs = getColumnRefs(leftTable);
         ArrayList<ColumnRef> rightRefs = getColumnRefs(rightTable);
         ArrayList<ColumnRef[]> columnPairs = getColumnPairs(leftRefs, rightRefs, dispatcher);

         if(columnPairs.size() > 0) {
            autojoined = true;
         }

         for(ColumnRef[] pair : columnPairs) {
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setOperation(XConstants.INNER_JOIN);
            op.setDistinct(false);
            op.setLeftAttribute(pair[0]);
            op.setLeftTable(leftTable.getAbsoluteName());
            op.setRightAttribute(pair[1]);
            op.setRightTable(rightTable.getAbsoluteName());
            noperator.addOperator(op);
            break outer;
         }
      }

      if(targetJoinTable == null) {
         RelationalJoinTableAssembly jtable;

         if(autojoined) {
            jtable = createNewInnerJoinTable(rws, noperator);
         }
         else {
            String[] pair = new String[2];
            pair[0] = sourceTable.getAbsoluteName();
            pair[1] = targetTable.getAbsoluteName();
            jtable = CrossJoinController.process(rws, pair, true).joinTable;
         }

         if(jtable != null) {
            joinInfo = new JoinMetaInfo(jtable, true, targetTable, sourceTable);
         }
      }
      else {
         joinInfo = new JoinMetaInfo(targetJoinTable, false);

         if(!autojoined) {
            TableAssemblyOperator operator = new TableAssemblyOperator();
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setOperation(TableAssemblyOperator.INNER_JOIN);
            operator.addOperator(op);

            TableAssembly[] arr = targetJoinTable.getTableAssemblies(true);
            TableAssembly[] narr = new TableAssembly[arr.length + 1];

            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[arr.length] = sourceTable;
            targetJoinTable.setOperator(narr[narr.length - 2].getName(),
                                        narr[narr.length - 1].getName(), operator);
            targetJoinTable.setTableAssemblies(narr);
         }

         editExistingJoinTable(rws, targetJoinTable,
                               noperator, true);

         if(schemaTableInfo != null) {
            CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) targetJoinTable
               .getTableInfo();

            if(info.getSchemaPixelPosition(sourceTable.getName()) != null) {
               info.setSchemaTableInfo(sourceTable.getName(), schemaTableInfo);
            }
         }
      }

      return joinInfo;
   }

   public TableAssemblyOperator getOperatorsOfJoinTable(
      RelationalJoinTableAssembly targetJoinTable)
   {
      TableAssemblyOperator noperator = new TableAssemblyOperator();
      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) targetJoinTable
         .getTableInfo();
      Enumeration tbls = info.getOperatorTables();

      while(tbls.hasMoreElements()) {
         String[] tables = (String[]) tbls.nextElement();
         TableAssemblyOperator top = info.getOperator(tables[0], tables[1]);
         if(!top.getOperator(0).isMergeJoin() && !top.getOperator(0).isCrossJoin()) {
            TableAssemblyOperator.Operator[] operators = top.getOperators();

            for(TableAssemblyOperator.Operator op : operators) {
               if(op.isCrossJoin()) {
                  continue;
               }

               if("null".equals(op.getLeftTable()) || "null"
                  .equals(op.getRightTable()))
               {
                  op.setLeftTable(tables[0]);
                  op.setRightTable(tables[1]);
               }

               noperator.addOperator(op);
            }
         }
      }

      return noperator;
   }

   // Resulting ColumnRef[] will be length 2
   private ArrayList<ColumnRef[]> getColumnPairs(
      ArrayList<ColumnRef> leftRefs, ArrayList<ColumnRef> rightRefs, CommandDispatcher dispatcher)
   {
      ArrayList<ColumnRef[]> pairs = new ArrayList<>();
      int leftCount = Math.min(leftRefs.size(), 500);
      int rightCount = Math.min(rightRefs.size(), 500);

      if(leftRefs.size() > 500 || rightRefs.size() > 500) {
         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(Catalog.getCatalog().getString("composer.ws.autoJoin.columnExceed"));
         dispatcher.sendCommand(cmd);
      }

      for(int i = 0; i < leftCount; i++) {
         for(int j = 0; j < rightCount; j++) {
            ColumnRef leftRef = leftRefs.get(i);
            ColumnRef rightRef = rightRefs.get(j);

            if(areColumnsAutojoinable(leftRef, rightRef)) {
               ColumnRef[] pair = new ColumnRef[2];
               pair[0] = leftRef;
               pair[1] = rightRef;
               pairs.add(pair);
            }
         }
      }

      return pairs;
   }

   private boolean areColumnsAutojoinable(ColumnRef leftRef, ColumnRef rightRef) {
      if(AssetUtil.isMergeable(leftRef.getDataType(),
                               rightRef.getDataType()))
      {
         boolean attributes = leftRef.getAttribute()
            .equals(rightRef.getAttribute());
         boolean names = leftRef.getName().equals(rightRef.getName());

         return attributes || names;
      }

      return false;
   }

   private ArrayList<ColumnRef> getColumnRefs(AbstractTableAssembly table) {
      Enumeration selection = table.getColumnSelection(true).getAttributes();
      ArrayList<ColumnRef> refs = new ArrayList<>();

      while(selection.hasMoreElements()) {
         refs.add((ColumnRef) selection.nextElement());
      }

      return refs;
   }

   private class IncompatibleDataTypesException extends RuntimeException {

      public IncompatibleDataTypesException(String s) {
         super(s);
      }
   }

   /**
    * From 12.2 EditInnerJoinOverEvent.
    * Creates a new inner join table
    *
    * @param rws               the runtime worksheet
    * @param noperator         the operators for the join table
    */
   public RelationalJoinTableAssembly createNewInnerJoinTable(
      RuntimeWorksheet rws, TableAssemblyOperator noperator)
      throws Exception
   {
      // check if operators are valid
      RelationalJoinTableAssembly joined = null;

      for(int i = 0; i < noperator.getOperatorCount(); i++) {
         if(!isValidOperator(noperator.getOperator(i))) {
            throw new IncompatibleDataTypesException(
               catalog.getString(
                  "common.invalidJoinType1",
                  noperator.getOperator(i).getLeftAttribute().getName(),
                  noperator.getOperator(i).getRightAttribute().getName()));
         }
      }

      //by hunk
      noperator.checkValidity();

      for(int i = 0; i < noperator.getOperatorCount(); i++) {
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = noperator.getOperator(i);
         top.addOperator(op);
         joined = concatenateTable(joined, top, rws);
      }

      return joined;
   }

   private RelationalJoinTableAssembly concatenateTable(
      RelationalJoinTableAssembly jTable,
      TableAssemblyOperator operator,
      RuntimeWorksheet rws)
   {
      Worksheet ws = rws.getWorksheet();
      TableAssembly ltable = (TableAssembly) ws.getAssembly(
         operator.getOperator(0).getLeftTable());
      TableAssembly rtable = (TableAssembly) ws.getAssembly(
         operator.getOperator(0).getRightTable());

      if(jTable == null) {
         final String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         jTable = new RelationalJoinTableAssembly(ws, nname,
                                                new TableAssembly[]{ ltable, rtable },
                                                new TableAssemblyOperator[]{ operator });

         ws.addAssembly(jTable);
      }
      else {
         TableAssembly[] arr = jTable.getTableAssemblies(true);
         boolean lcontain = isContain(jTable, ltable);
         boolean rcontain = isContain(jTable, rtable);

         if(!lcontain && !rcontain) {
            TableAssembly[] narr = new TableAssembly[arr.length + 2];
            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[narr.length - 2] = ltable;
            narr[narr.length - 1] = rtable;
            jTable.setTableAssemblies(narr);
            setOperator(jTable, operator, narr);
         }
         else if(lcontain && !rcontain) {
            TableAssembly[] narr = new TableAssembly[arr.length + 1];
            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[narr.length - 1] = rtable;
            jTable.setTableAssemblies(narr);
            setOperator(jTable, operator, narr);
         }
         else if(!lcontain && rcontain) {
            TableAssembly[] narr = new TableAssembly[arr.length + 1];
            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[narr.length - 1] = ltable;
            jTable.setTableAssemblies(narr);
            setOperator(jTable, operator, narr);
         }
         else {
            setOperator(jTable, operator, arr);
         }

         ws.removeAssembly(jTable);
         ws.addAssembly(jTable);
      }

      return jTable;
   }

   /**
    * If join table contains table.
    */
   private boolean isContain(RelationalJoinTableAssembly jTable, TableAssembly table) {
      TableAssembly[] tables = jTable.getTableAssemblies();

      for(TableAssembly tempTable : tables) {
         if(tempTable.getName().equals(table.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if operator is valid.
    */
   private boolean isValidOperator(TableAssemblyOperator.Operator operator) {
      return AssetUtil.isMergeable(operator.getLeftAttribute().getDataType(),
                                   operator.getRightAttribute().getDataType());
   }

   /**
    * Set operator to join table.
    */
   private void setOperator(
      RelationalJoinTableAssembly jTable,
      TableAssemblyOperator newTop, TableAssembly[] narr)
   {
      Enumeration iter = jTable.getOperators();
      TableAssemblyOperator.Operator newOp = newTop.getOperator(0);
      List<TableAssemblyOperator> tops = new ArrayList<>();

      while(iter.hasMoreElements()) {
         tops.add((TableAssemblyOperator) iter.nextElement());
      }

      for(TableAssemblyOperator top : tops) {
         TableAssemblyOperator.Operator[] ops = top.getOperators();

         for(TableAssemblyOperator.Operator op : ops) {
            boolean sameSideEqual =
               newOp.getLeftTable().equals(op.getLeftTable()) &&
                  newOp.getRightTable().equals(op.getRightTable());
            boolean nSameSideEqual =
               newOp.getLeftTable().equals(op.getRightTable()) &&
                  newOp.getRightTable().equals(op.getLeftTable());

            if(sameSideEqual || nSameSideEqual) {
               DataRef newColumnLeft = sameSideEqual ?
                  newOp.getLeftAttribute() : newOp.getRightAttribute();
               DataRef newColumnRight = sameSideEqual ?
                  newOp.getRightAttribute() : newOp.getLeftAttribute();
               String leftTable = sameSideEqual ?
                  newOp.getLeftTable() : newOp.getRightTable();
               String rightTable = sameSideEqual ?
                  newOp.getRightTable() : newOp.getLeftTable();

               if(!Tool.equals(newColumnLeft.getName(), op.getLeftAttribute().getName()) &&
                  !Tool.equals(newColumnRight.getName(), op.getRightAttribute().getName()))
               {
                  newOp.setLeftTable(leftTable);
                  newOp.setRightTable(rightTable);
                  newOp.setLeftAttribute(newColumnLeft);
                  newOp.setRightAttribute(newColumnRight);

                  if(nSameSideEqual) {
                     notifyOp(newOp);
                  }

                  top.addOperator(newOp);

                  return;
               }
            }
         }
      }

      exchange(newOp, narr);

      if(jTable.getOperator(newOp.getRightTable(), newOp.getLeftTable()) != null &&
         jTable.getOperator(newOp.getRightTable(), newOp.getLeftTable()).isCrossJoin())
      {
         jTable.removeOperator(newOp.getRightTable(), newOp.getLeftTable());
      }

      jTable.setOperator(newOp.getLeftTable(), newOp.getRightTable(), newTop);
   }


   /**
    * if nSameSideEqual, notify the op.
    */
   private void notifyOp(TableAssemblyOperator.Operator newOp) {
      int op = newOp.getOperation();

      switch(op) {
      case TableAssemblyOperator.GREATER_JOIN:
         op = TableAssemblyOperator.LESS_JOIN;
         break;
      case TableAssemblyOperator.LESS_JOIN:
         op = TableAssemblyOperator.GREATER_JOIN;
         break;
      case TableAssemblyOperator.GREATER_EQUAL_JOIN:
         op = TableAssemblyOperator.LESS_EQUAL_JOIN;
         break;
      case TableAssemblyOperator.LESS_EQUAL_JOIN:
         op = TableAssemblyOperator.GREATER_EQUAL_JOIN;
         break;
      default:
         break;
      }

      newOp.setOperation(op);
   }

   private void exchange(TableAssemblyOperator.Operator op, TableAssembly[] narr) {
      Map<TableAssembly, Integer> tables = new HashMap<>();
      int leftWeight = 0;
      int rightWeight = 0;

      for(int i = 0; i < narr.length; i++) {
         tables.put(narr[i], narr.length - i);
      }

      for(TableAssembly table : tables.keySet()) {
         if(table.getName().equals(op.getLeftTable())) {
            leftWeight = tables.get(table);
         }

         if(table.getName().equals(op.getRightTable())) {
            rightWeight = tables.get(table);
         }
      }

      if(leftWeight != 0 && rightWeight != 0 && rightWeight > leftWeight) {
         String tempTable = op.getLeftTable();
         DataRef tempColumn = op.getLeftAttribute();
         op.setLeftTable(op.getRightTable());
         op.setRightTable(tempTable);
         op.setLeftAttribute(op.getRightAttribute());
         op.setRightAttribute(tempColumn);
      }
   }

   /**
    * From 12.2 EditJoinTypeOverEvent
    */
   public void editExistingJoinTable(
      RuntimeWorksheet rws, RelationalJoinTableAssembly joinTableAssembly,
      TableAssemblyOperator noperator, boolean containsNewTable) throws Exception
   {
      List<TableAssemblyOperator.Operator> operators = Arrays
         .asList(noperator.getOperators());
      String[] leftTables = operators.stream()
         .map(TableAssemblyOperator.Operator::getLeftTable).toArray(String[]::new);
      String[] rightTables = operators.stream()
         .map(TableAssemblyOperator.Operator::getRightTable).toArray(String[]::new);
      TableAssemblyOperator allOperator = new TableAssemblyOperator();

      for(TableAssemblyOperator.Operator operator : operators) {
         if(operator.getLeftAttribute() != null && operator.getRightAttribute() != null &&
            !AssetUtil.isMergeable(operator.getLeftAttribute().getDataType(),
                                   operator.getRightAttribute().getDataType()))
         {
            throw new MessageException(
               catalog.getString("common.invalidJoinType"));
         }

         allOperator.addOperator(operator);
      }

      try {
         allOperator.checkValidity();
      }
      catch(MessageException | ConfirmException e) {
         throw e;
      }
      catch(Exception e) {
         throw new MessageException(e.getMessage(), e);
      }

      List<String[]> pairs = new ArrayList<>();

      // Get the previous table relationships.
      for(Enumeration<?> e = joinTableAssembly.getOperatorTables(); e.hasMoreElements(); ) {
         String[] pair = (String[]) e.nextElement();
         pairs.add(pair);
      }

      Map<String, SchemaTableInfo> oldSchemaTableInfos = new HashMap<>(
         ((CompositeTableAssemblyInfo) joinTableAssembly
            .getTableInfo()).getSchemaTableInfos());

      // Clear the existing relationships. Do this in a separate loop to prevent
      // a concurrent modification exception in previous loop.
      for(String[] pair : pairs) {
         joinTableAssembly.removeOperator(pair[0], pair[1]);
      }

      // If there is a new table in the join, it should be concatenated with the table.
      if(containsNewTable) {
         for(int i = 0; i < noperator.getOperatorCount(); i++) {
            TableAssemblyOperator top = new TableAssemblyOperator();
            TableAssemblyOperator.Operator op = noperator.getOperator(i);
            top.addOperator(op);
            joinTableAssembly = concatenateTable(joinTableAssembly, top, rws);
         }
      }

      Set<String> joinedTables = new HashSet<>();
      Map<String, Map<String, TableAssemblyOperator>> joins =
         new HashMap<>();

      for(int i = 0; i < operators.size(); i++) {
         addOperator(
            leftTables[i], rightTables[i],
            operators.get(i), joinTableAssembly, joins, joinedTables);
      }

      // Check for tables no longer joined. If any exist, join them using a
      // cross join with the table to which they were previously joined.
      for(String[] pair : pairs) {
         String ltable = null;
         String rtable = null;

         if(!joinedTables.contains(pair[0])) {
            ltable = pair[1];
            rtable = pair[0];
         }
         else if(!joinedTables.contains(pair[1])) {
            ltable = pair[0];
            rtable = pair[1];
         }

         if(ltable != null) {
            Map<String, TableAssemblyOperator> joins2 = joins
               .computeIfAbsent(ltable, k -> new HashMap<>());

            TableAssemblyOperator join = new TableAssemblyOperator();
            joins2.put(rtable, join);
            joinTableAssembly.setOperator(ltable, rtable, join);

            TableAssemblyOperator.Operator operator =
               new TableAssemblyOperator.Operator();
            operator.setLeftTable(ltable);
            operator.setRightTable(rtable);
            operator.setOperation(TableAssemblyOperator.CROSS_JOIN);
            join.addOperator(operator);

            joinedTables.add(ltable);
            joinedTables.add(rtable);
         }
      }

      // ChrisSpagnoli bug1417795166475 2014-12-18
      // Check validity *after* processing of *all* operator updates completes.
      for(Enumeration<?> e = joinTableAssembly.getOperatorTables(); e.hasMoreElements();) {
         String[] pair = (String[]) e.nextElement();
         TableAssemblyOperator toperator =
            joinTableAssembly.getOperator(pair[0], pair[1]);
         toperator.checkValidity();
      }

      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) joinTableAssembly
         .getTableInfo();

      // Reinstate old schema table infos.
      for(Map.Entry<String, SchemaTableInfo> e : oldSchemaTableInfos.entrySet()) {
         if(info.getSchemaTableInfos().containsKey(e.getKey())) {
            info.setSchemaTableInfo(e.getKey(), e.getValue());
         }
      }
   }

   private void addOperator(
      String leftTable, String rightTable,
      TableAssemblyOperator.Operator operator,
      RelationalJoinTableAssembly joinTableAssembly,
      Map<String, Map<String, TableAssemblyOperator>> joins,
      Set<String> joinedTables)
   {
      Map<String, TableAssemblyOperator> joins2;
      List subtables = Arrays.asList(joinTableAssembly.getTableNames());
      String ltable = leftTable;
      String rtable = rightTable;

      // always from left to right, which is what the gui expects
      if(subtables.indexOf(ltable) > subtables.indexOf(rtable)) {
         ltable = rightTable;
         rtable = leftTable;
         flipOp(operator);
      }

      if(joins.containsKey(leftTable) && joins.get(leftTable).containsKey(rightTable)) {
         joins2 = joins.get(leftTable);
      }
      else if(joins.containsKey(rtable) && joins.get(rtable).containsKey(ltable)) {
         // Does the relationship exist in the reverse order? Use the
         // existing relationship with the inverse operation.
         joins2 = joins.get(rtable);
         ltable = rightTable;
         rtable = leftTable;
         flipOp(operator);
      }
      else {
         joins2 = joins.computeIfAbsent(ltable, k -> new HashMap<>());
      }

      TableAssemblyOperator join = joins2.get(rtable);

      if(join == null) {
         join = new TableAssemblyOperator();
         joins2.put(rtable, join);
         joinTableAssembly.setOperator(ltable, rtable, join);
      }

      join.addOperator(operator);
      joinedTables.add(leftTable);
      joinedTables.add(rightTable);
   }

   /**
    * Flip operator order.
    */
   public static void flipOp(TableAssemblyOperator.Operator operator) {
      int op = operator.getOperation();

      switch(op) {
      case TableAssemblyOperator.GREATER_JOIN:
         op = TableAssemblyOperator.LESS_JOIN;
         break;

      case TableAssemblyOperator.LESS_JOIN:
         op = TableAssemblyOperator.GREATER_JOIN;
         break;

      case TableAssemblyOperator.GREATER_EQUAL_JOIN:
         op = TableAssemblyOperator.LESS_EQUAL_JOIN;
         break;

      case TableAssemblyOperator.LESS_EQUAL_JOIN:
         op = TableAssemblyOperator.GREATER_EQUAL_JOIN;
         break;

      case TableAssemblyOperator.LEFT_JOIN:
         op = TableAssemblyOperator.RIGHT_JOIN;
         break;

      case TableAssemblyOperator.RIGHT_JOIN:
         op = TableAssemblyOperator.LEFT_JOIN;
         break;

      default:
         break;
      }

      String leftTable = operator.getLeftTable();
      String rightTable = operator.getRightTable();
      DataRef leftAttribute = operator.getLeftAttribute();
      DataRef rightAttribute = operator.getRightAttribute();

      operator.setOperation(op);
      operator.setLeftTable(rightTable);
      operator.setRightTable(leftTable);
      operator.setLeftAttribute(rightAttribute);
      operator.setRightAttribute(leftAttribute);
   }

   private static class JoinMetaInfo {
      private JoinMetaInfo(RelationalJoinTableAssembly joinTable, boolean newTable) {
         this(joinTable, newTable, null, null);
      }

      private JoinMetaInfo(
         RelationalJoinTableAssembly joinTable, boolean newTable, AbstractTableAssembly targetTable,
         AbstractTableAssembly sourceTable)
      {
         this.joinTable = joinTable;
         this.newTable = newTable;
         this.targetTable = targetTable;
         this.sourceTable = sourceTable;
      }

      final RelationalJoinTableAssembly joinTable;
      final boolean newTable;
      final AbstractTableAssembly targetTable;
      final AbstractTableAssembly sourceTable;
   }

   private static final Catalog catalog = Catalog.getCatalog();
}
