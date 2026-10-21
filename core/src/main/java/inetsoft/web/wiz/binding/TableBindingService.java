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

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.ClearTableHeaderAliasHandler;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CalcTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.wiz.binding.model.ColumnLabelEntry;
import inetsoft.web.wiz.binding.model.ColumnVisibilityEntry;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.TableLensGuard;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

/**
 * Crosstab and table shelf mutations.
 *
 * <p>Unlike charts, tables have no dedicated write endpoint: they go through the generic
 * {@code setbinding}, which takes the whole polymorphic {@code BindingModel}. That endpoint
 * carries a trap flag, defaulting on, so a binding that would produce a cartesian result is
 * reported rather than quietly applied. It is deliberately not disabled to make a call
 * succeed.
 *
 * <p>Each public method is exactly one {@code sessions.mutate} — one undo checkpoint. That is
 * why {@code moveField} exists rather than asking callers to remove then add: a crosstab pivot
 * as two calls would be two checkpoints, with an intermediate state the browser renders.
 */
@Service
public class TableBindingService {
   @Autowired
   public TableBindingService(ViewsheetSessionService sessions,
                              VSBindingService binding,
                              VSBindingModelService bindingModelService,
                              DataRefModelFactoryService refModelService,
                              HideColumnsDialogService hideColumnsService,
                              VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.bindingModelService = bindingModelService;
      this.refModelService = refModelService;
      this.hideColumnsService = hideColumnsService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   /**
    * @param sourceTable the table to point the assembly at as part of this write, or {@code null}
    *                    to leave its source alone. A crosstab/table with no source renders nothing
    *                    however correctly its shelves are filled in — see {@link #setSource} — and
    *                    this establishes it the same way {@code ChartBindingService.setShelf} does
    *                    for a chart, one call rather than two.
    */
   public List<String> setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields, String sourceTable) throws Exception
   {
      return sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         applySource(model, sourceTable);
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs.getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = BindingSources.effective(
            assembly instanceof DataVSAssembly data ? data.getSourceInfo() : null,
            model.getSource());
         requireSourceForFieldWrite(assemblyName, shelf, model.getSource(),
                                    fields == null ? 0 : fields.size());
         String livePercentageBy = livePercentageByValue(assembly);
         TableBindingMutator.drainNotes();
         TableBindingMutator.setShelf(model, shelf, fields, rvs, source, refModelService);
         relayUnhiddenNotes(assemblyName, assembly, shelf, fields, dispatcher);
         TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);
         relayNotes(assemblyName, dispatcher);
         relayShapeWarning(assemblyName, model, dispatcher);

         Map<String, ColumnOption> formOptions = captureFormOptions(assembly);
         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
         restoreFormRefInvariant(assembly, shelf, formOptions);
      });
   }

   /**
    * A Form-enabled Table's {@code ColumnSelection} must stay entirely {@link FormRef} --
    * {@code VSFormTableService.changeFormInput}/{@code addRow} cast every bound column to
    * {@code FormRef} unconditionally once {@code TableVSAssemblyInfo.isForm()} is true, with no
    * fallback for a plain {@code ColumnRef}. Two native paths already maintain that invariant:
    * {@code TableViewPropertyDialogService.apply} converts every existing column the moment
    * {@code form} flips off-to-on, and {@code VSTableBindingHandler.addColumns} (the Composer's
    * own drag-and-drop-onto-table path) wraps each newly dropped column the same way. This wiz
    * endpoint is a third write path -- the generic {@code TableBindingModel}/details-shelf apply
    * via {@link VSTableBindingFactory#updateTableAssembly} -- that does neither: it only
    * PRESERVES {@code FormRef}-ness an incoming field already has (copying the old column's
    * {@code ColumnOption} across), it never ESTABLISHES it, and assumes without checking that
    * every same-named existing column is already a {@code FormRef}.
    *
    * <p>Confirmed live (bug #77042 follow-up): binding a field to an already form-enabled table
    * via {@code set_table_fields}/{@code add_table_field} left it a plain {@code ColumnRef} --
    * the table rendered fine, {@code get_assembly_properties} read back {@code form: true}, and
    * nothing about the write was reported as a problem -- until the first {@code
    * form_table_set_cell}/{@code form_table_insert_row} on that column crashed the server with a
    * {@code ClassCastException}/{@code NullPointerException} instead of a named error. Run after
    * every details-shelf write so the invariant holds regardless of call order (enabling Form
    * before or after fields are bound), the same way the two native paths above hold it
    * regardless of which one a human happens to use.
    */
   static void restoreFormRefInvariant(VSAssembly assembly, String shelf,
                                       Map<String, ColumnOption> formOptions)
   {
      String normalizedShelf = shelf == null ? "" : shelf.trim().toLowerCase();

      if(!"details".equals(normalizedShelf) && !"detail".equals(normalizedShelf)) {
         return;
      }

      if(!(assembly instanceof TableVSAssembly)) {
         return;
      }

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(!info.isForm()) {
         return;
      }

      ColumnSelection cols = info.getColumnSelection();
      List<DataRef> refs = new ArrayList<>();
      boolean plain = false;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);
         refs.add(ref);
         plain = plain || ref != null && !(ref instanceof FormRef);
      }

      if(!plain) {
         return;
      }

      // ColumnSelection.setAttribute is a silent no-op when a same-named ref is already present
      // (equality is name-based, class-agnostic), so a ColumnRef can only become a FormRef by
      // rebuilding the selection, as TableViewPropertyDialogService does.
      cols.clear();

      for(int i = 0; i < refs.size(); i++) {
         DataRef ref = refs.get(i);

         if(ref == null) {
            continue;
         }

         if(!(ref instanceof FormRef)) {
            FormRef fref = FormRef.toFormRef(ref);
            ColumnOption saved = formOptions == null ? null : formOptions.get(fref.getName());

            if(saved != null) {
               fref.setOption(saved);
            }

            ref = fref;
         }

         cols.addAttribute(i, ref);
      }
   }

   /**
    * The {@link ColumnOption} of each {@link FormRef} column of a Form-enabled Table, by column
    * name, so a details re-set can carry them across as
    * {@code VSTableBindingFactory.updateTableAssembly} does for the native Composer.
    */
   static Map<String, ColumnOption> captureFormOptions(VSAssembly assembly) {
      Map<String, ColumnOption> options = new HashMap<>();

      if(assembly instanceof TableVSAssembly &&
         ((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).isForm())
      {
         ColumnSelection cols =
            ((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getColumnSelection();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            if(cols.getAttribute(i) instanceof FormRef fref && fref.getOption() != null) {
               options.put(fref.getName(), fref.getOption());
            }
         }
      }

      return options;
   }

   /**
    * Establishes the source, if one was worked out and the model has none.
    *
    * <p>Guarded on the model already being sourceless rather than trusting the caller: a repoint
    * deletes bound fields, so it stays behind {@code set_table_source}'s explicit {@code force} and
    * can never happen as a side effect of binding a field. Mirrors
    * {@code ChartBindingService.applySource}.
    */
   static void applySource(BaseTableBindingModel model, String sourceTable) {
      if(sourceTable != null && model != null && model.getSource() == null) {
         model.setSource(BindingSources.assetSource(sourceTable));
      }
   }

   /**
    * Points a crosstab or table at a source table.
    *
    * <p>An assembly added in the Composer starts with no source. Its shelves can be populated —
    * {@code set_table_fields} reports success — and it renders nothing at all, because shelves
    * with no source have nothing to query. Nothing else here assigns one: the mutators
    * <em>preserve</em> {@code source} through a read-modify-write, which is not the same as
    * being able to set it.
    *
    * <p>Repointing a bound assembly discards every field on its shelves, since the columns
    * belong to the old source. That is refused unless {@code force} is set, rather than done
    * silently on one call.
    */
   public List<String> setSource(String sessionToken, Principal user, String assemblyName,
                         String table, boolean force) throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_table_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this assembly can bind to.");
      }

      return apply(sessionToken, user, assemblyName, model -> {
         String resolved = resolveTable(model, table, assemblyName);

         if(!force) {
            requireNoBoundFields(model, assemblyName, resolved);
         }
         else {
            discardBoundFields(model, resolved);
         }

         // Only type, prefix and source survive the trip back: VSBindingService.updateSourceInfo
         // calls SourceInfo.toSourceAttr, which rebuilds the asset source from exactly those
         // three. Setting them directly rather than through the SourceInfo(uql.SourceInfo)
         // convenience constructor also avoids that constructor's toView() call, which drags in
         // VSUtil for a display string nothing here reads.
         SourceInfo source = new SourceInfo();
         source.setType(inetsoft.uql.asset.SourceInfo.ASSET);
         source.setSource(resolved);
         source.setView(resolved);
         model.setSource(source);
      }, true);
   }

   /** Matches a requested table against what the assembly can actually bind to. */
   private static String resolveTable(BaseTableBindingModel model, String table,
                                      String assemblyName)
   {
      List<BindingModel.SourceTable> tables = model.getTables();
      List<String> names = new ArrayList<>();

      if(tables != null) {
         for(BindingModel.SourceTable candidate : tables) {
            if(candidate.getName() != null) {
               names.add(candidate.getName());

               if(candidate.getName().equalsIgnoreCase(table)) {
                  return candidate.getName();
               }
            }
         }
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' cannot bind to '" + table + "'. Available: " + names + ". " +
         "A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   /**
    * Refuses to discard bound fields. The columns on a shelf belong to the source that was set
    * when they were added, so repointing invalidates all of them.
    */
   private static void requireNoBoundFields(BaseTableBindingModel model, String assemblyName,
                                            String table)
   {
      SourceInfo current = model.getSource();

      if(current != null && table.equalsIgnoreCase(current.getSource())) {
         return;
      }

      // A calc table has no shelves to discard — its binding lives in its cells, which keep
      // referring to their columns by name. Nothing to warn about, and shelvesOf would refuse it.
      if(model instanceof CalcTableBindingModel) {
         return;
      }

      List<String> populated = new ArrayList<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         int count = TableBindingMutator.read(model, shelf).size();

         if(count > 0) {
            populated.add(count + " on " + shelf);
         }
      }

      if(!populated.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' already has fields bound (" + String.join(", ", populated) +
            "). Changing its source would discard them, because those columns belong to the " +
            "old source. Clear the shelves first, or pass force:true to discard them " +
            "deliberately.");
      }
   }

   /**
    * The {@code force:true} counterpart to {@link #requireNoBoundFields}: discards only the
    * fields that no longer resolve in the new source, matching the selective-discard intent
    * documented on the UI's own repoint path ({@code VSAssemblyInfoHandler}: "check the old
    * binding columns when source changed, if cannot found the columns in the source, just
    * remove them"). A field whose column name also exists in the new source is kept — a
    * same-shaped repoint (e.g. a partitioned/monthly table swapped for its sibling) should not
    * discard bindings a human doing the equivalent repoint would keep. Without this at all, a
    * repoint left the old source's field refs sitting on every shelf {@code force} didn't itself
    * touch, and those stale refs were written straight back onto the live assembly's design
    * headers by the factory that follows this mutation — that failure mode (never discarding
    * anything) is guarded against by shelves whose fields never resolve in the new source still
    * being fully cleared here, same as before.
    *
    * <p>Known limitation: a kept field's {@code namedGroup} binding is not preserved, because
    * this path has no {@code RuntimeViewsheet}/{@code DataRefModelFactoryService} context to
    * re-resolve it against (the same limitation {@link TableBindingMutator}'s context-less
    * {@code setShelf} overload already has everywhere else it is used).
    */
   private static void discardBoundFields(BaseTableBindingModel model, String table) {
      SourceInfo current = model.getSource();

      if(current != null && table.equalsIgnoreCase(current.getSource())) {
         return;
      }

      // A calc table has no shelves to discard — see requireNoBoundFields above.
      if(model instanceof CalcTableBindingModel) {
         return;
      }

      List<String> availableColumns = columnsOf(model, table);
      List<BAggregateRefModel> aggregatesBefore = model.getAggregates() == null
         ? List.of() : new ArrayList<>(model.getAggregates());

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         List<FieldRef> bound = TableBindingMutator.read(model, shelf);
         List<FieldRef> stillResolves = new ArrayList<>();

         for(FieldRef field : bound) {
            if(field.column() != null &&
               availableColumns.stream().anyMatch(
                  c -> c.equalsIgnoreCase(field.column()) ||
                       c.equalsIgnoreCase(unqualified(field.column()))))
            {
               stillResolves.add(field);
            }
         }

         if(stillResolves.size() != bound.size()) {
            TableBindingMutator.setShelf(model, shelf, stillResolves);
         }
      }

      // A kept dimension's by-value sort/ranking now survives the discard (bug #77025 R1), so one
      // that named an aggregate this repoint discarded must not be left pointing at it.
      TableBindingMutator.clearDanglingAggregateReferences(model, aggregatesBefore);
   }

   /**
    * The new source table's column names, matching {@link #resolveTable}'s own lookup. Each
    * column contributes both its raw name and, when it is qualified ({@code "table.attribute"}),
    * the unqualified attribute name too -- {@code ColumnSelection} entries for a joined/merged
    * worksheet table (the common case a repoint targets) commonly carry the qualified form. An
    * old bound field's column name can independently be qualified or not (see {@link
    * #unqualified}), so expanding only this side is not sufficient by itself -- but skipping it
    * would still treat every field as unresolved whenever the new source itself is qualified and
    * the old field is not, silently degrading back to discarding everything.
    */
   private static List<String> columnsOf(BaseTableBindingModel model, String table) {
      List<String> names = new ArrayList<>();
      List<BindingModel.SourceTable> tables = model.getTables();

      if(tables != null) {
         for(BindingModel.SourceTable candidate : tables) {
            if(table.equalsIgnoreCase(candidate.getName()) && candidate.getColumns() != null) {
               for(BindingModel.SourceTableColumn column : candidate.getColumns()) {
                  if(column.getName() == null) {
                     continue;
                  }

                  names.add(column.getName());
                  String bare = unqualified(column.getName());

                  if(!bare.equals(column.getName())) {
                     names.add(bare);
                  }
               }
            }
         }
      }

      return names;
   }

   /**
    * The unqualified suffix of a possibly {@code "table.attribute"}-qualified column name, or
    * the name itself when it carries no qualifier. Applied to both the new source's column list
    * and an old bound field's column name in {@link #discardBoundFields}, since either side can
    * independently be qualified or not depending on whether its own source table is a
    * joined/merged worksheet table -- comparing only one side's unqualified form would still
    * miss the {qualified old field, unqualified new source} pairing. Package-private: {@link
    * TableBindingMutator#dataTypeOf} reuses it for the identical problem (a qualified column
    * from a joined/merged table not matching a bare field name).
    */
   static String unqualified(String name) {
      int dot = name.lastIndexOf('.');
      return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
   }

   /** @param sourceTable see {@link #setShelf}. */
   public List<String> addField(String sessionToken, Principal user, String assemblyName, String shelf,
                        FieldRef field, Integer position, String sourceTable) throws Exception
   {
      return applyWithContext(sessionToken, user, assemblyName, shelf,
         field == null ? List.of() : List.of(field),
         (model, rvs, source) -> {
            applySource(model, sourceTable);
            requireSourceForFieldWrite(assemblyName, shelf, model.getSource(),
                                       field == null ? 0 : 1);
            source = BindingSources.effective(source, model.getSource());
            TableVSAssembly table = rvs.getViewsheet().getAssembly(assemblyName)
               instanceof TableVSAssembly t ? t : null;
            List<String> full = table == null || position == null || field == null
               ? null : fullDetailsOrder(table, field.column());

            if(full == null) {
               TableBindingMutator.addField(model, shelf, field, position, rvs, source,
                                            refModelService);
               return;
            }

            if(position < 0 || position > full.size()) {
               boolean readding = isHidden(table, field.column());
               throw new IllegalArgumentException(
                  "Position " + position + " is outside the details shelf, which holds " +
                  (full.size() + (readding ? 1 : 0)) + " field(s), shown and hidden (the " +
                  "order get_table_binding reports)." +
                  (readding ? " Re-adding hidden '" + field.column() + "' moves it, so it " +
                     "does not count as a slot of its own." : "") +
                  " Valid positions are 0 to " + full.size() + ".");
            }

            int shownPosition = 0;

            for(String name : full.subList(0, position)) {
               if(!isHidden(table, name)) {
                  shownPosition++;
               }
            }

            TableBindingMutator.addField(model, shelf, field, shownPosition, rvs, source,
                                         refModelService);
            full.add(position, field.column());
            restampHiddenPositions(table, full);
         });
   }

   /**
    * The Table's column names in the full (shown and hidden) order {@code get_table_binding}
    * reports, minus {@code excluding} when that column is currently hidden (re-adding it shows
    * it, so it is not a slot of the list it is being inserted into); {@code null} when the
    * Table has no hidden column, so the shown-only index already is the full-order index.
    */
   private static List<String> fullDetailsOrder(TableVSAssembly table, String excluding) {
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();

      if(info.getHiddenColumns().getAttributeCount() == 0) {
         return null;
      }

      List<String> names = new ArrayList<>();

      for(DataRef ref : HideColumnsDialogService.getOrderedColumns(info.getColumnSelection(),
                                                                   info.getHiddenColumns()))
      {
         names.add(refKey(ref));
      }

      if(isHidden(table, excluding)) {
         names.removeIf(n -> n.equalsIgnoreCase(excluding));
      }

      return names;
   }

   private static boolean isHidden(TableVSAssembly table, String name) {
      Enumeration<DataRef> refs =
         ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getHiddenColumns().getAttributes();

      while(refs.hasMoreElements()) {
         String key = refKey(refs.nextElement());

         if(name != null && key.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Re-stamps every hidden column's recorded position as its index in {@code full}, the order
    * the Table will have once the write that produced it is applied, so a later show lands the
    * column in the same slot relative to its neighbours.
    */
   private static void restampHiddenPositions(TableVSAssembly table, List<String> full) {
      Enumeration<DataRef> refs =
         ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getHiddenColumns().getAttributes();

      while(refs.hasMoreElements()) {
         DataRef ref = refs.nextElement();
         int index = full.indexOf(refKey(ref));

         if(index >= 0 && ref instanceof ColumnRef column) {
            column.setHiddenPosition(index);
         }
      }
   }

   /**
    * Refuses a non-empty shelf write to an assembly with no source: {@link
    * TableBindingMutator}'s shelf builders have no source-conditioned branch, so the write would
    * be applied and reported as success, then render nothing because there is no source to
    * query. Scoped to callers that add fields to a shelf ({@link #setShelf}/{@link #addField});
    * {@link #removeField}/{@link #moveField} must not call this — a sourceless assembly can never
    * have anything on its shelves to remove or move in the first place, once this guard is in
    * place on the calls that put fields there.
    *
    * <p>Checked against the model's own source, taken after {@link #applySource} has had a
    * chance to establish one from the caller's {@code sourceTable} — that is what {@code
    * setShelf}/{@code addField} are about to write back, whereas the assembly's own {@code
    * SourceInfo} (used elsewhere in this method for named-group resolution) does not reflect
    * this call's mutation until the write is applied at the end of it.
    */
   private static void requireSourceForFieldWrite(String assemblyName, String shelf,
                                                   SourceInfo source, int fieldCount)
   {
      if(source == null && fieldCount > 0) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' has no source table yet, so binding " + fieldCount +
            " field(s) to its '" + shelf + "' shelf would render nothing -- shelves with no " +
            "source have nothing to query. Call set_table_source first.");
      }
   }

   public List<String> removeField(String sessionToken, Principal user, String assemblyName,
                           String shelf, String column) throws Exception
   {
      return removeField(sessionToken, user, assemblyName, shelf, column, null);
   }

   /** @param index which occurrence of {@code column} on the shelf, when it is bound more than once. */
   public List<String> removeField(String sessionToken, Principal user, String assemblyName,
                           String shelf, String column, Integer index) throws Exception
   {
      return applyWithContext(sessionToken, user, assemblyName, shelf,
         (model, rvs, source) -> {
            TableVSAssembly table = rvs.getViewsheet().getAssembly(assemblyName)
               instanceof TableVSAssembly t ? t : null;
            List<String> full = table == null ? null : fullDetailsOrder(table, null);

            Integer shownIndex = index;

            if(full != null && index != null && index >= 0 && index < full.size() &&
               full.get(index).equalsIgnoreCase(column) && !isHidden(table, column))
            {
               shownIndex = 0;

               for(String name : full.subList(0, index)) {
                  if(!isHidden(table, name)) {
                     shownIndex++;
                  }
               }
            }

            TableBindingMutator.removeField(model, shelf, column, shownIndex, rvs, source,
                                            refModelService);

            if(full != null && !isHidden(table, column) &&
               full.removeIf(n -> n.equalsIgnoreCase(column)))
            {
               restampHiddenPositions(table, full);
            }
         });
   }

   public List<String> moveField(String sessionToken, Principal user, String assemblyName,
                         String fromShelf, String toShelf, String column, Integer position)
      throws Exception
   {
      return moveField(sessionToken, user, assemblyName, fromShelf, toShelf, column, null,
                       position);
   }

   /** @param fromIndex which occurrence of {@code column} on the source shelf, when it is bound more than once. */
   public List<String> moveField(String sessionToken, Principal user, String assemblyName,
                         String fromShelf, String toShelf, String column, Integer fromIndex,
                         Integer position)
      throws Exception
   {
      return applyWithContext(sessionToken, user, assemblyName, toShelf,
         (model, rvs, source) ->
            TableBindingMutator.moveField(model, fromShelf, toShelf, column, fromIndex, position,
                                          rvs, source, refModelService));
   }

   /**
    * Moves one column between a Table's live {@link TableVSAssemblyInfo#getColumnSelection()}
    * (shown) and {@link TableVSAssemblyInfo#getHiddenColumns()} (hidden but still bound) --
    * "Hide Column" without unbinding the field, unlike {@link #removeField}. Reuses {@link
    * HideColumnsDialogService#setColumnOptionDialogModel}, the same write the Composer's own Hide
    * Columns dialog commits, rather than reimplementing its {@code assemblyInfoHandler.apply}
    * checkpoint/refresh side effects here.
    *
    * <p>Unlike every mutator above, this does not go through {@link BaseTableBindingModel} at
    * all -- {@code hiddenColumns} lives one level down, on the live {@code VSAssemblyInfo}, which
    * the wiz binding model does not model.
    *
    * <p>Crosstab is refused by name rather than silently no-op'd: {@code
    * CrosstabVSAssemblyInfo.hiddenColumns} is keyed by a rendered lens column's {@code
    * TableDataPath} + header occurrence, not by field name, so this single
    * {@code assembly + column} contract does not map onto it. {@link #setCrosstabColumnVisibility}
    * below is the Crosstab-shaped sibling, addressed the same name-first way but resolved down to
    * that occurrence server-side.
    */
   public List<String> setFieldVisibility(String sessionToken, Principal user, String assemblyName,
                                  String column, boolean visible, String linkUri) throws Exception
   {
      return sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(!(liveAssembly instanceof TableVSAssembly)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " +
               (liveAssembly instanceof CrosstabVSAssembly ? "a Crosstab" : "not a Table") +
               " -- set_table_field_visibility only supports Table. Use " +
               "set_crosstab_column_visibility for a Crosstab's row or aggregate occurrence (a " +
               "column dimension has no header of its own and cannot be hidden by name).");
         }

         HideColumnsDialogModel current =
            hideColumnsService.getColumnOptionDialogModel(runtimeId, assemblyName, user);

         if(!current.availableColumns().contains(column) &&
            !current.hiddenColumns().contains(column))
         {
            List<String> bound = new ArrayList<>(current.availableColumns());
            bound.addAll(current.hiddenColumns());
            throw new IllegalArgumentException(
               "'" + column + "' is not bound on '" + assemblyName + "'. It holds: " +
               (bound.isEmpty() ? "(nothing)" : String.join(", ", bound)) +
               ". Add it with add_table_field or set_table_fields first.");
         }

         List<String> newAvailable = new ArrayList<>(current.availableColumns());
         List<String> newHidden = new ArrayList<>(current.hiddenColumns());
         newAvailable.remove(column);
         newHidden.remove(column);
         (visible ? newAvailable : newHidden).add(column);

         HideColumnsDialogModel updated = HideColumnsDialogModel.builder()
            .availableColumns(newAvailable)
            .hiddenColumns(newHidden)
            .build();

         hideColumnsService.setColumnOptionDialogModel(runtimeId, assemblyName, updated, user,
                                                       dispatcher, linkUri);
      });
   }

   /**
    * {@link #setFieldVisibility}'s Crosstab-shaped sibling: hides one or more rendered pivot
    * occurrences by <em>name</em> (matching this plugin's own established name-first addressing
    * convention -- see {@code set_column_labels}/{@code set_column_widths}), or unhides
    * everything at once. There is deliberately no selective per-column show, matching the native
    * Composer's own pivot-header Hide/Show action ({@code ComposerVSTableService.hideColumns}):
    * {@code showColumns()} there always sends an empty column list, and {@code
    * CrosstabVSAssemblyInfo.clearHiddenColumns()} has no other mode.
    *
    * <p>Hide resolves each {@code columns}/{@code entries} item the same way {@link
    * #setColumnLabels} resolves a Crosstab label -- {@link TableBindingMutator#resolveVisibilityTarget}
    * (name/shelf/index -&gt; binding-order shelf position) -&gt; {@link #liveCrosstabRef} (shelf
    * position -&gt; live {@code DataRef}) -&gt; {@link SetTableHeaderAliasHandler#findHeaderCell}
    * (live {@code DataRef} -&gt; rendered lens column index) -&gt; {@code
    * CrosstabVSAssemblyInfo.addHiddenColumn(col, lens)}. This is the same binding-order addressing
    * {@code get_table_binding}'s own shelf arrays already expose, resolved down to the rendered
    * lens's own occurrence-keyed index space the caller has no way to observe directly (see this
    * method's own design note in 03-fix.md for why a raw rendered-grid index was rejected as the
    * tool's own contract).
    *
    * @param columns bare column names to hide (the ordinary, unambiguous case) -- ignored/must be
    *                empty when {@code visible} is {@code true}.
    * @param entries disambiguated {@code {shelf, column, index}} entries, for a column bound more
    *                than once -- same role as {@code set_column_labels}'s {@code entries}.
    * @param visible {@code false} to hide everything named by {@code columns}/{@code entries};
    *                {@code true} to unhide every currently-hidden column at once (which is why
    *                {@code columns}/{@code entries} must be empty in that direction -- there is
    *                nothing for them to select).
    * @return one line per column actually hidden, e.g. {@code "Customer:State -> hidden"}, or a
    *         single {@code "shown every hidden column"} line for the show direction.
    */
   public TableWrite setCrosstabColumnVisibility(String sessionToken, Principal user,
                                                  String assemblyName, List<String> columns,
                                                  List<ColumnVisibilityEntry> entries,
                                                  boolean visible)
      throws Exception
   {
      boolean hasColumns = columns != null && !columns.isEmpty();
      boolean hasEntries = entries != null && !entries.isEmpty();

      if(visible && (hasColumns || hasEntries)) {
         throw new IllegalArgumentException(
            "visible:true (show) unhides every hidden column on a Crosstab at once and takes " +
            "no column list -- there is no selective per-column show, matching the native " +
            "Composer's own Hide/Show action. Omit 'columns'/'entries' to show everything.");
      }

      if(!visible && !hasColumns && !hasEntries) {
         throw new IllegalArgumentException(
            "set_crosstab_column_visibility needs at least one column to hide -- 'columns' or " +
            "'entries'.");
      }

      List<String> applied = new ArrayList<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(!(liveAssembly instanceof CrosstabVSAssembly crosstab)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " +
               (liveAssembly instanceof TableVSAssembly ? "a Table" : "not a Crosstab") +
               " -- set_crosstab_column_visibility only supports Crosstab. Use " +
               "set_table_field_visibility for a Table.");
         }

         CrosstabVSAssemblyInfo tableInfo = crosstab.getCrosstabInfo();

         if(visible) {
            tableInfo.clearHiddenColumns();
            applied.add("shown every hidden column");
         }
         else {
            VSTableLens lens = TableLensGuard.requireRenderedLens(
               rvs, crosstab, assemblyName, "its columns cannot be resolved");
            VSCrosstabInfo crossInfo = crosstab.getVSCrosstabInfo();
            List<String> requestedNames = new ArrayList<>();
            List<TableBindingMutator.ShelfIndex> targets = new ArrayList<>();

            if(hasColumns) {
               for(String column : columns) {
                  requestedNames.add(column);
                  targets.add(TableBindingMutator.resolveVisibilityTarget(
                     model, null, column, null));
               }
            }

            if(hasEntries) {
               for(ColumnVisibilityEntry entry : entries) {
                  if(entry == null || entry.column() == null || entry.column().isBlank()) {
                     throw new IllegalArgumentException(
                        "Each 'entries' item needs 'column' -- the bound column's name.");
                  }

                  requestedNames.add(entry.column());
                  targets.add(TableBindingMutator.resolveVisibilityTarget(
                     model, entry.shelf(), entry.column(), entry.index()));
               }
            }

            // Validate every target before hiding any, so a call that throws leaves the
            // assembly unchanged (the mutate() checkpoint would otherwise keep the partial hide).
            List<String> errors = new ArrayList<>();
            List<Integer> hideCols = new ArrayList<>();
            List<Integer> hideCounts = new ArrayList<>();

            for(int i = 0; i < targets.size(); i++) {
               TableBindingMutator.ShelfIndex target = targets.get(i);
               DataRef ref = liveCrosstabRef(crossInfo, target.shelf(), target.index());

               if(ref == null) {
                  errors.add(
                     "'" + target.shelf() + "[" + target.index() + "]' no longer resolves on " +
                     "the live assembly -- the binding may have changed since this call was " +
                     "validated.");
                  continue;
               }

               // Bug #76882: same collision as applyCrosstabLabels/enrichCrosstabLabelsAndVisibility
               // -- a crosstab column dimension has no corner header cell of its own, so
               // findHeaderCell's "Cell [row,col]" positional match can only find nothing or
               // wrongly collide with an unrelated row dimension's own corner cell, hiding the
               // wrong column.
               if("cols".equals(target.shelf())) {
                  errors.add(
                     "'" + target.shelf() + "[" + target.index() + "]' has no header cell of " +
                     "its own to resolve -- a crosstab's column dimension renders its bound " +
                     "values as the header, not a single column to hide by dimension position.");
                  continue;
               }

               boolean aggregate = "aggregates".equals(target.shelf());

               // Bug #78850: hiding is per rendered lens column, but an aggregate renders in
               // several (one per cols value, plus grand totals/subtotals) when side by side, and
               // all measures share ONE column when stacked.
               if(aggregate && isStackedMeasures(crossInfo)) {
                  errors.add(
                     "'" + requestedNames.get(i) + "' cannot be hidden on its own: this " +
                     "crosstab stacks its measures as rows (summarySideBySide is false), so " +
                     "they all share one measure-name column and hiding it would hide every " +
                     "measure's label. Set summarySideBySide:true with set_table_options " +
                     "first, then hide the measure.");
                  continue;
               }

               List<SetTableHeaderAliasHandler.HeaderCell> cells = new ArrayList<>();

               if(aggregate) {
                  cells.addAll(
                     SetTableHeaderAliasHandler.findHeaderCells(lens, ref, target.index()));
               }
               else {
                  SetTableHeaderAliasHandler.HeaderCell first =
                     SetTableHeaderAliasHandler.findHeaderCell(lens, ref, target.index());

                  if(first != null) {
                     cells.add(first);
                  }
               }

               if(cells.isEmpty()) {
                  errors.add(
                     "Could not find '" + target.shelf() + "[" + target.index() + "]' on the " +
                     "rendered header of '" + assemblyName + "' -- it may not currently render " +
                     "(suppressed or filtered out by the crosstab's current shape).");
                  continue;
               }

               for(SetTableHeaderAliasHandler.HeaderCell cell : cells) {
                  hideCols.add(cell.col());
               }

               hideCounts.add(cells.size());
            }

            if(!errors.isEmpty()) {
               throw new IllegalArgumentException(String.join(" ", errors));
            }

            for(int col : hideCols) {
               tableInfo.addHiddenColumn(col, lens);
            }

            for(int i = 0; i < requestedNames.size(); i++) {
               int copies = hideCounts.get(i);
               applied.add(requestedNames.get(i) + " -> hidden" +
                           (copies > 1 ? " (" + copies + " rendered columns)" : ""));
            }
         }

         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
      });

      return new TableWrite(applied, warnings);
   }

   /**
    * Sets a plain Table's own interactive column-header sort -- the {@link SortInfo} field on
    * {@link TableDataVSAssemblyInfo} a Viewer/Preview column-header click mutates directly
    * (see {@code BaseTableSortColumnService.tableSortColumn}), reached here by an explicit
    * {@code direction} instead of that click handler's ASC -&gt; DESC -&gt; NONE toggle. This is
    * a different Java field entirely from {@link #setSort} above, which only ever writes a
    * Crosstab dimension's order inside {@link BaseTableBindingModel} -- see the refusal below
    * for why a Crosstab is rejected here rather than routed to that method.
    *
    * <p><b>Additive, not exclusive.</b> A plain (non-shift) column-header click discards every
    * other column's sort and keeps only the one just clicked ({@code tableSortColumn}'s
    * {@code if(sinfo == null || !event.multi())} branch); only a shift-click preserves the
    * others. This call always preserves the others -- it is the only way a sequence of
    * single-column tool calls can build a multi-column sort (e.g. "sort Region ascending, then
    * Sales descending" as two separate calls), which a plain click cannot do one column at a
    * time. There is no parameter to ask for the exclusive/plain-click behaviour instead; call
    * this once per column and it composes.
    *
    * <p>Commits via {@link VSAssemblyInfoHandler#apply}, the same primitive
    * {@code VSInputService.setVSSortingDialogModel} (the CalcTable Sort dialog, the closest
    * existing precedent on this exact {@code sinfo} field family) trusts for this, rather than
    * hand-replaying {@code tableSortColumn}'s own multi-call refresh sequence
    * ({@code coreLifecycleService.execute} + {@code layoutViewsheet} +
    * {@code bindingFactory.createModel} + {@code SetVSBindingModelCommand}). {@code refreshPara}
    * is left {@code false} (unlike that CalcTable dialog's call, which passes {@code true}) to
    * match what the native column-click handler itself does -- it never calls
    * {@code refreshParameters} either -- which is also why {@code engine} is passed {@code null}
    * here: {@link VSAssemblyInfoHandler#apply} only dereferences it when {@code refreshPara} is
    * {@code true}.
    *
    * <p>Persists into the saved viewsheet asset on the next save, same as every other write in
    * this class: {@code sinfo} round-trips through {@code TableDataVSAssemblyInfo}'s real
    * {@code writeContents}/{@code parseContents} XML pair, not a runtime-only field.
    *
    * <p>Form tables are refused for now -- the interactive path's form branch
    * ({@code box.sortFormTableLens} plus an {@code UpdateSortInfoCommand}) needs a live
    * {@code ViewsheetSandbox} form-lens refresh this call does not drive.
    */
   public List<String> setColumnSort(String sessionToken, Principal user, String assemblyName,
                             String column, String direction, String linkUri) throws Exception
   {
      int order = parseColumnSortDirection(direction);

      return sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(!(liveAssembly instanceof TableVSAssembly table)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " +
               (liveAssembly instanceof CrosstabVSAssembly ? "a Crosstab" : "not a Table") +
               " -- set_table_column_sort only supports a plain Table. A Crosstab's column " +
               "sort is set_field_sort's job instead (it sorts a dimension shelf, not a " +
               "rendered column).");
         }

         if(table.getVSAssemblyInfo() instanceof TableVSAssemblyInfo tinfo && tinfo.isForm()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a form table -- set_table_column_sort does not " +
               "support form tables yet.");
         }

         // A hidden column is still bound and its sort still applies; it just lives in the
         // separate hidden-columns selection, so resolve over shown then hidden.
         ColumnSelection columns = table.getColumnSelection();
         ColumnSelection hidden = table.getHiddenColumns();
         DataRef ref = resolveSortColumn(columns, column);
         ref = ref != null ? ref : resolveSortColumn(hidden, column);

         if(ref == null) {
            List<String> bound = boundColumnNames(columns);
            bound.addAll(boundColumnNames(hidden).stream().map(n -> n + " (hidden)").toList());
            throw new IllegalArgumentException(
               "'" + column + "' is not bound on '" + assemblyName + "'. It holds: " +
               (bound.isEmpty() ? "(nothing)" : String.join(", ", bound)) + ".");
         }

         // Sorting is applied before alias, so the base ref is what SortInfo keys off -- the
         // same unwrap BaseTableSortColumnService.tableSortColumn() does.
         DataRef base = ref;

         if(ref instanceof ColumnRef columnRef) {
            base = columnRef.getDataRef();

            if(!(base instanceof ColumnRef)) {
               ColumnRef wrapped = new ColumnRef(base);
               wrapped.setDataType(ref.getDataType());
               base = wrapped;
            }
         }

         // Mutate a CLONE, not the assembly's own live info -- table.getVSAssemblyInfo()
         // returns that live field directly (AbstractVSAssembly.getVSAssemblyInfo(), no
         // defensive copy). Passing the live object itself into apply() would make
         // AbstractVSAssembly.setVSAssemblyInfo()'s this.info.copyInfo(info) a self-comparison
         // -- TableDataVSAssemblyInfo.copyInputDataInfo()'s Tool.equalsContent(sinfo, tinfo.sinfo)
         // check is always "equal" against itself, so VSAssembly.INPUT_DATA_CHANGED is never set
         // and the refresh/recompute dispatch a real column-header click triggers silently does
         // not happen. Same clone-first shape as HideColumnsDialogService
         // .setColumnOptionDialogModel() (the delegate setFieldVisibility above already uses
         // correctly) -- info.clone() deep-clones sinfo too (TableDataVSAssemblyInfo.clone(false)),
         // so mutating the clone's SortInfo never touches the live one until apply() commits it.
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getVSAssemblyInfo();
         TableDataVSAssemblyInfo clone = (TableDataVSAssemblyInfo) info.clone();
         SortInfo sinfo = clone.getSortInfo();
         sinfo = sinfo == null ? new SortInfo() : sinfo;
         SortRef sortRef = new SortRef(base);

         if(order == StyleConstants.SORT_NONE) {
            sinfo.removeSort(sortRef);
         }
         else {
            sortRef.setOrder(order);
            sinfo.addSort(sortRef);
         }

         clone.setSortInfo(sinfo);
         assemblyInfoHandler.apply(rvs, clone, null, false, false, false, false, dispatcher,
                                   null, null, linkUri, null);
      });
   }

   /**
    * Maps {@code direction} to {@link StyleConstants}'s {@code SORT_ASC}/{@code SORT_DESC}/
    * {@code SORT_NONE} -- the only three states a plain Table's {@link SortRef} ever takes.
    * Case-folded and a couple of natural spellings accepted (ascending/descending), but unlike
    * {@link DimensionSortRanking}'s Crosstab vocabulary, {@code value_asc}/{@code value_desc}/
    * {@code manual} have no equivalent here (there is no aggregate-by or manual-value-order
    * concept on a Table's own column sort), so those -- and anything else -- are refused by
    * name rather than silently coerced to one of the three real states.
    */
   private static int parseColumnSortDirection(String direction) {
      String token = direction == null ? "" : direction.trim().toLowerCase();

      return switch(token) {
         case "asc", "ascending" -> StyleConstants.SORT_ASC;
         case "desc", "descending" -> StyleConstants.SORT_DESC;
         case "none" -> StyleConstants.SORT_NONE;
         default -> throw new IllegalArgumentException(
            "'direction' must be one of asc, desc, none -- got '" + direction + "'. " +
            "value_asc/value_desc/manual are set_field_sort's Crosstab-only vocabulary and " +
            "have no equivalent on a plain Table's column sort.");
      };
   }

   /** Case-insensitive column-name lookup, matching this class's other by-name resolutions. */
   private static DataRef resolveSortColumn(ColumnSelection columns, String column) {
      if(columns == null) {
         return null;
      }

      Enumeration<DataRef> refs = columns.getAttributes();

      while(refs.hasMoreElements()) {
         DataRef ref = refs.nextElement();
         String name = ref.getName();

         if(name != null &&
            (name.equalsIgnoreCase(column) || unqualified(name).equalsIgnoreCase(column)))
         {
            return ref;
         }
      }

      return null;
   }

   private static List<String> boundColumnNames(ColumnSelection columns) {
      List<String> names = new ArrayList<>();

      if(columns == null) {
         return names;
      }

      Enumeration<DataRef> refs = columns.getAttributes();

      while(refs.hasMoreElements()) {
         names.add(refs.nextElement().getName());
      }

      return names;
   }

   public List<String> setSort(String sessionToken, Principal user, String assemblyName, String shelf,
                       String column, Integer index, DimensionSortRanking.Sort sort)
      throws Exception
   {
      return apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setSort(model, shelf, column, index, sort));
   }

   public List<String> setRanking(String sessionToken, Principal user, String assemblyName, String shelf,
                          String column, Integer index, DimensionSortRanking.Ranking ranking)
      throws Exception
   {
      return apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setRanking(model, shelf, column, index, ranking));
   }

   /**
    * What a table write that bypasses {@link #apply} hands back: one line per change written, and
    * the warnings {@code sessions.mutate} captured while the table re-ran (a script failure, for
    * one).
    */
   public record TableWrite(List<String> applied, List<String> warnings) {
      public TableWrite {
         warnings = warnings == null ? List.of() : warnings;
      }
   }

   /**
    * Unlike every other mutator above, this does not go through {@link #apply}/{@link
    * #applyWithContext}: a Crosstab label needs a rendered {@code VSTableLens} to resolve a
    * column to its header cell, which neither of those helpers exposes (see {@code
    * TableBindingMutator.setColumnLabels}'s own javadoc for why the mutator itself stops short of
    * that). Table needs no lens — {@code ColumnRefModel.alias} already round-trips through the
    * existing write below — but does need the live {@code TableVSAssembly} to rekey a
    * pre-existing per-column {@code FormatInfo}/column-width/highlight entry from the old display
    * name to the new one, which {@code TableBindingMutator} (model-only) cannot reach either.
    *
    * @return one line per label actually written, e.g. {@code "Region -> Sales Region"} — used to
    *         build an accurate summary instead of trusting the request's own label count, since a
    *         request can name more columns than a single assembly type actually has shelves for.
    */
   public TableWrite setColumnLabels(String sessionToken, Principal user, String assemblyName,
                                     Map<String, String> labels,
                                     List<ColumnLabelEntry> entries) throws Exception
   {
      List<String> applied = new ArrayList<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         TableBindingMutator.ColumnLabelWrite write =
            TableBindingMutator.setColumnLabels(model, labels, entries);

         if(!write.tableRenames().isEmpty() && assembly instanceof TableVSAssembly table) {
            rekeyTableFormatWidthAndHighlight(table, write.tableRenames());
            aliasLiveColumns(table, write.tableRenames());

            for(TableBindingMutator.Rename rename : write.tableRenames()) {
               applied.add(rename.oldDisplayName() + " -> " + rename.newDisplayName());
            }
         }

         if(!write.crosstabTargets().isEmpty() && assembly instanceof CrosstabVSAssembly crosstab) {
            applyCrosstabLabels(rvs, crosstab, write.crosstabTargets());

            for(TableBindingMutator.CrosstabTarget target : write.crosstabTargets()) {
               applied.add(target.shelf() + "[" + target.index() + "] -> " + target.label());
            }
         }

         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
      });

      return new TableWrite(applied, warnings);
   }

   /**
    * Sets each renamed column's alias on the live {@code ColumnRef}, the way {@code
    * ComposerVSTableService.changeColumnTitle} does, before the model is applied. {@link
    * VSTableBindingFactory#updateTableAssembly} looks the old column up by the NEW (aliased) name
    * to carry a Form column's {@code ColumnOption} across; with the alias only on the model that
    * lookup missed and a Form Table's relabel crashed. The column is found by its old display
    * name (alias when set, else attribute name), not by the attribute-keyed {@code refKey}.
    */
   private static void aliasLiveColumns(TableVSAssembly table,
                                        List<TableBindingMutator.Rename> renames)
   {
      ColumnSelection cols = ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).getColumnSelection();

      for(TableBindingMutator.Rename rename : renames) {
         Enumeration<DataRef> refs = cols.getAttributes();

         while(refs.hasMoreElements()) {
            if(refs.nextElement() instanceof ColumnRef column &&
               rename.oldDisplayName().equals(column.getName()))
            {
               column.setAlias(rename.newDisplayName());
               break;
            }
         }
      }
   }

   /**
    * Ports {@code ComposerVSTableService.changeColumnTitle}'s Table-branch rekey (lines 151-167
    * at the time this was written, plus its private {@code syncHighlight} helper) so a
    * pre-existing per-column format/highlight/width entry follows a wiz rename instead of
    * silently detaching under the old display name.
    */
   private static void rekeyTableFormatWidthAndHighlight(TableVSAssembly table,
                                                         List<TableBindingMutator.Rename> renames)
   {
      FormatInfo finfo = table.getFormatInfo();
      FormatInfo nfinfo = new FormatInfo();

      for(TableDataPath path : finfo.getPaths()) {
         String[] pathArr = path.getPath();
         String newName = pathArr == null || pathArr.length != 1 ? null : renamedTo(renames, pathArr[0]);

         if(newName != null) {
            TableDataPath renamed = (TableDataPath) path.clone(new String[]{ newName });
            nfinfo.setFormat(renamed, finfo.getFormat(path));
         }
         else {
            nfinfo.setFormat(path, finfo.getFormat(path));
         }
      }

      table.setFormatInfo(nfinfo);

      for(TableBindingMutator.Rename rename : renames) {
         table.getTableDataVSAssemblyInfo()
            .updateColumnWidthNames(rename.oldDisplayName(), rename.newDisplayName());
      }

      syncHighlight(table, renames);
   }

   /**
    * Ports {@code ComposerVSTableService.syncHighlight} (still a private, dead-for-this-purpose
    * method on that class): rekeys a {@code TableHighlightAttr} entry keyed by a column's old
    * display name to its new one, the same way {@link #rekeyTableFormatWidthAndHighlight}'s own
    * {@code FormatInfo} rekey does, so a pre-existing per-column highlight does not silently
    * orphan under a name nothing renders under anymore.
    */
   private static void syncHighlight(TableVSAssembly table, List<TableBindingMutator.Rename> renames) {
      TableHighlightAttr hattr = table.getTableDataVSAssemblyInfo().getHighlightAttr();

      if(hattr == null) {
         return;
      }

      Map<TableDataPath, HighlightGroup> map = hattr.getHighlightMap();
      List<TableDataPath> paths = new ArrayList<>(map.keySet());

      for(TableDataPath path : paths) {
         if(path == null) {
            continue;
         }

         String[] pathArr = path.getPath();
         String newName = pathArr == null || pathArr.length != 1 ? null :
            renamedTo(renames, pathArr[0]);

         if(newName != null) {
            TableDataPath renamed = (TableDataPath) path.clone(new String[]{ newName });
            HighlightGroup hg = hattr.getHighlight(path);
            map.remove(path);
            hattr.setHighlight(renamed, hg);
         }
      }
   }

   private static String renamedTo(List<TableBindingMutator.Rename> renames, String oldName) {
      for(TableBindingMutator.Rename rename : renames) {
         if(Objects.equals(rename.oldDisplayName(), oldName)) {
            return rename.newDisplayName();
         }
      }

      return null;
   }

   /**
    * The Crosstab write {@code TableBindingMutator} could not do itself: resolves each target's
    * live {@code DataRef} off the real assembly (not the wiz model — see {@code
    * TableBindingMutator.CrosstabTarget}'s javadoc), renders the same {@code VSTableLens}
    * {@code ComposerVSTableService.changeColumnTitle} renders for the native Composer's own
    * header-rename gesture, and writes the {@code MESSAGE_FORMAT}/{@code TableDataPath} override
    * that mechanism relies on to render a fixed header string.
    */
   private void applyCrosstabLabels(RuntimeViewsheet rvs, CrosstabVSAssembly crosstab,
                                    List<TableBindingMutator.CrosstabTarget> targets)
      throws Exception
   {
      VSTableLens lens = null;
      VSCrosstabInfo crossInfo = crosstab.getVSCrosstabInfo();
      FormatInfo formatInfo = crosstab.getFormatInfo();
      // Resolve every target before writing any, so a call that throws leaves the assembly
      // unchanged (the mutate() checkpoint would otherwise keep the partial label write).
      List<String> errors = new ArrayList<>();
      List<DataRef> clearRefs = new ArrayList<>();
      List<Integer> clearIdx = new ArrayList<>();
      List<TableDataPath> setPaths = new ArrayList<>();
      List<String> setLabels = new ArrayList<>();

      for(TableBindingMutator.CrosstabTarget target : targets) {
         DataRef ref = liveCrosstabRef(crossInfo, target.shelf(), target.index());

         if(ref == null) {
            errors.add(
               "'" + target.shelf() + "[" + target.index() + "]' no longer resolves on the " +
               "live assembly -- the binding may have changed since this call was validated.");
            continue;
         }

         // Bug #76882: a crosstab column dimension has no header cell of its own to alias -- its
         // rendered header shows the dimension's bound values, not a title, so findHeaderPath's
         // "Cell [row,col]" positional match can only ever find (or, worse, wrongly collide with)
         // a row dimension's own corner title cell. See enrichCrosstabLabelsAndVisibility's
         // javadoc for the read-side half of this same defect.
         if("cols".equals(target.shelf())) {
            errors.add(
               "'" + target.shelf() + "[" + target.index() + "]' has no header cell of its own " +
               "to relabel -- a crosstab's column dimension renders its bound values as the " +
               "header, not a dimension title, so there is nothing here for set_column_labels " +
               "to target.");
            continue;
         }

         if(target.label().isEmpty()) {
            clearRefs.add(ref);
            clearIdx.add(target.index());
            continue;
         }

         if(lens == null) {
            lens = TableLensGuard.requireRenderedLens(
               rvs, crosstab, crosstab.getAbsoluteName(),
               "its header cannot be resolved to a cell");
         }

         TableDataPath path = SetTableHeaderAliasHandler.findHeaderPath(lens, ref, target.index());

         if(path == null) {
            errors.add(
               "Could not find '" + target.shelf() + "[" + target.index() + "]' on the " +
               "rendered header of '" + crosstab.getAbsoluteName() + "' -- it may not currently " +
               "render (suppressed, filtered out, or hidden by the crosstab's current shape).");
            continue;
         }

         setPaths.add(path);
         setLabels.add(target.label());
      }

      if(!errors.isEmpty()) {
         throw new IllegalArgumentException(String.join(" ", errors));
      }

      for(int i = 0; i < clearRefs.size(); i++) {
         ClearTableHeaderAliasHandler.clearAlias(clearRefs.get(i), formatInfo, clearIdx.get(i));
      }

      for(int i = 0; i < setPaths.size(); i++) {
         SetTableHeaderAliasHandler.setAliasWithHeaderDuality(
            setPaths.get(i), formatInfo, setLabels.get(i));
      }


      crosstab.setFormatInfo(formatInfo);
   }

   /**
    * The live {@code DataRef} at a shelf position -- {@code rows}/{@code cols} map straight onto
    * {@code VSCrosstabInfo}'s own arrays (a 1:1 build, confirmed against {@code
    * VSCrosstabBindingFactory.createModel}), but {@code aggregates} does not: that factory skips
    * {@code VSUtil.isFake} entries when building the wiz-facing model, so the live array can have
    * more entries than the model's {@code aggregates} shelf. Filtering the same way here restores
    * the position correspondence the model's {@code index} was resolved against.
    */
   /**
    * {@code true} when this crosstab lays several measures out as rows ({@code
    * summarySideBySide} off, more than one aggregate), so they share one measure-name column
    * rather than each owning rendered columns.
    */
   private static boolean isStackedMeasures(VSCrosstabInfo crossInfo) {
      if(crossInfo.isSummarySideBySide()) {
         return false;
      }

      int count = 0;

      for(DataRef agg : crossInfo.getAggregates()) {
         if(!VSUtil.isFake(agg)) {
            count++;
         }
      }

      return count > 1;
   }

   private static DataRef liveCrosstabRef(VSCrosstabInfo crossInfo, String shelf, int index) {
      switch(shelf) {
         case "rows":
            DataRef[] rows = crossInfo.getRowHeaders();
            return index >= 0 && index < rows.length ? rows[index] : null;
         case "cols":
            DataRef[] cols = crossInfo.getColHeaders();
            return index >= 0 && index < cols.length ? cols[index] : null;
         default:
            List<DataRef> aggregates = new ArrayList<>();

            for(DataRef agg : crossInfo.getAggregates()) {
               if(!VSUtil.isFake(agg)) {
                  aggregates.add(agg);
               }
            }

            return index >= 0 && index < aggregates.size() ? aggregates.get(index) : null;
      }
   }

   /**
    * Unlike {@link #setColumnLabels}, Table and Crosstab share one code path here: a column
    * width is stored keyed by the column's rendered {@link TableDataPath} ({@code
    * TableDataVSAssemblyInfo.setColumnWidthValue2}), which resolves identically for either
    * assembly type once a {@code VSTableLens} is rendered -- there is no type-specific mechanism
    * to branch on the way labels' Table (alias written straight onto the model) vs Crosstab
    * (header cell resolved in the lens) split requires. Like the Crosstab label branch, this
    * bypasses {@link #apply}/{@link #applyWithContext}, since it needs the live sandbox/lens
    * those helpers don't expose.
    *
    * <p>A column is resolved by matching its current rendered header text -- the header cell's
    * actual rendered value ({@code lens.getObject(row, col)}), not its {@code TableDataPath}'s
    * last path segment, which for a Crosstab dimension header cell is an internal positional
    * token ({@code "Cell [row,col]"}, see {@code CrossFilterDataDescriptor.getCellDataPath}), not
    * the rendered text -- against every column the lens is rendering right now. The scan covers
    * the same L-shaped header region {@link SetTableHeaderAliasHandler#findHeaderPath} does (top
    * arm: rows {@code [0, headerRowCount)} across every column; left arm: rows {@code
    * [headerRowCount, rowCount)} within the header columns), since a non-side-by-side crosstab's
    * per-row aggregate labels render in column 0 at rows past the header row rectangle, never in
    * row 0 alone. The left arm accepts only those aggregate label cells: the rest of that
    * region is row-dimension values, and matching one by text (bug #77036, {@code "USA East"})
    * widened the whole header column instead of refusing a name that is not a column. This is not a shelf/index lookup the way a {@code ColumnLabelEntry} resolves a
    * Crosstab target, because a width is a rendering-only property with no shelf position of its
    * own. A name matching zero or more than one rendered column is refused rather than guessed
    * at: this call has no shelf-index fallback the way {@code set_column_labels}'s {@code
    * entries} does, so an ambiguous name is a real, honest limitation here, not a bug to route
    * around.
    *
    * <p>Width is in pixels. A {@code null} width resets the column back to auto-fit ({@code
    * setColumnWidthValue(col, NaN)}); a finite positive width is stored with {@code
    * setColumnWidthValue2}, the same call {@code ComposerVSTableService.changeColumnWidth} makes
    * for a live column-drag resize (its own {@code setColumnWidthValue} javadoc calling the
    * value "a ratio to total width" is stale for this call path -- trust the usage site).
    *
    * @return one line per width actually written, e.g. {@code "Region -> 120px"} or
    *         {@code "Region -> auto"} for a reset.
    */
   public TableWrite setColumnWidths(String sessionToken, Principal user, String assemblyName,
                                     Map<String, Double> widths) throws Exception
   {
      if(widths == null || widths.isEmpty()) {
         throw new IllegalArgumentException(
            "setColumnWidths requires a non-empty 'widths' object.");
      }

      List<String> applied = new ArrayList<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         VSTableLens lens = TableLensGuard.requireRenderedLens(
            rvs, assembly, assemblyName, "its columns cannot be resolved");

         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();

         int headerRows = lens.getHeaderRowCount();
         int headerCols = lens.getHeaderColCount();
         int colCount = lens.getColCount();
         int rowCount = lens.getRowCount();
         Set<String> aggregateHeaders = aggregateFullNames(assembly);

         // Validate every entry before writing any, so a call that throws leaves the assembly
         // unchanged (the mutate() checkpoint would otherwise keep the partial width write).
         List<String> errors = new ArrayList<>();
         List<Integer> cols = new ArrayList<>();
         List<String> names = new ArrayList<>();
         List<Double> values = new ArrayList<>();

         for(Map.Entry<String, Double> entry : widths.entrySet()) {
            String column = entry.getKey();
            Double width = entry.getValue();
            List<Integer> matches = new ArrayList<>();

            scanForColumnMatch(lens, column, 0, headerRows, 0, colCount, null, matches);
            scanForColumnMatch(lens, column, headerRows, rowCount, 0, headerCols,
                               aggregateHeaders, matches);

            if(matches.isEmpty()) {
               errors.add(
                  "'" + column + "' is not a visible column on '" + assemblyName + "' right " +
                  "now.");
               continue;
            }

            if(matches.size() > 1) {
               errors.add(
                  "'" + column + "' matches " + matches.size() + " rendered columns on '" +
                  assemblyName + "' -- width cannot be set by name when it's ambiguous.");
               continue;
            }

            if(width != null && (!Double.isFinite(width) || width <= 0)) {
               errors.add(
                  "'" + column + "' width must be a finite, positive number of pixels; got " +
                  width + ".");
               continue;
            }

            cols.add(matches.get(0));
            names.add(column);
            values.add(width);
         }

         if(!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
         }

         for(int i = 0; i < cols.size(); i++) {
            Double width = values.get(i);

            if(width == null) {
               info.setColumnWidthValue(cols.get(i), Double.NaN);
               applied.add(names.get(i) + " -> auto");
            }
            else {
               info.setColumnWidthValue2(cols.get(i), width, lens);
               applied.add(names.get(i) + " -> " + formatPixels(width) + "px");
            }
         }

         info.setExplicitTableWidthValue(true);

         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
      });

      return new TableWrite(applied, warnings);
   }

   /** Strips a trailing {@code .0} so a whole-pixel width reads as {@code "120px"}, not {@code "120.0px"}. */
   private static String formatPixels(double width) {
      return width == Math.floor(width) && !Double.isInfinite(width)
         ? String.valueOf((long) width) : String.valueOf(width);
   }

   /**
    * Appends every column in {@code [colStart, colEnd)} whose rendered cell value at some row in
    * {@code [rowStart, rowEnd)} equals {@code column} to {@code matches}, skipping a column
    * already recorded (guards against the top/left scan arms in {@link #setColumnWidths}
    * double-counting a cell that falls in both).
    *
    * @param headerPaths when non-null, a cell counts only if its {@code TableDataPath}'s last
    *                    segment is one of these -- how the left arm tells a mirrored aggregate
    *                    label apart from a row-dimension value in the same header column.
    */
   private static void scanForColumnMatch(VSTableLens lens, String column, int rowStart,
                                          int rowEnd, int colStart, int colEnd,
                                          Set<String> headerPaths, List<Integer> matches)
   {
      for(int row = rowStart; row < rowEnd; row++) {
         for(int col = colStart; col < colEnd; col++) {
            Object val = lens.getObject(row, col);

            if(Objects.equals(column, val == null ? null : val.toString()) &&
               !matches.contains(col) &&
               (headerPaths == null || headerPaths.contains(lastPathSegment(lens, row, col))))
            {
               matches.add(col);
            }
         }
      }
   }

   private static String lastPathSegment(VSTableLens lens, int row, int col) {
      TableDataPath path = lens.getTableDataPath(row, col);
      String[] segments = path == null ? null : path.getPath();
      return segments == null || segments.length == 0 ? null : segments[segments.length - 1];
   }

   /**
    * The full names ({@code "Sum(Sales)"}) of a crosstab's aggregates -- the last {@code
    * TableDataPath} segment of each aggregate's header cell, the same test {@code
    * ClearTableHeaderAliasHandler.matchAgg} applies. Empty for a Table, which renders no
    * aggregate labels down its header columns. The runtime aggregates are included because the
    * lens builds its paths from them -- a {@code $(var)} measure or a date-comparison
    * aggregate has a rendered full name that only the runtime list carries.
    */
   private static Set<String> aggregateFullNames(VSAssembly assembly) {
      Set<String> names = new HashSet<>();

      if(assembly instanceof CrosstabVSAssembly crosstab &&
         crosstab.getVSCrosstabInfo() != null)
      {
         VSCrosstabInfo info = crosstab.getVSCrosstabInfo();

         for(DataRef[] aggregates : new DataRef[][]{ info.getRuntimeAggregates(),
                                                     info.getAggregates() })
         {
            for(DataRef agg : aggregates == null ? new DataRef[0] : aggregates) {
               if(agg instanceof VSAggregateRef aggregate) {
                  names.add(aggregate.getFullName());
               }
            }
         }
      }

      return names;
   }

   public List<String> setOptions(String sessionToken, Principal user, String assemblyName,
                          Map<String, Object> options) throws Exception
   {
      // Unlike every other mutator below, this one is allowed to actually change percentageBy
      // — but only when this call's own options map carries that key. TableBindingMutator
      // .setCrosstabOptions only calls setPercentageByValue when options.containsKey
      // ("percentageBy"); an options-only write that omits it (e.g. {"rowTotals": true}) must
      // still have the manufactured default reset, or it reopens this same bug through a
      // narrower trigger.
      boolean changesPercentageBy = options != null && options.containsKey("percentageBy");
      return apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setOptions(model, options), false, !changesPercentageBy);
   }

   public Map<String, Object> optionVocabulary() {
      return TableBindingMutator.optionVocabulary();
   }

   /** The shelves, their contents, and the object type — without opening a checkpoint. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
      Map<String, List<FieldRef>> shelfFields = new LinkedHashMap<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         shelfFields.put(shelf, new ArrayList<>(TableBindingMutator.read(model, shelf)));
      }
      List<String> visibilityNotes = new ArrayList<>();

      if(model instanceof CrosstabBindingModel) {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(liveAssembly instanceof CrosstabVSAssembly crosstab) {
            enrichCrosstabLabelsAndVisibility(rvs, crosstab, shelfFields, visibilityNotes);
         }
      }
      else if(model instanceof TableBindingModel) {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(liveAssembly instanceof TableVSAssembly table) {
            enrichTableVisibility(table, shelfFields);
         }
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("objectType", model instanceof CrosstabBindingModel ? "crosstab" : "table");
      out.put("source", model.getSource() == null ? null : model.getSource().getSource());

      // A sourceless assembly accepts shelf writes and renders nothing, with no error anywhere
      // to say why. Saying so on the read every caller is told to make first is the cheapest
      // place to stop that.
      if(model.getSource() == null) {
         out.put("note",
                 "This assembly has no source table, so it renders empty whatever is on its " +
                 "shelves. Point it at one with set_table_source — list_bindable_fields " +
                 "reports the names it accepts.");
      }

      if(!visibilityNotes.isEmpty()) {
         out.put("visibilityNote", String.join(" ", visibilityNotes));
      }

      out.put("shelves", new LinkedHashMap<>(shelfFields));
      putColumnLabels(out, shelfFields);
      Map<String, Object> sorts = new LinkedHashMap<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         if(!"aggregates".equals(shelf) && !"details".equals(shelf)) {
            sorts.putAll(TableBindingMutator.describeSorts(model, shelf));
         }
      }

      // The loop above never fires for a Table -- shelvesOf() only ever returns "details" for
      // one, which is unconditionally excluded (that exclusion is TableBindingMutator's own
      // Crosstab-dimension-shelf vocabulary, deliberately not touched here; see
      // describeTableColumnSorts's javadoc). Without this, "sorts" was structurally always
      // empty for objectType:"table", the read-side half of this bug -- setColumnSort's write
      // above had nothing that reported it back.
      if(model instanceof TableBindingModel) {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(liveAssembly instanceof TableVSAssembly table) {
            sorts.putAll(describeTableColumnSorts(table));
         }
      }

      out.put("sorts", sorts);
      out.put("options", describeOptions(model));

      if(model instanceof CrosstabBindingModel crosstab) {
         out.put("suppressGroupTotal", crosstab.getSuppressGroupTotal());
      }
      else if(model instanceof TableBindingModel table) {
         // Read-only for now: writing it turns an embedded table into a bound one, which is
         // closer to a data-loss operation than a binding edit.
         out.put("embedded", table.getEmbedded());
      }

      return out;
   }

   /**
    * The backward-compatible {@code columnLabels: {column: label}} map {@code name2Labels} used
    * to source (always empty — see the class javadoc history) — now real, but only for a column
    * bound exactly once: a column bound more than once (a Year/Quarter drill) can carry a
    * different label per occurrence, which a flat map keyed by bare column name cannot represent.
    * Those are left out of {@code columnLabels} with a {@code columnLabelsNote} pointing at each
    * shelf entry's own {@code label} instead, rather than silently reporting just one of the two
    * (which one would depend on shelf iteration order, not on anything the caller chose).
    */
   private static void putColumnLabels(Map<String, Object> out,
                                       Map<String, List<FieldRef>> shelfFields)
   {
      Map<String, Integer> occurrences = new LinkedHashMap<>();

      for(List<FieldRef> fields : shelfFields.values()) {
         for(FieldRef field : fields) {
            if(field.column() != null) {
               occurrences.merge(field.column(), 1, Integer::sum);
            }
         }
      }

      Map<String, String> columnLabels = new LinkedHashMap<>();
      List<String> ambiguous = new ArrayList<>();

      for(List<FieldRef> fields : shelfFields.values()) {
         for(FieldRef field : fields) {
            if(field.column() == null || field.label() == null) {
               continue;
            }

            if(occurrences.getOrDefault(field.column(), 0) > 1) {
               if(!ambiguous.contains(field.column())) {
                  ambiguous.add(field.column());
               }

               continue;
            }

            columnLabels.put(field.column(), field.label());
         }
      }

      out.put("columnLabels", columnLabels);

      if(!ambiguous.isEmpty()) {
         out.put("columnLabelsNote",
                 "'" + String.join("', '", ambiguous) + "' " +
                 (ambiguous.size() == 1 ? "is" : "are") + " bound more than once; see its label " +
                 "on each shelf entry instead of columnLabels.");
      }
   }

   /**
    * Best-effort: a Crosstab label is a {@code MESSAGE_FORMAT} {@code FormatInfo} entry at the
    * bound column's header {@code TableDataPath}, and a Crosstab's hidden state ({@code
    * CrosstabVSAssemblyInfo.isColumnHidden}) is keyed by a rendered lens column index -- both need
    * the same rendered {@code VSTableLens}/{@code DataRef} resolution per shelf entry, so they are
    * folded into one enrichment pass rather than resolving each entry's header cell twice.
    * Swallows any render failure and leaves every {@code label}/{@code visible} at its default
    * {@code null} rather than failing a read that would otherwise succeed — matching this
    * codebase's established fail-open stance for anything that needs a live render just to
    * disclose more, not to validate.
    *
    * <p>{@code visible} is left {@code null} (not {@code false}) when the entry's header cell
    * cannot be found in the rendered lens at all -- that can mean genuinely suppressed/filtered
    * for a reason unrelated to {@code hiddenColumns}, not necessarily hidden, so this does not
    * guess. When the cell <em>is</em> found, {@code isColumnHidden(col, lens)} is definitive and
    * precise: a hidden dimension's header cell resolves through the same {@code
    * CrossFilterDataDescriptor} computation as any other bound column's (see {@code
    * SetTableHeaderAliasHandler.findHeaderCell}'s own javadoc), so there is no proxy/guesswork
    * needed here the way an unresolved {@code findHeaderPath() == null} would have been.
    *
    * <p>The {@code cols} shelf is skipped entirely (bug #76882) -- {@code findHeaderCell}'s
    * {@code "Cell [row,col]"} positional match only ever identifies a <em>row</em> dimension's own
    * corner title cell (row dimensions stack horizontally across the header row, one grid column
    * per row-dimension index, which is what that path's numeric position encodes). A column
    * dimension has no analogous title cell of its own -- its header renders the dimension's bound
    * *values*, each with a real tuple-derived path {@code findHeaderCell} never matches this way --
    * so resolving a {@code cols} entry through this mechanism either finds nothing or, whenever the
    * corner box happens to be small (e.g. one row dimension and one column dimension collapse the
    * corner to a single cell), spuriously collides with an unrelated row dimension's own corner
    * cell and leaks its label/visibility onto the wrong shelf entry.
    */
   private static void enrichCrosstabLabelsAndVisibility(RuntimeViewsheet rvs,
                                                         CrosstabVSAssembly crosstab,
                                                         Map<String, List<FieldRef>> shelfFields,
                                                         List<String> notes)
   {
      try {
         Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

         if(box.isEmpty()) {
            return;
         }

         String oname = crosstab.getAbsoluteName();
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSTableLens lens = box.get().getVSTableLens(oname, detail);
         VSCrosstabInfo crossInfo = crosstab.getVSCrosstabInfo();
         FormatInfo formatInfo = crosstab.getFormatInfo();
         CrosstabVSAssemblyInfo tableInfo = crosstab.getCrosstabInfo();

         for(String shelf : List.of("rows", "aggregates")) {
            List<FieldRef> fields = shelfFields.get(shelf);

            if(fields == null) {
               continue;
            }

            for(int i = 0; i < fields.size(); i++) {
               DataRef ref = liveCrosstabRef(crossInfo, shelf, i);

               if(ref == null) {
                  continue;
               }

               boolean aggregate = "aggregates".equals(shelf);
               List<SetTableHeaderAliasHandler.HeaderCell> cells = new ArrayList<>();

               if(aggregate) {
                  cells.addAll(SetTableHeaderAliasHandler.findHeaderCells(lens, ref, i));
               }
               else {
                  SetTableHeaderAliasHandler.HeaderCell first =
                     SetTableHeaderAliasHandler.findHeaderCell(lens, ref, i);

                  if(first != null) {
                     cells.add(first);
                  }
               }

               if(cells.isEmpty()) {
                  continue;
               }

               FieldRef field = fields.get(i);
               String label = readAlias(formatInfo, cells.get(0).path());

               if(label != null) {
                  field = withLabel(field, label);
               }

               // Bug #78850: stacked measures share one column, so no single measure has a
               // visibility of its own -- leave it unreported rather than read all-hidden.
               if(aggregate && isStackedMeasures(crossInfo)) {
                  fields.set(i, field);
                  continue;
               }

               int hidden = 0;

               for(SetTableHeaderAliasHandler.HeaderCell cell : cells) {
                  if(tableInfo.isColumnHidden(cell.col(), lens)) {
                     hidden++;
                  }
               }

               // Hidden only when every rendered copy is; a partial hide (e.g. one column hidden
               // in the Composer UI) still renders, so it reads visible with a note.
               field = withVisibility(field, hidden < cells.size());

               if(hidden > 0 && hidden < cells.size()) {
                  notes.add("'" + field.column() + "' is partially hidden: " + hidden + " of " +
                            cells.size() + " rendered columns are hidden.");
               }

               fields.set(i, field);
            }
         }
      }
      catch(Exception ignore) {
         // Best-effort, see javadoc above.
      }
   }

   private static String readAlias(FormatInfo formatInfo, TableDataPath path) {
      VSCompositeFormat format = formatInfo.getFormat(path);
      VSFormat ufmt = format == null ? null : format.getUserDefinedFormat();

      if(ufmt == null || !VSFormat.MESSAGE_FORMAT.equals(ufmt.getFormatValue())) {
         return null;
      }

      return ufmt.getFormatExtentValue();
   }

   private static FieldRef withLabel(FieldRef field, String label) {
      return new FieldRef(field.column(), field.type(), field.aggregate(), field.dateLevel(),
                          field.namedGroup(), field.chartType(), field.runtimeChartType(),
                          field.namedGroupValues(), field.calculateInfo(), label,
                          field.secondaryY(), field.visible(), field.timeSeries(),
                          field.secondaryColumn(), field.operand());
   }

   /**
    * Marks each {@code details} shelf {@link FieldRef} with whether its column currently renders,
    * per {@link TableVSAssemblyInfo#getHiddenColumns()} -- the read side of {@link
    * #setFieldVisibility}. {@code get_table_binding} was blind to this state before (see
    * bug-76807): {@link TableBindingMutator#read} only ever reads {@code
    * TableBindingModel.getDetails()}, which has no visibility concept of its own.
    *
    * <p>A hidden column is <em>absent</em> from {@code details} to begin with -- {@link
    * TableBindingModel}'s constructor builds it only from {@link
    * TableVSAssembly#getColumnSelection()}, the shown side, disjoint by construction from {@code
    * getHiddenColumns()}. So this does not just re-mark the existing list: it merges in every
    * hidden column {@code details} is missing, at the position {@link
    * HideColumnsDialogService#getOrderedColumns} recorded for it. Without this merge a hidden
    * field vanishes from {@code get_table_binding} instead of reporting {@code visible: false}
    * -- the exact silent-disappearance class of bug bug-76807 exists to close, just reopened on
    * the read side.
    *
    * <p>The resulting {@code details} index is the column's slot in the <em>full</em> (shown and
    * hidden) order, and {@link #addField}'s {@code position} on a Table reads the same order, so
    * an index read here and written back names the same slot. Hidden positions are absolute
    * indexes into that order: they are re-stamped by {@code addField} and {@code removeField} but not
    * when shown columns are reordered, inserted or removed by other writes, so a column shown again later may land beside a
    * different neighbour than the one it was hidden next to.
    */
   private void enrichTableVisibility(TableVSAssembly table,
                                       Map<String, List<FieldRef>> shelfFields)
   {
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection hiddenCols = info.getHiddenColumns();
      Set<String> hidden = new HashSet<>();
      Enumeration<DataRef> refs = hiddenCols.getAttributes();

      while(refs.hasMoreElements()) {
         DataRef href = refs.nextElement();
         hidden.add(refKey(href));
      }

      List<FieldRef> details = shelfFields.get("details");
      List<FieldRef> enriched = details == null ? new ArrayList<>() : new ArrayList<>(details);
      Set<String> present = new HashSet<>();

      for(int i = 0; i < enriched.size(); i++) {
         FieldRef field = enriched.get(i);
         present.add(field.column());
         enriched.set(i, withVisibility(field, !hidden.contains(field.column())));
      }

      Map<String, FieldRef> hiddenFields = new LinkedHashMap<>();
      Enumeration<DataRef> hiddenRefs = hiddenCols.getAttributes();

      while(hiddenRefs.hasMoreElements()) {
         DataRef ref = hiddenRefs.nextElement();

         if(present.contains(refKey(ref))) {
            continue;
         }

         ColumnRef col = (ColumnRef) ref.clone();
         col.setApplyingAlias(false);
         ColumnRefModel model = (ColumnRefModel) refModelService.createDataRefModel(col);
         hiddenFields.put(refKey(ref),
                          withVisibility(TableBindingMutator.fieldOf(model, "details"), false));
      }

      // a hidden column reads back at the position it will return to when shown
      List<FieldRef> ordered = new ArrayList<>();
      Map<String, FieldRef> byName = new HashMap<>();

      for(FieldRef field : enriched) {
         byName.put(field.column(), field);
      }

      for(DataRef ref : HideColumnsDialogService.getOrderedColumns(info.getColumnSelection(),
                                                                   hiddenCols))
      {
         FieldRef field = byName.remove(refKey(ref));

         if(field == null) {
            field = hiddenFields.remove(refKey(ref));
         }

         if(field != null) {
            ordered.add(field);
         }
      }

      for(FieldRef field : enriched) {
         if(byName.containsKey(field.column())) {
            ordered.add(field);
         }
      }

      ordered.addAll(hiddenFields.values());
      shelfFields.put("details", ordered);
   }

   /**
    * The key {@link FieldRef#column()} reports a Table column under: the column's own name,
    * entity-qualified when it has an entity, never its alias.
    */
   private static String refKey(DataRef ref) {
      return ref instanceof ColumnRef column && column.getDataRef() != null
         ? column.getDataRef().getName() : ref.getName();
   }

   /**
    * The read side of {@link #setColumnSort}: a plain Table's current column sort, keyed by
    * column name to {@code "asc"}/{@code "desc"} -- the same {@code sorts} map
    * {@link TableBindingMutator#describeSorts} already populates for a Crosstab dimension
    * shelf, just built from {@code table.getSortInfo()} directly instead, since that mutator's
    * vocabulary (sortByField/manualOrder/ranking) is Crosstab-dimension-only and does not apply
    * to a Table's plain {@link SortRef}[]. An unsorted column is omitted rather than reported as
    * {@code "none"}, matching how {@code shelves} and this same {@code sorts} map already omit
    * anything with nothing to report elsewhere in this method, rather than enumerating every
    * bound column's default state.
    */
   private static Map<String, Object> describeTableColumnSorts(TableVSAssembly table) {
      Map<String, Object> out = new LinkedHashMap<>();
      SortInfo sinfo = table.getSortInfo();

      if(sinfo == null) {
         return out;
      }

      for(SortRef ref : sinfo.getSorts()) {
         DataRef column = ref.getDataRef();
         String name = column == null ? null : column.getName();

         if(name == null) {
            continue;
         }

         if(ref.getOrder() == StyleConstants.SORT_ASC) {
            out.put(name, "asc");
         }
         else if(ref.getOrder() == StyleConstants.SORT_DESC) {
            out.put(name, "desc");
         }
      }

      return out;
   }

   private static FieldRef withVisibility(FieldRef field, boolean visible) {
      return new FieldRef(field.column(), field.type(), field.aggregate(), field.dateLevel(),
                          field.namedGroup(), field.chartType(), field.runtimeChartType(),
                          field.namedGroupValues(), field.calculateInfo(), field.label(),
                          field.secondaryY(), visible, field.timeSeries(),
                          field.secondaryColumn(), field.operand());
   }

   private static Map<String, Object> describeOptions(BaseTableBindingModel model) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(model instanceof CrosstabBindingModel crosstab && crosstab.getOption() != null) {
         out.put("rowTotals", crosstab.getOption().getRowTotalVisibleValue());
         out.put("colTotals", crosstab.getOption().getColTotalVisibleValue());
         out.put("percentageBy",
                 TableBindingMutator.percentageByName(
                    crosstab.getOption().getPercentageByValue()));
         out.put("summarySideBySide", crosstab.getOption().isSummarySideBySide());
      }
      else if(model instanceof TableBindingModel table && table.getOption() != null) {
         out.put("grandTotal", table.getOption().getGrandTotal());
         out.put("distinct", table.getOption().getDistinct());
      }

      return out;
   }

   private List<String> apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation) throws Exception
   {
      return apply(sessionToken, user, assemblyName, mutation, false, true);
   }

   /**
    * A shelf mutation that also needs the runtime context {@link TableBindingMutator#setShelf}
    * threads through to resolve a field's {@code namedGroup} -- unlike the plain {@link Consumer}
    * overload above, which reapplies a shelf with no context and so silently drops an
    * already-resolved {@code namedGroup} on any field the mutation doesn't itself touch.
    */
   @FunctionalInterface
   private interface ContextualShelfMutation {
      void accept(BaseTableBindingModel model, RuntimeViewsheet rvs,
                  inetsoft.uql.asset.SourceInfo source) throws Exception;
   }

   /**
    * A Table details write that names a currently hidden column shows it again (bug #76820: the
    * whole-shelf write cannot tell a re-add from a read-back round trip), and nothing in the
    * write's own result says so. Evaluated against the hidden set as it is BEFORE the write, keyed
    * on the column's attribute name rather than its alias.
    */
   static void relayUnhiddenNotes(String assemblyName, VSAssembly assembly, String shelf,
                                  List<FieldRef> written, CommandDispatcher dispatcher)
   {
      if(!(assembly instanceof TableVSAssembly table) || !"details".equals(shelf) ||
         written == null)
      {
         return;
      }

      Set<String> seen = new HashSet<>();

      for(FieldRef field : written) {
         if(field != null && field.column() != null && isHidden(table, field.column()) &&
            seen.add(field.column().toLowerCase()))
         {
            MessageCommand command = new MessageCommand();
            command.setType(MessageCommand.Type.WARNING);
            command.setMessage(TableBindingMutator.WARNING_PREFIX + "Column '" + field.column() +
               "' was hidden and is shown again: a hidden column named in a details write is " +
               "shown. Leave it out of the list to keep it hidden.");
            dispatcher.sendCommand(assemblyName, command);
         }
      }
   }

   /**
    * Reports what the mutator reset on its own as WARNING messages, so the caller is told rather
    * than finding a sort or ranking gone (bug #77025 review F1).
    */
   private static void relayNotes(String assemblyName, CommandDispatcher dispatcher) {
      for(String note : TableBindingMutator.drainNotes()) {
         MessageCommand command = new MessageCommand();
         command.setType(MessageCommand.Type.WARNING);
         command.setMessage(note);
         dispatcher.sendCommand(assemblyName, command);
      }
   }

   /**
    * Warns, on a write that can change the crosstab's shape, when the result would render a
    * PERCENT aggregate as 100% in every cell. Deliberately not called by the labels, widths and
    * column-visibility writes, which cannot change the shape.
    */
   private static void relayShapeWarning(String assemblyName, BaseTableBindingModel model,
                                         CommandDispatcher dispatcher)
   {
      String warning = TableBindingMutator.degeneratePercentWarning(model);

      if(warning != null) {
         MessageCommand command = new MessageCommand();
         command.setType(MessageCommand.Type.WARNING);
         command.setMessage(warning);
         dispatcher.sendCommand(assemblyName, command);
      }
   }

   private List<String> applyWithContext(String sessionToken, Principal user, String assemblyName,
                                 String shelf, ContextualShelfMutation mutation) throws Exception
   {
      return applyWithContext(sessionToken, user, assemblyName, shelf, List.of(), mutation);
   }

   /** @param unhiddenCandidates the fields a details write is about to name, for relayUnhiddenNotes */
   private List<String> applyWithContext(String sessionToken, Principal user, String assemblyName,
                                 String shelf, List<FieldRef> unhiddenCandidates,
                                 ContextualShelfMutation mutation) throws Exception
   {
      return sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = assembly instanceof DataVSAssembly data
            ? data.getSourceInfo() : null;
         String livePercentageBy = livePercentageByValue(assembly);
         TableBindingMutator.drainNotes();
         mutation.accept(model, rvs, source);
         relayUnhiddenNotes(assemblyName, assembly, shelf, unhiddenCandidates, dispatcher);
         TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);
         relayNotes(assemblyName, dispatcher);
         relayShapeWarning(assemblyName, model, dispatcher);

         Map<String, ColumnOption> formOptions = captureFormOptions(assembly);
         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
         // See restoreFormRefInvariant's own doc -- addField is the other wiz entry point
         // (besides setShelf) that can introduce a brand-new column onto a Table's details
         // shelf, so it needs the same safety net. removeField/moveField never introduce a new
         // column, but passing their shelf through too is cheap and keeps all three callers of
         // this shared method uniform.
         restoreFormRefInvariant(assembly, shelf, formOptions);
      });
   }

   private List<String> apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation, boolean allowCalcTable)
      throws Exception
   {
      return apply(sessionToken, user, assemblyName, mutation, allowCalcTable, true);
   }

   /**
    * @param preservePercentageBy see {@link TableBindingMutator#preserveUntouchedPercentageBy} --
    *                             {@code false} only for {@link #setOptions}, the one wiz call
    *                             allowed to actually change {@code percentageBy}.
    */
   private List<String> apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation, boolean allowCalcTable,
                      boolean preservePercentageBy)
      throws Exception
   {
      return sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName, allowCalcTable);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         String livePercentageBy = preservePercentageBy ? livePercentageByValue(assembly) : null;
         TableBindingMutator.drainNotes();
         mutation.accept(model);
         relayNotes(assemblyName, dispatcher);

         if(preservePercentageBy) {
            TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);
         }

         relayShapeWarning(assemblyName, model, dispatcher);
         applyBinding(rvs, runtimeId, assemblyName, model, user, dispatcher);
      });
   }

   /**
    * The one {@code setbinding} every wiz table write ends in.
    *
    * <p>The trap flag is left at its default of true. A trap means the binding produces a
    * cartesian or otherwise invalid result, and turning it off to make the call succeed would be
    * trading a reported problem for an unreported one.
    *
    * <p>Bug #78530: a crosstab row label is not binding state but a positional {@code
    * MESSAGE_FORMAT} override on the corner header path {@code Cell [0,i]}, which {@code
    * VSUtil.syncCrosstabPath} re-keys as the rows change. Without a clear-alias processor a
    * vacated path kept its label: the corner went on showing a removed field's label, and the
    * next field at that position inherited it (and get_table_binding reported it as that field's
    * label). Two parts, together the Composer's own drag-and-drop and replace behaviour:
    * <ul>
    *   <li>{@link ApplyVSAssemblyInfoEvent#setClearVacatedAliases} -- the same {@code
    *   processClearAliasFormat} native drag-and-drop passes, which clears every path left
    *   behind once survivors have been re-keyed (a removal, and the shift it leaves);</li>
    *   <li>{@link #clearLabelsOfRowsLeaving} before the write, for what the processor never
    *   sees: a row moved to cols (no branch in {@code syncCornerPath}) and a one-call replace
    *   ({@code buildMap}'s runtime-ID fallback maps the dropped row onto the newcomer).</li>
    * </ul>
    * Must run after the mutation's last possible throw, so a refused write clears nothing.
    */
   private void applyBinding(RuntimeViewsheet rvs, String runtimeId, String assemblyName,
                             BindingModel model, Principal user, CommandDispatcher dispatcher)
      throws Exception
   {
      VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);

      if(assembly instanceof CrosstabVSAssembly crosstab &&
         model instanceof CrosstabBindingModel crosstabModel)
      {
         clearLabelsOfRowsLeaving(crosstab, crosstabModel);
      }

      ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
      event.setName(assemblyName);
      event.setBinding(model);
      event.setClearVacatedAliases(true);
      bindingModelService.setBinding(runtimeId, event, user, dispatcher);
   }

   /**
    * Clears, on the live crosstab's {@code FormatInfo}, the label of every current row dimension
    * that this write takes off rows -- removed, replaced by a different column, or moved to cols
    * or aggregates -- at its own row index, as native {@code VSCrosstabBindingHandler.addDataRef}
    * does for a replace. Uses the clone-and-put {@code clearAlias}, never the in-place {@code
    * clearHeaderAlias}, whose format object may be shared with the undo checkpoint.
    *
    * <p>"Leaving" is decided on design values on both sides (the live ref through the same
    * {@code BDimensionRefModel} the read builds), never on the live ref's runtime full name,
    * which carries a runtime date level or period prefix. A row stays when a new row has its
    * column, date level and named group, or when the new row at its own index has its column --
    * a date level or named group changed in place keeps its label, as it does today.
    *
    * <p>The index is the row's position in {@code getRowHeaders()}, unshifted: {@code
    * syncCornerPath}, {@code buildMap} and the wiz label writer all key {@code Cell [0,i]} by the
    * runtime row index, and the runtime rows never hold the period-calendar dimension. When a
    * variable-expanded dimension makes the design and runtime rows differ in length, the design
    * index is not the path index, so nothing is pre-cleared and the processor alone applies.
    */
   private static void clearLabelsOfRowsLeaving(CrosstabVSAssembly crosstab,
                                                CrosstabBindingModel model)
   {
      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      FormatInfo formatInfo = crosstab.getFormatInfo();
      DataRef[] rows = info == null ? null : info.getRowHeaders();

      if(rows == null || rows.length == 0 || formatInfo == null) {
         return;
      }

      DataRef[] runtimeRows = info.getRuntimeRowHeaders();

      if(runtimeRows == null || runtimeRows.length != rows.length) {
         return;
      }

      List<BDimensionRefModel> next = model.getRows() == null ? List.of() : model.getRows();
      boolean[] taken = new boolean[next.size()];
      boolean[] stays = new boolean[rows.length];
      BDimensionRefModel[] designs = new BDimensionRefModel[rows.length];

      for(int i = 0; i < rows.length; i++) {
         if(rows[i] instanceof VSDimensionRef dim) {
            designs[i] = new BDimensionRefModel(dim);
            designs[i].setColumnValue(dim.getGroupColumnValue());
         }
      }

      for(int i = 0; i < rows.length; i++) {
         for(int j = 0; designs[i] != null && j < next.size(); j++) {
            if(!taken[j] && sameDesign(designs[i], next.get(j))) {
               taken[j] = stays[i] = true;
               break;
            }
         }
      }

      for(int i = 0; i < rows.length; i++) {
         if(!stays[i] && designs[i] != null && i < next.size() && !taken[i] &&
            sameColumn(designs[i], next.get(i)))
         {
            taken[i] = stays[i] = true;
         }
      }

      for(int i = 0; i < rows.length; i++) {
         if(!stays[i] && designs[i] != null) {
            ClearTableHeaderAliasHandler.clearAlias(rows[i], formatInfo, i);
         }
      }
   }

   private static boolean sameColumn(BDimensionRefModel a, BDimensionRefModel b) {
      return a.getColumnValue() != null && a.getColumnValue().equalsIgnoreCase(b.getColumnValue());
   }

   private static boolean sameDesign(BDimensionRefModel a, BDimensionRefModel b) {
      return sameColumn(a, b) && Objects.equals(designLevel(a), designLevel(b)) &&
         Objects.equals(namedGroupKey(a), namedGroupKey(b));
   }

   /**
    * The design date level, with "none" spelled one way: the live ref reads back {@code null},
    * while a dimension the mutator writes back carries {@code DateLevels}' {@code "-1"} sentinel.
    */
   private static String designLevel(BDimensionRefModel ref) {
      String level = ref.getDateLevel();
      return level == null || level.isBlank() || "-1".equals(level.trim()) ? null : level.trim();
   }

   private static String namedGroupKey(BDimensionRefModel ref) {
      NamedGroupInfoModel group = ref.getNamedGroupInfo();
      return group == null || group.getType() == 0 ? null : group.getType() + ":" + group.getName();
   }

   /**
    * The live crosstab's own, never-manufactured {@code percentageBy}, read directly off the
    * real assembly before this call's mutation runs -- {@code null} for a crosstab that has
    * never had {@code set_table_options(percentageBy: ...)} called on it (or for anything that
    * is not a crosstab). See {@link TableBindingMutator#preserveUntouchedPercentageBy}.
    */
   private static String livePercentageByValue(VSAssembly assembly) {
      if(!(assembly instanceof CrosstabVSAssembly crosstab)) {
         return null;
      }

      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      return info == null ? null : info.getPercentageByValue();
   }

   private BaseTableBindingModel requireTableBinding(RuntimeViewsheet rvs, String assemblyName) {
      return requireTableBinding(rvs, assemblyName, false);
   }

   /**
    * @param allowCalcTable calc tables are refused for <b>shelf</b> operations, because their
    *                       binding lives in their cell layout. Assigning a <b>source</b> is not a
    *                       shelf operation: a {@code CalcTableVSAssembly} is a
    *                       {@code TableDataVSAssembly} and carries a source like any other, and
    *                       without one a freehand table renders empty however its cells are bound.
    */
   private BaseTableBindingModel requireTableBinding(RuntimeViewsheet rvs, String assemblyName,
                                                     boolean allowCalcTable)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(allowCalcTable && assembly instanceof CalcTableVSAssembly) {
         BindingModel calcModel = binding.createModel(assembly);

         if(calcModel instanceof BaseTableBindingModel calcTable) {
            return calcTable;
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table whose binding model cannot carry a source.");
      }

      refuseCalcTable(assembly, assemblyName);
      refuseNonTableAssembly(assembly, assemblyName);
      BindingModel model = binding.createModel(assembly);

      if(!(model instanceof BaseTableBindingModel table)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a table or crosstab. Use the chart binding tools for charts.");
      }

      completeExpertNamedGroups(table, assembly);
      return table;
   }

   /**
    * The type refusal of {@link #requireTableBinding} without building a binding model, for the
    * endpoints that resolve their fields against the source before reaching this service. Run
    * first, it makes those tools refuse a non-table by its type rather than with a source error.
    */
   public void requireTableAssembly(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      refuseCalcTable(assembly, assemblyName);
      refuseNonTableAssembly(assembly, assemblyName);
   }

   private static void refuseCalcTable(VSAssembly assembly, String assemblyName) {
      if(assembly instanceof CalcTableVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table. Its binding lives in its cell layout, " +
            "not its shelves — use the calc-table tools instead.");
      }
   }

   /**
    * Refuses every assembly the table tools cannot bind. It must run before
    * {@code VSBindingService.createModel}, which has factories for only a few types and throws a
    * raw "No model factory registered" error for every other one (bug #78533).
    */
   private static void refuseNonTableAssembly(VSAssembly assembly, String assemblyName) {
      if(assembly instanceof TableVSAssembly || assembly instanceof CrosstabVSAssembly) {
         return;
      }

      if(assembly instanceof ChartVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a chart, not a table or crosstab. " +
            "Use the chart binding tools for charts.");
      }

      String type;

      if(assembly instanceof Viewsheet) {
         type = "an embedded viewsheet";
      }
      else {
         type = assembly.getClass().getSimpleName();

         if(type.endsWith("VSAssembly") && type.length() > "VSAssembly".length()) {
            type = type.substring(0, type.length() - "VSAssembly".length());
         }

         type = ("AEIOU".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' is " + type + ", not a table or crosstab. " +
         "The table tools only work on Table and Crosstab assemblies.");
   }

   /**
    * {@code NamedGroupInfoModel(XNamedGroupInfo)} reads an {@code ExpertNamedGroupInfo} as its
    * flat member values only -- {@code conditions} stays empty -- so a model written back
    * unchanged is rebuilt through {@code createNamedGroupInfo}'s {@code groups} branch, which
    * flattens every operator (STARTING_WITH, ...) to EQUAL_TO. That happens on every wiz write to
    * the crosstab, not only one that touches the dimension's own shelf (bug #78362). Completing
    * the conditions here, before any mutation, makes an untouched dimension round-trip as stored.
    *
    * <p>Pairing is by index, which is safe because {@code VSCrosstabBindingFactory.createModel}
    * adds exactly one model per header, in order, from the same live assembly. Restricted to
    * EXPERT: {@code fixNamedGroupInfoModel} forces the type to EXPERT, which would break the
    * inline-group read of a SIMPLE group.
    */
   private void completeExpertNamedGroups(BaseTableBindingModel table, VSAssembly assembly) {
      if(!(table instanceof CrosstabBindingModel crosstabModel) ||
         !(assembly instanceof CrosstabVSAssembly crosstab) ||
         crosstab.getVSCrosstabInfo() == null)
      {
         return;
      }

      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      completeExpertNamedGroups(crosstabModel.getRows(), info.getRowHeaders());
      completeExpertNamedGroups(crosstabModel.getCols(), info.getColHeaders());
   }

   private void completeExpertNamedGroups(List<BDimensionRefModel> models,
                                          DataRef[] headers)
   {
      if(models == null || headers == null) {
         return;
      }

      for(int i = 0; i < models.size() && i < headers.length; i++) {
         if(headers[i] instanceof VSDimensionRef dim &&
            dim.getNamedGroupInfo() instanceof ExpertNamedGroupInfo expert &&
            models.get(i).getNamedGroupInfo() != null)
         {
            models.get(i).getNamedGroupInfo().fixNamedGroupInfoModel(expert, refModelService);
         }
      }
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final VSBindingModelService bindingModelService;
   private final DataRefModelFactoryService refModelService;
   private final HideColumnsDialogService hideColumnsService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
