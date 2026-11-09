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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SnapshotEmbeddedTableAssembly;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CellRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableServiceProxy;
import inetsoft.web.viewsheet.event.table.ChangeVSTableCellsTextEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Cell edits of a plain (non-Form) Embedded Table assembly: the Viewer's cell input, which sends
 * {@code /events/composer/viewsheet/table/changeCellText} over STOMP and has no agent route of
 * its own. This bridges a BATCH of such edits through
 * {@link ComposerVSTableServiceProxy#changeCellText} the way {@link FormTableRowService} bridges
 * {@code VSFormTableService}.
 *
 * <p><b>Every refusal happens before {@code mutate()} is called.</b> {@code mutate} opens an undo
 * checkpoint and broadcasts even when the mutation fails, and the native {@code changeCellText}
 * loop can fail halfway (an out-of-range column is a ClassCastException/NPE after the earlier
 * changes already wrote). So the whole batch is resolved and validated first, and a refused call
 * has written nothing. The plan is built a second time inside the mutation, against the runtime
 * the write actually lands on.
 *
 * <p>What the native path does that this class deliberately does not pass through:
 * <ul>
 *   <li>it stores a row index that does not exist under a dead row key (a silent no-op later
 *       dropped by {@code syncData0}) -- refused here with the valid range;</li>
 *   <li>{@code updateCell} stores text that does not parse for a numeric/date/boolean column as
 *       a STRING -- refused here with {@link WizStrictValueParser};</li>
 *   <li>it has no Form guard, and a Form-enabled table is also an
 *       {@code EmbeddedTableVSAssembly} -- refused here, pointing at
 *       {@code form_table_set_cell};</li>
 *   <li>it NPEs on a null text, and maps the empty string to a cleared cell for every column type
 *       (a string column cannot hold "" through it) -- so a null/omitted value is sent as "" to
 *       clear, and "" in a string column therefore also clears.</li>
 * </ul>
 *
 * <p>The edits are the assembly's runtime state ({@code stateDataMap}); they are written into the
 * viewsheet XML on save, never into the worksheet's embedded table. This class makes no claim
 * about how they interact with a later refresh, selection write or undo.
 */
@Service
public class EmbeddedTableCellService {
   public EmbeddedTableCellService(ViewsheetSessionService sessions,
                                   ComposerVSTableServiceProxy composerTableService)
   {
      this.sessions = sessions;
      this.composerTableService = composerTableService;
   }

   /** One requested edit; {@code row}/{@code col} are nullable so a missing one is named. */
   public record CellChange(Integer row, Integer col, String value) {
   }

   public Map<String, Object> setCells(String sessionToken, Principal user, String assemblyName,
                                       List<CellChange> changes, String linkUri) throws Exception
   {
      Map<String, Object> result = new LinkedHashMap<>();

      // Refuse before mutate(): a refused call inside it still costs an undo step and a
      // broadcast. Checked again inside the mutation in case the table changed in between.
      plan(sessions.resolve(sessionToken, user), assemblyName, changes);

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Plan plan = plan(rvs, assemblyName, changes);

         ChangeVSTableCellsTextEvent.TableCellTextChange[] nativeChanges =
            new ChangeVSTableCellsTextEvent.TableCellTextChange[plan.cells().size()];

         for(int i = 0; i < nativeChanges.length; i++) {
            Cell cell = plan.cells().get(i);
            ChangeVSTableCellsTextEvent.TableCellTextChange change =
               new ChangeVSTableCellsTextEvent.TableCellTextChange();
            change.setRow(cell.row());
            change.setCol(cell.col());
            change.setText(cell.nativeText());
            nativeChanges[i] = change;
         }

         ChangeVSTableCellsTextEvent event = new ChangeVSTableCellsTextEvent.Builder()
            .assemblyName(assemblyName)
            .changes(nativeChanges)
            .build();
         composerTableService.changeCellText(runtimeId, event, user, linkUri, dispatcher);

         List<Map<String, Object>> echo = new ArrayList<>();

         for(Cell cell : plan.cells()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("row", cell.row());
            entry.put("col", cell.col());
            entry.put("column", cell.column());
            entry.put("previous", cell.previous());
            entry.put("value", cell.requested());
            echo.add(entry);
         }

         result.put("applied", echo.size());
         result.put("changes", echo);
         result.put("submitOnChange", plan.submitOnChange());
      });

      result.put("assembly", assemblyName);
      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   /** Resolves and validates the whole batch; touches nothing. */
   private static Plan plan(RuntimeViewsheet rvs, String assemblyName, List<CellChange> changes) {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required.");
      }

      if(changes == null || changes.isEmpty()) {
         throw new IllegalArgumentException(
            "'changes' is required and must name at least one {row, col, value} edit.");
      }

      EmbeddedTableVSAssembly table = requireEmbeddedTable(rvs, assemblyName);
      EmbeddedTableVSAssemblyInfo info = (EmbeddedTableVSAssemblyInfo) table.getVSAssemblyInfo();
      EmbeddedTableAssembly source = requireSource(table, assemblyName);

      ColumnSelection visible = table.getVisibleColumns();
      int cols = visible == null ? 0 : visible.getAttributeCount();

      if(cols == 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no visible columns, so there is no cell to edit.");
      }

      XEmbeddedTable original = source.getOriginalEmbeddedData();
      XEmbeddedTable data = source.getEmbeddedData();
      int dataRows = original == null ? 0 : Math.max(0, original.getRowCount() - HEADER_ROWS);
      ColumnSelection sourceColumns = source.getColumnSelection(false);
      Map<CellRef, Object> dmap = table.getStateDataMap();
      Set<List<Integer>> seen = new HashSet<>();
      List<Cell> cells = new ArrayList<>(changes.size());

      for(int i = 0; i < changes.size(); i++) {
         CellChange change = changes.get(i);
         String at = "'changes[" + i + "]'";

         if(change == null) {
            throw new IllegalArgumentException(at + " is empty; expected {row, col, value}.");
         }

         if(change.row() == null || change.col() == null) {
            throw new IllegalArgumentException(
               at + " needs both 'row' (0-based data row) and 'col' (0-based visible column).");
         }

         int row = change.row();
         int col = change.col();

         if(row < 0 || row > dataRows - 1) {
            throw new IllegalArgumentException(
               at + " 'row' " + row + " is out of range for '" + assemblyName +
               "': valid range " + (dataRows > 0 ? "is 0.." + (dataRows - 1) : "is empty") +
               " (" + dataRows + " data row(s)).");
         }

         if(col < 0 || col > cols - 1) {
            throw new IllegalArgumentException(
               at + " 'col' " + col + " is out of range for '" + assemblyName +
               "': valid range is 0.." + (cols - 1) + " (" + cols + " visible column(s)).");
         }

         if(!seen.add(List.of(row, col))) {
            throw new IllegalArgumentException(
               at + " repeats row " + row + ", col " + col +
               "; each cell may be named only once per call.");
         }

         ColumnRef ref = (ColumnRef) visible.getAttribute(col);
         String key = ref.getDataRef().getName();
         String column = ref.getAlias() != null && !ref.getAlias().isEmpty() ?
            ref.getAlias() : ref.getAttribute();
         DataRef sourceRef = sourceColumns == null ? null : sourceColumns.getAttribute(key);
         int dataCol = sourceRef == null || data == null ? -1 :
            AssetUtil.findColumn(data, sourceRef);

         if(dataCol < 0 || dataCol >= original.getColCount()) {
            throw new IllegalArgumentException(
               at + " column '" + column + "' (col " + col + ") of '" + assemblyName +
               "' is not backed by a data column of its worksheet table, so it cannot be edited.");
         }

         String nativeText = nativeText(at, assemblyName, row, col, column, ref.getDataType(),
                                        change.value());
         CellRef cellRef = new CellRef(key, row + HEADER_ROWS);
         Object old;

         synchronized(dmap) {
            old = dmap.containsKey(cellRef) ? dmap.get(cellRef) :
               original.getObject(row + HEADER_ROWS, dataCol);
         }

         String requested = nativeText.isEmpty() ? null : nativeText;
         cells.add(new Cell(row, col, column, nativeText, old == null ? null :
                            Tool.getDataString(old), requested));
      }

      return new Plan(cells, info.isSubmitOnChange());
   }

   private static EmbeddedTableVSAssembly requireEmbeddedTable(RuntimeViewsheet rvs,
                                                               String assemblyName)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof EmbeddedTableVSAssembly table)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not an Embedded Table assembly.");
      }

      if(((EmbeddedTableVSAssemblyInfo) table.getVSAssemblyInfo()).isForm()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a Form-enabled table; its cells are edited with " +
            "form_table_set_cell (and committed with form_table_apply), not with this tool.");
      }

      return table;
   }

   private static EmbeddedTableAssembly requireSource(EmbeddedTableVSAssembly table,
                                                      String assemblyName)
   {
      Worksheet ws = table.getWorksheet();
      String name = table.getTableName();
      Assembly source = ws == null || name == null ? null : ws.getAssembly(name);

      if(!(source instanceof EmbeddedTableAssembly embedded) ||
         source instanceof SnapshotEmbeddedTableAssembly)
      {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no editable source: its worksheet table '" + name +
            "' is " + (source == null ? "missing" :
               source instanceof SnapshotEmbeddedTableAssembly ? "a snapshot embedded table" :
               "not an embedded table") + ", so a typed cell value would never be applied.");
      }

      return embedded;
   }

   /**
    * The text handed to {@code changeCellText} for {@code value}. A null value, and "" for a
    * string/char column, clear the cell (sent as "", the one text the native code reads as a
    * clear). Numeric/date/boolean text is parsed strictly against the column's data type and
    * forwarded in canonical form.
    */
   static String nativeText(String at, String assemblyName, int row, int col, String column,
                            String dataType, String value)
   {
      if(value == null) {
         return "";
      }

      String type = dataType == null || dataType.isBlank() ? XSchema.STRING : dataType.trim();

      if(!isStrictType(type)) {
         return value;
      }

      String where = at + " row " + row + ", col " + col + " ('" + column + "' of '" +
         assemblyName + "', " + type + " column): ";

      if(value.isEmpty()) {
         throw new IllegalArgumentException(
            where + "an empty string is not a " + type + " value. Send null (or omit 'value') " +
            "to clear the cell.");
      }

      if(Tool.FAKE_NULL.equals(value)) {
         throw new IllegalArgumentException(
            where + "'" + value + "' is not a " + type + " value. Send null (or omit 'value') " +
            "to clear the cell.");
      }

      Object parsed;

      try {
         parsed = WizStrictValueParser.parse(value, type);
      }
      catch(IllegalArgumentException ex) {
         throw new IllegalArgumentException(where + ex.getMessage(), ex);
      }

      if(XSchema.DATE.equalsIgnoreCase(type) || XSchema.TIME_INSTANT.equalsIgnoreCase(type) ||
         XSchema.TIME.equalsIgnoreCase(type))
      {
         return WizStrictValueParser.canonicalTemporalText(value);
      }

      if(parsed instanceof Double || parsed instanceof Float) {
         return value.trim();
      }

      return parsed == null ? value : String.valueOf(parsed);
   }

   /** Every type but string/char (which take any text) and one the parser does not know. */
   private static boolean isStrictType(String type) {
      return STRICT_TYPES.stream().anyMatch(type::equalsIgnoreCase);
   }

   private record Cell(int row, int col, String column, String nativeText, String previous,
                       String requested)
   {
   }

   private record Plan(List<Cell> cells, boolean submitOnChange) {
   }

   /** The embedded data's header row; {@code changeCellText}'s own row key is data row + 1. */
   private static final int HEADER_ROWS = 1;
   private static final List<String> STRICT_TYPES = List.of(
      XSchema.BOOLEAN, XSchema.INTEGER, XSchema.SHORT, XSchema.BYTE, XSchema.LONG,
      XSchema.DOUBLE, XSchema.FLOAT, XSchema.DATE, XSchema.TIME, XSchema.TIME_INSTANT);

   private final ViewsheetSessionService sessions;
   private final ComposerVSTableServiceProxy composerTableService;
}
