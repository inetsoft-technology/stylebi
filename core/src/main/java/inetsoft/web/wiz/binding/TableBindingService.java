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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.ClearTableHeaderAliasHandler;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CalcTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.binding.model.ColumnLabelEntry;
import inetsoft.web.wiz.binding.model.FieldRef;
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
                              HideColumnsDialogService hideColumnsService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.bindingModelService = bindingModelService;
      this.refModelService = refModelService;
      this.hideColumnsService = hideColumnsService;
   }

   /**
    * @param sourceTable the table to point the assembly at as part of this write, or {@code null}
    *                    to leave its source alone. A crosstab/table with no source renders nothing
    *                    however correctly its shelves are filled in — see {@link #setSource} — and
    *                    this establishes it the same way {@code ChartBindingService.setShelf} does
    *                    for a chart, one call rather than two.
    */
   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields, String sourceTable) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         applySource(model, sourceTable);
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs.getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = assembly instanceof DataVSAssembly data
            ? data.getSourceInfo() : null;
         requireSourceForFieldWrite(assemblyName, shelf, model.getSource(),
                                    fields == null ? 0 : fields.size());
         String livePercentageBy = livePercentageByValue(assembly);
         TableBindingMutator.setShelf(model, shelf, fields, rvs, source, refModelService);
         TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
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
   public void setSource(String sessionToken, Principal user, String assemblyName,
                         String table, boolean force) throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_table_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this assembly can bind to.");
      }

      apply(sessionToken, user, assemblyName, model -> {
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
   public void addField(String sessionToken, Principal user, String assemblyName, String shelf,
                        FieldRef field, Integer position, String sourceTable) throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) -> {
            applySource(model, sourceTable);
            requireSourceForFieldWrite(assemblyName, shelf, model.getSource(),
                                       field == null ? 0 : 1);
            TableBindingMutator.addField(model, shelf, field, position, rvs, source,
                                         refModelService);
         });
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

   public void removeField(String sessionToken, Principal user, String assemblyName,
                           String shelf, String column) throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) ->
            TableBindingMutator.removeField(model, shelf, column, rvs, source, refModelService));
   }

   public void moveField(String sessionToken, Principal user, String assemblyName,
                         String fromShelf, String toShelf, String column, Integer position)
      throws Exception
   {
      applyWithContext(sessionToken, user, assemblyName,
         (model, rvs, source) ->
            TableBindingMutator.moveField(model, fromShelf, toShelf, column, position, rvs,
                                          source, refModelService));
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
    * {@code assembly + column} contract does not map onto it without new resolution logic
    * (which pivoted occurrence(s) to hide) this call does not have.
    */
   public void setFieldVisibility(String sessionToken, Principal user, String assemblyName,
                                  String column, boolean visible, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(!(liveAssembly instanceof TableVSAssembly)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " +
               (liveAssembly instanceof CrosstabVSAssembly ? "a Crosstab" : "not a Table") +
               " -- set_table_field_visibility only supports Table right now. Crosstab " +
               "hide/show is a separate, not-yet-built capability.");
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

   public void setSort(String sessionToken, Principal user, String assemblyName, String shelf,
                       String column, Integer index, DimensionSortRanking.Sort sort)
      throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setSort(model, shelf, column, index, sort));
   }

   public void setRanking(String sessionToken, Principal user, String assemblyName, String shelf,
                          String column, Integer index, DimensionSortRanking.Ranking ranking)
      throws Exception
   {
      apply(sessionToken, user, assemblyName,
            model -> TableBindingMutator.setRanking(model, shelf, column, index, ranking));
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
   public List<String> setColumnLabels(String sessionToken, Principal user, String assemblyName,
                                       Map<String, String> labels,
                                       List<ColumnLabelEntry> entries) throws Exception
   {
      List<String> applied = new ArrayList<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         TableBindingMutator.ColumnLabelWrite write =
            TableBindingMutator.setColumnLabels(model, labels, entries);

         if(!write.tableRenames().isEmpty() && assembly instanceof TableVSAssembly table) {
            rekeyTableFormatWidthAndHighlight(table, write.tableRenames());

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

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });

      return applied;
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
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         throw new IllegalStateException(
            "'" + crosstab.getAbsoluteName() + "' has no active render sandbox right now, so " +
            "its header cannot be resolved to a cell.");
      }

      String oname = crosstab.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      VSTableLens lens = box.get().getVSTableLens(oname, detail);
      VSCrosstabInfo crossInfo = crosstab.getVSCrosstabInfo();
      FormatInfo formatInfo = crosstab.getFormatInfo();

      for(TableBindingMutator.CrosstabTarget target : targets) {
         DataRef ref = liveCrosstabRef(crossInfo, target.shelf(), target.index());

         if(ref == null) {
            throw new IllegalArgumentException(
               "'" + target.shelf() + "[" + target.index() + "]' no longer resolves on the " +
               "live assembly -- the binding may have changed since this call was validated.");
         }

         if(target.label().isEmpty()) {
            ClearTableHeaderAliasHandler.clearAlias(ref, formatInfo, target.index());
            continue;
         }

         TableDataPath path = SetTableHeaderAliasHandler.findHeaderPath(lens, ref, target.index());

         if(path == null) {
            throw new IllegalArgumentException(
               "Could not find '" + target.shelf() + "[" + target.index() + "]' on the " +
               "rendered header of '" + crosstab.getAbsoluteName() + "' -- it may not currently " +
               "render (suppressed, filtered out, or hidden by the crosstab's current shape).");
         }

         SetTableHeaderAliasHandler.setAliasWithHeaderDuality(path, formatInfo, target.label());
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
    * row 0 alone. This is not a shelf/index lookup the way a {@code ColumnLabelEntry} resolves a
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
   public List<String> setColumnWidths(String sessionToken, Principal user, String assemblyName,
                                       Map<String, Double> widths) throws Exception
   {
      if(widths == null || widths.isEmpty()) {
         throw new IllegalArgumentException(
            "setColumnWidths requires a non-empty 'widths' object.");
      }

      List<String> applied = new ArrayList<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

         if(box.isEmpty()) {
            throw new IllegalStateException(
               "'" + assemblyName + "' has no active render sandbox right now, so its columns " +
               "cannot be resolved.");
         }

         String oname = assembly.getAbsoluteName();
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSTableLens lens = box.get().getVSTableLens(oname, detail);

         if(lens == null) {
            throw new IllegalStateException(
               "'" + assemblyName + "' did not render -- its columns cannot be resolved right " +
               "now.");
         }

         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();

         int headerRows = lens.getHeaderRowCount();
         int headerCols = lens.getHeaderColCount();
         int colCount = lens.getColCount();
         int rowCount = lens.getRowCount();

         for(Map.Entry<String, Double> entry : widths.entrySet()) {
            String column = entry.getKey();
            Double width = entry.getValue();
            List<Integer> matches = new ArrayList<>();

            scanForColumnMatch(lens, column, 0, headerRows, 0, colCount, matches);
            scanForColumnMatch(lens, column, headerRows, rowCount, 0, headerCols, matches);

            if(matches.isEmpty()) {
               throw new IllegalArgumentException(
                  "'" + column + "' is not a visible column on '" + assemblyName + "' right " +
                  "now.");
            }

            if(matches.size() > 1) {
               throw new IllegalArgumentException(
                  "'" + column + "' matches " + matches.size() + " rendered columns on '" +
                  assemblyName + "' -- width cannot be set by name when it's ambiguous.");
            }

            int col = matches.get(0);

            if(width == null) {
               info.setColumnWidthValue(col, Double.NaN);
               applied.add(column + " -> auto");
            }
            else {
               if(!Double.isFinite(width) || width <= 0) {
                  throw new IllegalArgumentException(
                     "'" + column + "' width must be a finite, positive number of pixels; got " +
                     width + ".");
               }

               info.setColumnWidthValue2(col, width, lens);
               applied.add(column + " -> " + formatPixels(width) + "px");
            }
         }

         info.setExplicitTableWidthValue(true);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });

      return applied;
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
    */
   private static void scanForColumnMatch(VSTableLens lens, String column, int rowStart,
                                          int rowEnd, int colStart, int colEnd,
                                          List<Integer> matches)
   {
      for(int row = rowStart; row < rowEnd; row++) {
         for(int col = colStart; col < colEnd; col++) {
            Object val = lens.getObject(row, col);

            if(Objects.equals(column, val == null ? null : val.toString()) &&
               !matches.contains(col))
            {
               matches.add(col);
            }
         }
      }
   }

   public void setOptions(String sessionToken, Principal user, String assemblyName,
                          Map<String, Object> options) throws Exception
   {
      // Unlike every other mutator below, this one is allowed to actually change percentageBy
      // — but only when this call's own options map carries that key. TableBindingMutator
      // .setCrosstabOptions only calls setPercentageByValue when options.containsKey
      // ("percentageBy"); an options-only write that omits it (e.g. {"rowTotals": true}) must
      // still have the manufactured default reset, or it reopens this same bug through a
      // narrower trigger.
      boolean changesPercentageBy = options != null && options.containsKey("percentageBy");
      apply(sessionToken, user, assemblyName,
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

      if(model instanceof CrosstabBindingModel) {
         VSAssembly liveAssembly = rvs.getViewsheet().getAssembly(assemblyName);

         if(liveAssembly instanceof CrosstabVSAssembly crosstab) {
            enrichCrosstabLabels(rvs, crosstab, shelfFields);
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

      out.put("shelves", new LinkedHashMap<>(shelfFields));
      putColumnLabels(out, shelfFields);
      Map<String, Object> sorts = new LinkedHashMap<>();

      for(String shelf : TableBindingMutator.shelvesOf(model)) {
         if(!"aggregates".equals(shelf) && !"details".equals(shelf)) {
            sorts.putAll(TableBindingMutator.describeSorts(model, shelf));
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
    * bound column's header {@code TableDataPath}, which only a rendered {@code VSTableLens} can
    * resolve (see {@code TableBindingMutator.CrosstabTarget}'s javadoc — the same reason the
    * write side needs one). Swallows any render failure and leaves every {@code label} at its
    * default {@code null} rather than failing a read that would otherwise succeed — matching this
    * codebase's established fail-open stance for anything that needs a live render just to
    * disclose more, not to validate.
    */
   private static void enrichCrosstabLabels(RuntimeViewsheet rvs, CrosstabVSAssembly crosstab,
                                            Map<String, List<FieldRef>> shelfFields)
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

         for(String shelf : List.of("rows", "cols", "aggregates")) {
            List<FieldRef> fields = shelfFields.get(shelf);

            if(fields == null) {
               continue;
            }

            for(int i = 0; i < fields.size(); i++) {
               DataRef ref = liveCrosstabRef(crossInfo, shelf, i);

               if(ref == null) {
                  continue;
               }

               TableDataPath path = SetTableHeaderAliasHandler.findHeaderPath(lens, ref, i);
               String label = path == null ? null : readAlias(formatInfo, path);

               if(label != null) {
                  fields.set(i, withLabel(fields.get(i), label));
               }
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
                          field.secondaryY(), field.visible(), field.timeSeries());
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
    * hidden column {@code details} is missing, the same shown+hidden union {@link
    * HideColumnsDialogService}'s own {@code getAllColumns} builds for the write side's dialog
    * model. Without this merge a hidden field vanishes from {@code get_table_binding} instead of
    * reporting {@code visible: false} -- the exact silent-disappearance class of bug bug-76807
    * exists to close, just reopened on the read side.
    */
   private void enrichTableVisibility(TableVSAssembly table,
                                       Map<String, List<FieldRef>> shelfFields)
   {
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection hiddenCols = info.getHiddenColumns();
      Set<String> hidden = new HashSet<>();
      Enumeration<DataRef> refs = hiddenCols.getAttributes();

      while(refs.hasMoreElements()) {
         hidden.add(refs.nextElement().getAttribute());
      }

      List<FieldRef> details = shelfFields.get("details");
      List<FieldRef> enriched = details == null ? new ArrayList<>() : new ArrayList<>(details);
      Set<String> present = new HashSet<>();

      for(int i = 0; i < enriched.size(); i++) {
         FieldRef field = enriched.get(i);
         present.add(field.column());
         enriched.set(i, withVisibility(field, !hidden.contains(field.column())));
      }

      Enumeration<DataRef> hiddenRefs = hiddenCols.getAttributes();

      while(hiddenRefs.hasMoreElements()) {
         DataRef ref = hiddenRefs.nextElement();

         if(present.contains(ref.getAttribute())) {
            continue;
         }

         ColumnRef col = (ColumnRef) ref.clone();
         col.setApplyingAlias(false);
         ColumnRefModel model = (ColumnRefModel) refModelService.createDataRefModel(col);
         enriched.add(withVisibility(FieldRefFactory.from(model), false));
      }

      shelfFields.put("details", enriched);
   }

   private static FieldRef withVisibility(FieldRef field, boolean visible) {
      return new FieldRef(field.column(), field.type(), field.aggregate(), field.dateLevel(),
                          field.namedGroup(), field.chartType(), field.runtimeChartType(),
                          field.namedGroupValues(), field.calculateInfo(), field.label(),
                          field.secondaryY(), visible, field.timeSeries());
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

   private void apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation) throws Exception
   {
      apply(sessionToken, user, assemblyName, mutation, false, true);
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

   private void applyWithContext(String sessionToken, Principal user, String assemblyName,
                                 ContextualShelfMutation mutation) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         inetsoft.uql.asset.SourceInfo source = assembly instanceof DataVSAssembly data
            ? data.getSourceInfo() : null;
         String livePercentageBy = livePercentageByValue(assembly);
         mutation.accept(model, rvs, source);
         TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
   }

   private void apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation, boolean allowCalcTable)
      throws Exception
   {
      apply(sessionToken, user, assemblyName, mutation, allowCalcTable, true);
   }

   /**
    * @param preservePercentageBy see {@link TableBindingMutator#preserveUntouchedPercentageBy} --
    *                             {@code false} only for {@link #setOptions}, the one wiz call
    *                             allowed to actually change {@code percentageBy}.
    */
   private void apply(String sessionToken, Principal user, String assemblyName,
                      Consumer<BaseTableBindingModel> mutation, boolean allowCalcTable,
                      boolean preservePercentageBy)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         BaseTableBindingModel model = requireTableBinding(rvs, assemblyName, allowCalcTable);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         String livePercentageBy = preservePercentageBy ? livePercentageByValue(assembly) : null;
         mutation.accept(model);

         if(preservePercentageBy) {
            TableBindingMutator.preserveUntouchedPercentageBy(model, livePercentageBy);
         }

         ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
         event.setName(assemblyName);
         event.setBinding(model);
         // Left at its default of true. A trap means the binding produces a cartesian or
         // otherwise invalid result, and turning it off to make the call succeed would be
         // trading a reported problem for an unreported one.
         bindingModelService.setBinding(runtimeId, event, user, dispatcher);
      });
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

      if(assembly instanceof CalcTableVSAssembly) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a calc table. Its binding lives in its cell layout, " +
            "not its shelves — use the calc-table tools instead.");
      }

      BindingModel model = binding.createModel(assembly);

      if(!(model instanceof BaseTableBindingModel table)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a table or crosstab. Use the chart binding tools for charts.");
      }

      return table;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final VSBindingModelService bindingModelService;
   private final DataRefModelFactoryService refModelService;
   private final HideColumnsDialogService hideColumnsService;
}
