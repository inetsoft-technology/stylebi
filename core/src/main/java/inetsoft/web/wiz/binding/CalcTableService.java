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
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.event.CopyCutCalcCellEvent;
import inetsoft.web.binding.event.ModifyTableLayoutEvent;
import inetsoft.web.binding.event.SetCellBindingEvent;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.TableCell;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
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
   public CalcTableService(ViewsheetSessionService sessions, VSTableLayoutService layoutService) {
      this.sessions = sessions;
      this.layoutService = layoutService;
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

         SetCellBindingEvent event = new SetCellBindingEvent();
         event.setName(assemblyName);
         event.setSelectCells(new TableCell[]{ cellAt(row, col) });
         event.setBinding(toCellBindingInfo(binding));
         layoutService.setCellBinding(runtimeId, event, user, dispatcher);
      });
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

      if(binding.get("grouping") != null) {
         info.setBtype(CalcCellVocabulary.grouping(str(binding, "grouping")));
      }

      if(binding.get("expand") != null) {
         info.setExpansion(CalcCellVocabulary.expand(str(binding, "expand")));
      }

      if(type == CellBinding.BIND_COLUMN) {
         info.setValue(columnOf(binding.get("field")));
      }
      else if(type == CellBinding.BIND_FORMULA) {
         info.setFormula(str(binding, "formula"));
         info.setValue(str(binding, "value"));
      }
      else {
         info.setValue(str(binding, "value"));
      }

      if(binding.get("mergeCells") instanceof Boolean merge) {
         info.setMergeCells(merge);
      }

      info.setRowGroup(str(binding, "rowGroup"));
      info.setColGroup(str(binding, "colGroup"));
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

   private final ViewsheetSessionService sessions;
   private final VSTableLayoutService layoutService;
}
