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
package inetsoft.web.wiz.binding;

import inetsoft.report.CellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.web.binding.command.GetCellScriptCommand;
import inetsoft.web.binding.command.GetPredefinedNamedGroupCommand;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.event.GetCellScriptEvent;
import inetsoft.web.binding.event.GetPredefinedNamedGroupEvent;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.table.OrderModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.binding.event.CopyCutCalcCellEvent;
import inetsoft.web.binding.event.ModifyTableLayoutEvent;
import inetsoft.web.binding.event.SetCellBindingEvent;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.TableCell;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.awt.Rectangle;
import java.util.List;

/**
 * Freehand (calc) table authoring: the cell grid and per-cell bindings.
 *
 * <p><b>This service deliberately does not share the read-merge-write architecture</b> the
 * chart, table and crosstab services use, and the reason is structural rather than incidental:
 * {@code CalcTableBindingModel} extends {@code BaseTableBindingModel} and adds nothing at all.
 * A calc table's binding does not live in the binding model — it lives in the layout, and
 * StyleBI provides a dedicated cell-addressed endpoint family for it.
 *
 * <p>So this is cell-addressed rather than model-merged. It is a different architecture for a
 * genuinely different object.
 */
@Service
public class CalcTableService {
   @Autowired
   public CalcTableService(ViewsheetSessionService sessions, VSTableLayoutService layoutService,
                           DataRefModelFactoryService refModelService,
                           BindableFieldsService fieldsService)
   {
      this.sessions = sessions;
      this.layoutService = layoutService;
      this.refModelService = refModelService;
      this.fieldsService = fieldsService;
   }

   /**
    * The grid: its dimensions and every cell's binding, in the token vocabulary.
    *
    * <p>The discovery call everything else depends on. Note that any layout operation shifts
    * coordinates, so a layout read before one is stale afterwards.
    */
   public Map<String, Object> readLayout(String sessionToken, Principal user,
                                         String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
      TableLayout layout = layoutOf(assembly);
      List<Map<String, Object>> cells = new ArrayList<>();

      for(int row = 0; row < layout.getRowCount(); row++) {
         for(int col = 0; col < layout.getColCount(); col++) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("row", row);
            cell.put("col", col);
            Dimension span = layout.getSpan(row, col);

            if(span != null) {
               cell.put("spanRows", span.height);
               cell.put("spanCols", span.width);
            }

            cell.put("binding",
                     CalcCellVocabulary.describe(
                        layoutService.getCellBindingInfo(assembly, row, col)));
            cells.add(cell);
         }
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("rowCount", layout.getRowCount());
      out.put("colCount", layout.getColCount());
      out.put("cells", cells);
      return out;
   }

   /** One cell's binding, in the token vocabulary. */
   public Map<String, Object> readCell(String sessionToken, Principal user, String assemblyName,
                                       int row, int col)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
      requireInGrid(layoutOf(assembly), row, col);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("row", row);
      out.put("col", col);
      out.put("binding",
              CalcCellVocabulary.describe(layoutService.getCellBindingInfo(assembly, row, col)));
      return out;
   }

   /**
    * The script StyleBI evaluates for a cell.
    *
    * <p>Goes through {@code sessions.read} rather than {@code resolve} because
    * {@code VSTableLayoutService.getCellScript} returns {@code Void} and delivers the script by
    * <em>dispatching</em> a {@code GetCellScriptCommand}. A plain resolve has no dispatcher to
    * capture it, and a mutate would open an undo checkpoint for a read.
    */
   public Map<String, Object> cellScript(String sessionToken, Principal user, String assemblyName,
                                         int row, int col)
      throws Exception
   {
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
         requireInGrid(layoutOf(assembly), row, col);

         GetCellScriptEvent event = new GetCellScriptEvent();
         event.setName(assemblyName);
         event.setRow(row);
         event.setCol(col);
         layoutService.getCellScript(runtimeId, event, user, dispatcher);

         String script = null;

         for(CapturingCommandDispatcher.Command command : dispatcher.getCapturedCommands()) {
            if(command.getCommand() instanceof GetCellScriptCommand cellScript) {
               script = cellScript.getScript();
               break;
            }
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("assembly", assemblyName);
         out.put("row", row);
         out.put("col", col);
         out.put("script", script);

         if(script == null || script.isBlank()) {
            out.put("note",
                    "This cell has no script. A cell's own binding — content, grouping, expand — " +
                    "is read with get_cell_binding; a script is the optional expression layered " +
                    "on top of it.");
         }

         return out;
      });
   }

   /**
    * The predefined named groups a column offers, for a cell's {@code namedGroup}.
    *
    * <p>Works for any {@code DataVSAssembly} -- chart, crosstab, table, or calc table -- not
    * just calc tables. Named groups are a property of the assembly's bound source/column, so
    * discovery isn't limited to the assembly kind that happens to consume them by cell.
    *
    * <p>Same command-dispatch shape as {@link #cellScript}: {@code getNamedGroup} answers by
    * dispatching a {@code GetPredefinedNamedGroupCommand}.
    */
   public Map<String, Object> namedGroups(String sessionToken, Principal user,
                                          String assemblyName, String column)
      throws Exception
   {
      if(column == null || column.isBlank()) {
         throw new IllegalArgumentException(
            "list_named_groups requires 'column' — named groups are defined per column. " +
            "get_calc_layout reports which columns a cell is bound to.");
      }

      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         DataVSAssembly assembly = requireDataAssembly(rvs, assemblyName);

         // The command carries the group NAMES only — its constructor takes
         // AssetNamedGroupInfo[] but keeps just getName() from each. The members are not on
         // the wire, so this reports what StyleBI actually sends rather than inventing a shape.
         List<String> groups = new ArrayList<>(
            predefinedNamedGroupNames(runtimeId, user, dispatcher, assemblyName, column));

         // The above only sees repository-registered "predefined named group" assets
         // (SummaryAttr.getAssetNamedGroupInfos), a different kind from the plain
         // DefaultNamedGroupAssembly that add_named_group creates as an ordinary secondary
         // assembly inside the calc table's own bound worksheet. Scan that worksheet too, so
         // a group created through add_named_group is actually listed.
         Set<String> seen = new LinkedHashSet<>(groups);
         List<String> worksheetGroups = new ArrayList<>();

         for(DefaultNamedGroupAssembly ngAssembly : worksheetNamedGroups(rvs, assembly, column)) {
            if(seen.add(ngAssembly.getName())) {
               worksheetGroups.add(ngAssembly.getName());
            }
         }

         groups.addAll(worksheetGroups);

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("assembly", assemblyName);
         out.put("column", column);
         out.put("namedGroups", groups);

         if(!worksheetGroups.isEmpty()) {
            out.put("note",
                    "'" + String.join("', '", worksheetGroups) + "' " +
                    (worksheetGroups.size() == 1 ? "was" : "were") +
                    " created via add_named_group on this column's worksheet. set_cell_binding's " +
                    "'namedGroup' parameter binds it by converting its conditions into an " +
                    "expert named group on the cell's order.");
         }
         else if(groups.isEmpty()) {
            out.put("note",
                    "No predefined named groups exist for this column. Named groups can be " +
                    "defined on the data source, or created here with add_named_group.");
         }

         return out;
      });
   }

   /** Binds one cell. One {@code sessions.mutate}, so one undo checkpoint. */
   public void setCellBinding(String sessionToken, Principal user, String assemblyName,
                              int row, int col, Map<String, Object> binding)
      throws Exception
   {
      // Validated before the runtime is touched: an incomplete binding costs nothing to
      // reject here, and opens no checkpoint the caller then has to undo.
      CalcCellVocabulary.validate(binding);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
         requireInGrid(layoutOf(assembly), row, col);

         CellBindingInfo cellBindingInfo = toCellBindingInfo(binding);

         if(cellBindingInfo.getType() == CellBinding.BIND_COLUMN) {
            requireBindableColumn(runtimeId, user, assemblyName, cellBindingInfo.getValue());
         }

         String namedGroup = cellBindingInfo.getType() == CellBinding.BIND_COLUMN
            ? namedGroupOf(binding.get("field")) : null;

         if(namedGroup != null) {
            cellBindingInfo.setOrder(resolveNamedGroupOrder(
               rvs, assembly, runtimeId, user, dispatcher, cellBindingInfo.getValue(),
               namedGroup));
         }

         SetCellBindingEvent event = new SetCellBindingEvent();
         event.setName(assemblyName);
         event.setSelectCells(new TableCell[]{ cellAt(row, col) });
         event.setBinding(cellBindingInfo);
         layoutService.setCellBinding(runtimeId, event, user, dispatcher);
      });
   }

   /**
    * Checks that a column cell's {@code field.column} is actually reachable from the calc
    * table's current {@code set_table_source} target, the same check
    * {@code BindingAgentController.resolveSourceTable} already makes for chart/table/crosstab
    * writes via {@link BindableColumns#require}. Without it, a column that is a real column —
    * just on a different table than this assembly's source — bound cleanly and rendered a
    * silently blank cell (CLAUDE.md's tool-misuse-accepted-silently class).
    *
    * <p>A listing failure is logged and skipped rather than propagated, matching
    * {@code resolveSourceTable}'s own reasoning: not being able to read the tree is a different
    * fault than a bad column, and refusing the write on the strength of it would turn a read
    * problem into a write problem.
    */
   private void requireBindableColumn(String runtimeId, Principal user, String assemblyName,
                                      String column)
   {
      try {
         List<BindableTable> tables = fieldsService.list(runtimeId, assemblyName, user);
         BindableColumns.require(tables, assemblyName, new FieldRef(column, null, null, null, null));
      }
      catch(IllegalArgumentException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.debug("Could not list bindable columns for {}; skipping the check", assemblyName, e);
      }
   }

   /**
    * The predefined-named-group names a column offers, straight off the dispatched command —
    * the same lookup {@link #namedGroups} reports and {@link #resolveNamedGroupOrder} validates
    * an asset name against.
    */
   private List<String> predefinedNamedGroupNames(String runtimeId, Principal user,
                                                   CapturingCommandDispatcher dispatcher,
                                                   String assemblyName, String column)
      throws Exception
   {
      GetPredefinedNamedGroupEvent event = new GetPredefinedNamedGroupEvent();
      event.setName(assemblyName);
      event.setColumn(column);
      layoutService.getNamedGroup(runtimeId, event, user, dispatcher);

      List<String> groups = new ArrayList<>();

      for(CapturingCommandDispatcher.Command command : dispatcher.getCapturedCommands()) {
         if(command.getCommand() instanceof GetPredefinedNamedGroupCommand named &&
            named.getNamedGroups() != null)
         {
            groups.addAll(List.of(named.getNamedGroups()));
         }
      }

      return groups;
   }

   /**
    * The worksheet-local {@code DefaultNamedGroupAssembly}(s) {@code add_named_group} created
    * on this column, attached to the assembly's own source -- a different kind from the
    * repository-registered "predefined named group" assets {@link #predefinedNamedGroupNames}
    * sees. Takes any {@code DataVSAssembly} (chart, crosstab, table, calc table), not just a
    * calc table -- named groups are a property of the bound source/column, not of calc cells.
    */
   private List<DefaultNamedGroupAssembly> worksheetNamedGroups(RuntimeViewsheet rvs,
                                                                DataVSAssembly assembly,
                                                                String column)
   {
      SourceInfo sinfo = assembly.getSourceInfo();
      Worksheet ws = rvs.getViewsheet() == null ? null : rvs.getViewsheet().getBaseWorksheet();

      return WorksheetNamedGroupMatcher.worksheetNamedGroups(ws, sinfo, column);
   }

   /**
    * Resolves a cell's {@code field.namedGroup} to the {@code OrderModel}
    * {@code VSTableLayoutService.setNamedGroupInfo} actually knows how to apply -- worksheet-local
    * first (an {@code EXPERT_NAMEDGROUP_INFO} built from the assembly's own per-group
    * conditions), then the repository-registered asset kind. Neither matching is a hard failure:
    * a name that resolves to nothing would otherwise silently render without any grouping at
    * all, which is the defect this fixes.
    */
   private OrderModel resolveNamedGroupOrder(RuntimeViewsheet rvs, CalcTableVSAssembly assembly,
                                             String runtimeId, Principal user,
                                             CapturingCommandDispatcher dispatcher,
                                             String column, String namedGroup)
      throws Exception
   {
      for(DefaultNamedGroupAssembly ngAssembly : worksheetNamedGroups(rvs, assembly, column)) {
         if(namedGroup.equals(ngAssembly.getName())) {
            return worksheetLocalOrder(ngAssembly);
         }
      }

      List<String> registered = predefinedNamedGroupNames(
         runtimeId, user, dispatcher, assembly.getAbsoluteName(), column);

      if(registered.contains(namedGroup)) {
         NamedGroupInfoModel ngInfoModel = new NamedGroupInfoModel();
         ngInfoModel.setType(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO);
         ngInfoModel.setName(namedGroup);
         OrderModel orderModel = new OrderModel();
         orderModel.setType(XConstants.SORT_SPECIFIC);
         orderModel.setInfo(ngInfoModel);
         return orderModel;
      }

      throw new IllegalArgumentException(
         "'" + namedGroup + "' is not a named group on column '" + column + "' -- it matches " +
         "neither a worksheet-local group created by add_named_group nor a repository-" +
         "registered predefined named group. list_named_groups reports what is available.");
   }

   /**
    * Converts a worksheet-local {@code DefaultNamedGroupAssembly}'s per-group conditions into
    * the {@code EXPERT_NAMEDGROUP_INFO} shape {@code VSTableLayoutService.setNamedGroupInfo}
    * consumes.
    *
    * <p>This cannot reuse {@code NamedGroupInfoModel.fixNamedGroupInfoModel} -- that method skips
    * anything whose {@code getType()} isn't {@code EXPERT}/{@code SIMPLE}, and a worksheet-local
    * assembly's {@code NamedGroupInfo.getType()} hard-codes {@code ASSET_NAMEDGROUP_INFO} even
    * though it holds inline per-group {@code ConditionList}s. The conversion itself is the same
    * technique that method uses.
    *
    * <p>{@code SORT_SPECIFIC} on the order is not optional decoration: {@code OrderInfo.isSpecific()}
    * -- which reads this exact bit -- gates whether {@code OrderInfo.createSortOrder} ever folds
    * the named-group conditions into the {@code SortOrder} StyleBI actually groups by. Without
    * it the named group is attached but never takes effect.
    */
   private OrderModel worksheetLocalOrder(DefaultNamedGroupAssembly ngAssembly) throws Exception {
      NamedGroupInfoModel ngInfoModel = new NamedGroupInfoModel();
      ngInfoModel.setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);

      for(String group : ngAssembly.getNamedGroupInfo().getGroups(false)) {
         Object[] conditions = ConditionUtil.fromConditionListToModel(
            ngAssembly.getNamedGroupInfo().getGroupCondition(group), refModelService);
         ConditionExpression conditionExpression = new ConditionExpression();
         conditionExpression.setName(group);
         conditionExpression.setList(conditions);
         ngInfoModel.addCondition(conditionExpression);
      }

      OrderModel orderModel = new OrderModel();
      orderModel.setType(XConstants.SORT_SPECIFIC);
      orderModel.setInfo(ngInfoModel);
      return orderModel;
   }

   /**
    * Layout operations. Op names are taken verbatim from {@code TableLayoutHandler} rather than
    * renamed, so the tool surface and the Composer's own log messages agree.
    *
    * <p><b>Every one of these shifts coordinates</b> — inserting a row at 2 moves everything
    * below it down by one. So this returns the <i>updated</i> layout, and an agent never has to
    * re-read to stay correct or guess how the grid moved.
    */
   private static final Map<String, String> LAYOUT_OPS = layoutOps();

   private static Map<String, String> layoutOps() {
      Map<String, String> map = new LinkedHashMap<>();
      map.put("insertrow", "insertRow");
      map.put("insert_row", "insertRow");
      map.put("appendrow", "appendRow");
      map.put("append_row", "appendRow");
      map.put("deleterow", "deleteRow");
      map.put("delete_row", "deleteRow");
      map.put("insertcol", "insertCol");
      map.put("insert_col", "insertCol");
      map.put("appendcol", "appendCol");
      map.put("append_col", "appendCol");
      map.put("deletecol", "deleteCol");
      map.put("delete_col", "deleteCol");
      map.put("mergecells", "mergeCells");
      map.put("merge_cells", "mergeCells");
      map.put("splitcells", "splitCells");
      map.put("split_cells", "splitCells");
      return Collections.unmodifiableMap(map);
   }

   /**
    * Applies a layout operation and returns the layout it produced.
    *
    * @param rows how many rows/columns the selection spans; merge needs more than one cell
    * @param n    how many rows/columns to insert or delete
    */
   public Map<String, Object> modifyLayout(String sessionToken, Principal user,
                                           String assemblyName, String op, int row, int col,
                                           Integer rows, Integer cols, Integer n)
      throws Exception
   {
      String resolved = requireOp(op);
      int spanRows = rows == null ? 1 : rows;
      int spanCols = cols == null ? 1 : cols;
      int count = n == null ? 1 : n;

      if(count < 1) {
         throw new IllegalArgumentException("'n' must be at least 1, got " + count + ".");
      }

      // Both of these are no-ops in the handler that would otherwise report success.
      if("mergeCells".equals(resolved) && spanRows <= 1 && spanCols <= 1) {
         throw new IllegalArgumentException(
            "mergeCells needs a selection spanning more than one cell — pass 'rows' and/or " +
            "'cols'. Merging a single cell does nothing and would report success.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
         TableLayout layout = layoutOf(assembly);
         requireInGrid(layout, row, col);

         if("splitCells".equals(resolved)) {
            Dimension span = layout.getSpan(row, col);

            if(span == null || (span.width <= 1 && span.height <= 1)) {
               throw new IllegalArgumentException(
                  "Cell [" + row + "," + col + "] is not merged, so splitCells does nothing and " +
                  "would report success.");
            }
         }

         ModifyTableLayoutEvent event = new ModifyTableLayoutEvent();
         event.setName(assemblyName);
         event.setOp(resolved);
         event.setNum(count);
         event.setSelection(new Rectangle(col, row, spanCols, spanRows));
         layoutService.modifyLayout(runtimeId, event, user, dispatcher);
      });

      // Returned rather than left to the caller: coordinates read before this call are stale.
      Map<String, Object> updated = readLayout(sessionToken, user, assemblyName);
      updated.put("note", "Coordinates read before this operation are stale — use this layout.");
      return updated;
   }

   /**
    * Copies, cuts or removes a cell range.
    *
    * @param target where a copy or cut lands; unused by {@code remove}
    */
   public Map<String, Object> copyCells(String sessionToken, Principal user, String assemblyName,
                                        String op, Rectangle source, Rectangle target)
      throws Exception
   {
      String resolved = switch(op == null ? "" : op.trim().toLowerCase()) {
         case "copy" -> "copy";
         case "cut" -> "cut";
         case "remove" -> "remove";
         default -> throw new IllegalArgumentException(
            "'op' must be copy, cut or remove, got '" + op + "'.");
      };

      if(!"remove".equals(resolved) && target == null) {
         throw new IllegalArgumentException(
            "'" + resolved + "' needs a target — the cell the range lands on. Without one there " +
            "is nowhere to paste and the operation would do nothing.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CalcTableVSAssembly assembly = requireCalcTable(rvs, assemblyName);
         TableLayout layout = layoutOf(assembly);
         requireInGrid(layout, source.y, source.x);

         if(target != null) {
            requireInGrid(layout, target.y, target.x);
         }

         CopyCutCalcCellEvent event = new CopyCutCalcCellEvent();
         event.setName(assemblyName);
         event.setOp(resolved);
         event.setSelections(target == null
                                ? new Rectangle[]{ source, source }
                                : new Rectangle[]{ source, target });
         layoutService.copyCut(runtimeId, event, user, dispatcher);
      });

      Map<String, Object> updated = readLayout(sessionToken, user, assemblyName);
      updated.put("note", "Coordinates read before this operation are stale — use this layout.");
      return updated;
   }

   private static String requireOp(String op) {
      String name = op == null ? "" : op.trim().toLowerCase();
      String resolved = LAYOUT_OPS.get(name);

      if(resolved == null) {
         throw new IllegalArgumentException(
            "Unknown layout op '" + op + "'. Valid ops: " +
            new TreeSet<>(LAYOUT_OPS.values()) + ".");
      }

      return resolved;
   }

   /** The tokens this build accepts, so an agent can discover rather than guess. */
   public Map<String, Object> vocabulary() {
      return Map.of(
         "content", CalcCellVocabulary.contentTokens(),
         "grouping", CalcCellVocabulary.groupingTokens(),
         "expand", CalcCellVocabulary.expandTokens(),
         "layoutOps", new TreeSet<>(LAYOUT_OPS.values()),
         "copyOps", List.of("copy", "cut", "remove"));
   }

   // ── conversions ───────────────────────────────────────────────────────────

   static CellBindingInfo toCellBindingInfo(Map<String, Object> binding) {
      CellBindingInfo info = new CellBindingInfo();
      int type = CalcCellVocabulary.content(str(binding, "content"));
      info.setType(type);

      String name = str(binding, "name");

      if(name != null) {
         info.setName(name);
      }

      if(binding.get("grouping") != null) {
         info.setBtype(CalcCellVocabulary.grouping(str(binding, "grouping")));
      }

      if(binding.get("expand") != null) {
         info.setExpansion(CalcCellVocabulary.expand(str(binding, "expand")));
      }

      if(type == CellBinding.BIND_COLUMN) {
         info.setValue(columnOf(binding.get("field")));
         // A summary cell is an aggregate, and an aggregate carries a formula. Binding a field
         // requires content "column", so this branch has to accept one too — without it StyleBI
         // threw `Cannot read field "formula" because "finfo" is null` from
         // TableLayoutHandler.createAggregateField, making a summary column cell impossible.
         String formula = str(binding, "formula");

         if(CalcCellVocabulary.isSummary(info.getBtype()) &&
            (formula == null || formula.isBlank()))
         {
            throw new IllegalArgumentException(
               "A 'summary' cell aggregates its column, so it needs a 'formula' such as " +
               "Sum, Count, Average, Max or Min. Without one StyleBI fails building the " +
               "aggregate rather than rendering an unaggregated cell.");
         }

         info.setFormula(formula);
      }
      else if(type == CellBinding.BIND_FORMULA) {
         String script = str(binding, "formula");
         info.setValue(script != null ? script : str(binding, "value"));
         // 'formula' on CellBinding is the aggregate name (Sum/Count/...) StyleBI generates for a
         // BIND_COLUMN summary cell -- see CellBinding.setFormula's own javadoc. It has no meaning
         // for a BIND_FORMULA cell's script and must not carry it; leaving it null here matches
         // what get_cell_binding already reports back for a formula cell today when a script is
         // genuinely absent.
      }
      else {
         info.setValue(str(binding, "value"));
      }

      if(binding.get("mergeCells") instanceof Boolean merge) {
         info.setMergeCells(merge);
      }

      // rowGroup/colGroup aren't part of this seam's cell vocabulary, so they're always
      // null here. null is the deliberate "no ancestor, grand total" sentinel elsewhere in
      // StyleBI (TableCellBinding), which is wrong for a cell created through this seam --
      // it must instead inherit its nearest enclosing expand ancestor by default, the same
      // as a freshly drag-and-dropped cell (see TableLayoutHandler.createDefalutCellBinding).
      String rowGroup = str(binding, "rowGroup");
      info.setRowGroup(rowGroup != null ? rowGroup : TableCellBinding.DEFAULT_GROUP);
      String colGroup = str(binding, "colGroup");
      info.setColGroup(colGroup != null ? colGroup : TableCellBinding.DEFAULT_GROUP);
      info.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
      info.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);
      return info;
   }

   /**
    * A cell's column binding is the column name. The nested field carries the shared
    * vocabulary so it reads the same as everywhere else, and its type is required for the
    * same reason it is required everywhere else.
    */
   private static String columnOf(Object field) {
      if(field instanceof FieldRef ref) {
         FieldRefFactory.requireType(ref);
         return ref.column();
      }

      if(field instanceof Map<?, ?> map) {
         Object column = map.get("column");
         Object type = map.get("type");

         if(column == null || String.valueOf(column).isBlank()) {
            throw new IllegalArgumentException("A cell's 'field' needs a 'column'.");
         }

         FieldRefFactory.requireType(
            new FieldRef(String.valueOf(column), type == null ? null : String.valueOf(type),
                         null, null, null));
         return String.valueOf(column);
      }

      throw new IllegalArgumentException(
         "A cell's 'field' must be an object such as {column: \"Region\", type: \"dimension\"}.");
   }

   /** A field's named-group name, if it carries one. {@code null} means none was given. */
   private static String namedGroupOf(Object field) {
      Object namedGroup = field instanceof FieldRef ref ? ref.namedGroup()
         : field instanceof Map<?, ?> map ? map.get("namedGroup") : null;
      String text = namedGroup == null ? "" : String.valueOf(namedGroup).trim();
      return text.isEmpty() ? null : text;
   }

   private static TableCell cellAt(int row, int col) {
      TableCell cell = new TableCell();
      cell.setRow(row);
      cell.setCol(col);
      return cell;
   }

   // ── guards ────────────────────────────────────────────────────────────────

   private static TableLayout layoutOf(CalcTableVSAssembly assembly) {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      TableLayout layout = info == null ? null : info.getTableLayout();

      if(layout == null) {
         throw new IllegalArgumentException(
            "'" + assembly.getAbsoluteName() + "' has no cell layout yet.");
      }

      return layout;
   }

   private static void requireInGrid(TableLayout layout, int row, int col) {
      if(row < 0 || col < 0 || row >= layout.getRowCount() || col >= layout.getColCount()) {
         throw new IllegalArgumentException(
            "Cell [" + row + "," + col + "] is outside the grid, which is " +
            layout.getRowCount() + " row(s) by " + layout.getColCount() + " column(s). " +
            "Coordinates read before a layout change are stale — re-read the layout.");
      }
   }

   private static DataVSAssembly requireDataAssembly(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof DataVSAssembly data)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", which has no bound source/column to look up named groups on.");
      }

      return data;
   }

   private static CalcTableVSAssembly requireCalcTable(RuntimeViewsheet rvs,
                                                       String assemblyName)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof CalcTableVSAssembly calc)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a calc table. Its binding lives in shelves, not cells — use " +
            "get_table_binding or get_binding instead.");
      }

      return calc;
   }

   private static String str(Map<String, Object> spec, String key) {
      Object value = spec == null ? null : spec.get(key);
      String text = value == null ? "" : String.valueOf(value).trim();
      return text.isEmpty() ? null : text;
   }

   private static final Logger LOG = LoggerFactory.getLogger(CalcTableService.class);

   private final ViewsheetSessionService sessions;
   private final VSTableLayoutService layoutService;
   private final DataRefModelFactoryService refModelService;
   private final BindableFieldsService fieldsService;
}
