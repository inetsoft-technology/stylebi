/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptEnv;
import inetsoft.web.binding.VSScriptableController;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.ExpressionDialogModel;
import inetsoft.web.composer.model.ws.ExpressionDialogModelValidator;
import inetsoft.web.composer.ws.RenameColumnController;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.io.StringReader;
import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class ExpressionDialogService extends WorksheetControllerService {

   public ExpressionDialogService(ViewsheetService viewsheetService,
                                  VSScriptableController scriptController)
   {
      super(viewsheetService);
      this.scriptController = scriptController;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ExpressionDialogModel getExpressionModel(@ClusterProxyKey String runtimeId, String tableName,
                                                   String columnIndexAsParam, boolean showOriginalName,
                                                   Principal principal) throws Exception
   {
      int columnIndex = columnIndexAsParam == null ? -1 : Integer.parseInt(columnIndexAsParam);
      ExpressionDialogModel.Builder modelBuilder = ExpressionDialogModel.builder();
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tableName);

      if(table == null) {
         return null;
      }

      modelBuilder.tableName(tableName);
      AssetQuery query = AssetUtil.handleMergeable(rws, table);
      modelBuilder.sqlMergeable(query.isSourceMergeable0());
      ColumnSelection columns = table.getColumnSelection();

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      ColumnRef column = null;

      if(columnIndex >= 0) {
         XTable data = box.getTableLens(tableName, AssetEventUtil.getMode(table));
         column = AssetUtil.findColumn(data, columnIndex, columns);
      }

      List<TreeNodeModel> fieldChildren = new ArrayList<>();
      columns = VSUtil.sortColumns(columns);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(i);

         if(col.equals(column) && column.getDataRef() instanceof ExpressionRef) {
            ExpressionRef eRef = (ExpressionRef) column.getDataRef();
            modelBuilder.oldName(eRef.getName())
               .dataType(column.getDataType())
               .setSQL(column.isSQL())
               .expression(eRef.getExpression());
         }
         else {
            List<TreeNodeModel> dateChildren = new ArrayList<>();

            if(XSchema.isDateType(col.getDataType())) {
               String dtype = col.getDataType();
               createDateTreeNodes(dtype, col, dateChildren);
            }

            String tooltip = showOriginalName ? ColumnRef.getTooltip(col) : col.getDescription();
            TreeNodeModel child = TreeNodeModel.builder()
               .label(col.getName()) //.replaceAll("(OUTER_|_\\d+)", ""))
               .data(col.getName())
               .tooltip(tooltip)
               .icon("column-icon")
               .leaf(true)
               .children(dateChildren)
               .build();

            fieldChildren.add(child);
         }
      }

      List<TreeNodeModel> variableChildren = new ArrayList<>();
      Arrays.stream(ws.getAssemblies()).filter(
            (assembly) -> assembly instanceof VariableAssembly && assembly.isVisible())
         .forEach((variableAssembly) -> {
            String absoluteName = variableAssembly.getAbsoluteName();
            variableChildren.add(TreeNodeModel.builder()
                                    .label(absoluteName)
                                    .data("parameter." + absoluteName)
                                    .icon("variable-icon")
                                    .leaf(true)
                                    .build());
         });

      ArrayList<TreeNodeModel> rootChildren = new ArrayList<>();

      TreeNodeModel.Builder root = TreeNodeModel.builder().label("root");
      TreeNodeModel.Builder fields = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Fields"))
         .icon("data-table-icon")
         .children(fieldChildren);

      if(variableChildren.size() > 0) {
         TreeNodeModel variables = TreeNodeModel.builder()
            .label("Variables")
            .children(variableChildren)
            .build();

         modelBuilder.variableTree(variables);
         rootChildren.add(variables);
      }
      else {
         fields.expanded(true);
      }

      rootChildren.add(0, fields.build());
      modelBuilder.columnTree(root.children(rootChildren).build());
      modelBuilder.scriptDefinitions(scriptController.getScriptDefinition(rws, table, principal));

      if(column == null || !(column.getDataRef() instanceof ExpressionRef)) {
         modelBuilder.dataType(XSchema.STRING)
            .setSQL(query.isSourceMergeable0())
            .expression("");
      }

      return modelBuilder.build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ExpressionDialogModelValidator validateExpression(@ClusterProxyKey String runtimeId,
                                                            ExpressionDialogModel model, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = model.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table == null || model.newName() == null) {
         return null;
      }

      ColumnSelection columns = (ColumnSelection) table.getColumnSelection().clone();
      ExpressionDialogModelValidator.Builder builder = ExpressionDialogModelValidator.builder();

      if(table instanceof BoundTableAssembly) {
         SourceInfo sourceInfo =
            (SourceInfo) ((BoundTableAssembly) table).getSourceInfo().clone();
         AssetEntry wsEntry = rws.getEntry();
         sourceInfo.setProperty("fileName", wsEntry.getProperty("fileName"));
         ColumnSelection cinfo = AssetEventUtil.getAttributesBySource(
            getWorksheetEngine(), rws.getUser(), sourceInfo);
         ColumnRef conflictingColumn = getConflictingColumn(cinfo, model.newName());

         if(!model.newName().equals(model.oldName()) && conflictingColumn != null) {
            builder.invalidName(
               RenameColumnController.
                  createColumnConflictErrorMessage(model.newName(), conflictingColumn));
         }
      }

      ColumnRef conflictingColumn = getConflictingColumn(columns, model.newName());

      if(!model.newName().equals(model.oldName()) && conflictingColumn != null) {
         builder.invalidName(
            RenameColumnController.
               createColumnConflictErrorMessage(model.newName(), conflictingColumn));
      }

      ColumnRef column;
      ExpressionRef exp;

      // new column
      if(model.oldName().length() == 0) {
         int index = columns.getAttributeCount();
         exp = new ExpressionRef(null, model.newName());
         exp.setName(model.newName());
         column = new ColumnRef(exp);
         columns.addAttribute(index, column);
         AggregateInfo group = table.getAggregateInfo();

//          @by larryl, if aggregate is defined, add the new formula to the
//          summary so the formula would not 'disappear' from the table
         if(group != null && !group.isEmpty() && table.isAggregate() &&
            table.isPlain())
         {
            AggregateFormula formula = AggregateFormula.COUNT_ALL;
            AggregateRef aggregateref = new AggregateRef(column, formula);
            group.addAggregate(aggregateref);
         }
      }
      else {
         column = (ColumnRef) columns.getAttribute(model.oldName());
         exp = (ExpressionRef) column.getDataRef();
      }

      column.setSQL(model.isSQL());
      exp.setExpression(model.expression());

      int idx = columns.indexOfAttribute(column);
      ColumnRef ocolumn = idx >= 0 ?
         (ColumnRef) columns.getAttribute(idx) : null;

      // @by davidd 2009-02-12 bug1232345495535 Add property to table
      // indicating if a column has been tainted by adhoc, in order to
      // restrict script package-access.
//      table.setProperty("adhoc.edit." + column.getName(),
//         isWebEvent() ? "true" : null); TODO
      table.setProperty("adhoc.edit." + column.getName(), "true");

      if(ocolumn != null && !(ocolumn.getDataRef() instanceof ExpressionRef)) {
         builder.invalidName(
            RenameColumnController.
               createColumnConflictErrorMessage(model.newName(), ocolumn));
      }
      else if(ocolumn != null) {
         ExpressionRef oexp = (ExpressionRef) ocolumn.getDataRef();
         String ostr = oexp.getExpression();
         String str = ExpressionRef.getSQLExpression(column.isSQL(),
                                                     exp.getExpression());

         // check script, if it is wrong, alert warning.
         if(str != null /*&& !confirmed*/) {
            try {
               check(str, rws.getAssetQuerySandbox().getScriptEnv(),
                     column.isSQL());
            }
            catch(Exception ex) {
               String message = column.isSQL() ? "viewer.viewsheet.sqlFailed" :
                  "viewer.worksheet.scriptFailed";
               String[] params = column.isSQL() ? new String[]{ ex.getMessage() }
                  : new String[]{ column.getName(), tname };

               builder.invalidExpression(Catalog.getCatalog().getString(message, (Object[]) params));
            }
         }
      }

      return builder.build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setModel(@ClusterProxyKey String runtimeId, ExpressionDialogModel model, Principal principal,
                        CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = model.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table == null || model.newName() == null) {
         return null;
      }

      ColumnSelection columns = table.getColumnSelection().clone();

      if(columns.getAttributeCount() >= Util.getOrganizationMaxColumn()) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(command);

         return null;
      }

      if(table instanceof BoundTableAssembly) {
         SourceInfo sourceInfo = ((BoundTableAssembly) table).getSourceInfo();
         ColumnSelection cinfo = AssetEventUtil.getAttributesBySource(
            getWorksheetEngine(), rws.getUser(), sourceInfo);
         ColumnRef conflictingColumn = getConflictingColumn(cinfo, model.newName());

         if(!model.newName().equals(model.oldName()) && conflictingColumn != null) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setType(MessageCommand.Type.ERROR);
            messageCommand.setMessage(RenameColumnController.
                                         createColumnConflictErrorMessage(model.newName(), conflictingColumn));
            messageCommand.setAssemblyName(tname);
            commandDispatcher.sendCommand(messageCommand);
            return null;
         }
      }

      ColumnRef conflictingColumn = getConflictingColumn(columns, model.newName());
      ColumnRef ocol = getConflictingColumn(columns, model.oldName());

      if(!model.newName().equals(model.oldName()) && conflictingColumn != null) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setType(MessageCommand.Type.ERROR);
         messageCommand.setMessage(RenameColumnController.
                                      createColumnConflictErrorMessage(model.newName(), conflictingColumn));
         messageCommand.setAssemblyName(tname);
         commandDispatcher.sendCommand(messageCommand);
         return null;
      }

      ColumnRef column;
      ExpressionRef exp;

      // new column
      if(model.oldName().length() == 0) {
         int index = columns.getAttributeCount();
         exp = new ExpressionRef(null, model.newName());
         exp.setName(model.newName());
         column = new ColumnRef(exp);
         columns.addAttribute(index, column);
         AggregateInfo group = table.getAggregateInfo();

         //@by larryl, if aggregate is defined, add the new formula to the
         //summary so the formula would not 'disappear' from the table
         if(group != null && !group.isEmpty() && table.isAggregate() &&
            table.isPlain())
         {
            AggregateFormula formula = AggregateFormula.COUNT_ALL;
            AggregateRef aggregateref = new AggregateRef(column, formula);
            group.addAggregate(aggregateref);
         }
      }
      else {
         column = (ColumnRef) columns.getAttribute(model.oldName());
         exp = (ExpressionRef) column.getDataRef();
      }

      column.setSQL(model.isSQL());
      exp.setExpression(model.expression());

      int idx = columns.indexOfAttribute(column);
      ColumnRef ocolumn = idx >= 0 ?
         (ColumnRef) columns.getAttribute(idx) : null;

      // @by davidd 2009-02-12 bug1232345495535 Add property to table
      // indicating if a column has been tainted by adhoc, in order to
      // restrict script package-access.
      table.setProperty("adhoc.edit." + column.getName(), "true");

      if(ocolumn != null) {
         ExpressionRef oexp = (ExpressionRef) ocolumn.getDataRef();
         String ostr = oexp.getExpression();

         oexp.setExpression(exp.getExpression());
         ocolumn.setSQL(column.isSQL());

         try {
            table.checkDependency();
         }
         catch(Exception ex) {
            oexp.setExpression(ostr);
            throw ex;
         }

         // modify data type if required
         if(!Tool.equals(ocolumn.getDataType(), model.dataType())) {
            ocolumn.setDataType(model.dataType());
         }

         table.setColumnSelection(columns);

         // rename column if required
         String nname = model.newName();

         if(model.oldName().length() > 0 && !model.oldName()
            .equals(model.newName()))
         {
            RenameColumnController.renameColumn(ws, commandDispatcher, table, column, nname);
         }

         if(WorksheetEventUtil.refreshVariables(
            rws, super.getWorksheetEngine(), tname, commandDispatcher))
         {
            return null;
         }

         WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
         WorksheetEventUtil.loadTableData(rws, tname, true, true);
         WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         AssetEventUtil.refreshTableLastModified(ws, tname, true);
      }

      return null;
   }

   private void createDateTreeNodes(String dtype, ColumnRef col, List<TreeNodeModel> children) {
      Catalog catalog = Catalog.getCatalog();
      String[] levels = Util.getDateParts(dtype);

      for(int i = 0; i < levels.length; i++) {
         String label = catalog.getString(levels[i]);
         String dateFunc = Util.getDatePartFunc(levels[i]);

         TreeNodeModel dateNode = TreeNodeModel.builder()
            .label(label + "(" + col.getName() + ")")
            .data(dateFunc + "(" + col.getName() + ")")
            .type(Util.DATE_PART_COLUMN)
            .tooltip(col.getName())
            .icon("column-icon")
            .leaf(true)
            .build();
         children.add(dateNode);
      }
   }

   /**
    * Check the syntax of current script.
    */
   public void check(String text, ScriptEnv env, boolean sql) throws Exception {
      if(text != null && text.trim().length() != 0) {
         // java script
         if(!sql) {
            if(env != null) {
               env.compile(text);
            }
         }
         // sql expression
         else {
            inetsoft.uql.util.sqlparser.SQLLexer lexer =
               new inetsoft.uql.util.sqlparser.SQLLexer(new StringReader(text));
            inetsoft.uql.util.sqlparser.SQLParser parser =
               new inetsoft.uql.util.sqlparser.SQLParser(lexer);
            Object obj = parser.value_exp();
            String token = null;

            try {
               token = lexer.nextToken().toString();
            }
            catch(Exception ex) {
               return;
            }

            if(token != null && token.toLowerCase().indexOf("null") == -1) {
               throw new RuntimeException("Unexpected token: " + token);
            }
         }
      }
   }

   private ColumnRef getConflictingColumn(
      ColumnSelection columns, String name)
   {
      return AssetUtil.findColumnConflictingWithAlias(columns, null, name, false);
   }

   private VSScriptableController scriptController;
}
