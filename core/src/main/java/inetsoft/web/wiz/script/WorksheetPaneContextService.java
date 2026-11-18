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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PaneState;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.worksheet.WorksheetReadService;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The worksheet-session counterpart of {@link PaneContextService} (Bug #81557): what a worksheet
 * formula dialog's own tree shows (the table's columns with types, the worksheet variables, the
 * functions of the dialog's current mode) plus the syntax the location is evaluated in.
 *
 * <p>Read-only and names-and-types only, exactly like the viewsheet side: nothing here is an
 * identifier a worksheet edit op accepts, so a pane token gains no reach beyond the one
 * expression it was minted for. The Script/SQL mode comes from the pane's reported
 * {@link PaneState}, the same channel the calculated field dialog uses.</p>
 */
@Service
public class WorksheetPaneContextService {
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetPaneContextService.class);

   @Autowired
   public WorksheetPaneContextService(WorksheetReadService readService,
                                      ScriptApiService apiService)
   {
      this.readService = readService;
      this.apiService = apiService;
   }

   /**
    * @param rws     the session's runtime worksheet, already authorized through the pairing grant
    * @param session the live session; its editor context decides the columns and the syntax
    * @param state   what the pane last reported about itself (its SQL/Script switch)
    */
   public PaneContext paneContext(RuntimeWorksheet rws, JoinSession session, PaneState state) {
      EditorContext grant = session.editorContext();
      boolean sql = PaneContextService.isSqlMode(grant, state);
      List<String> notes = new ArrayList<>();
      WorksheetModel model = null;

      try {
         model = readService.read(rws);
      }
      catch(Exception e) {
         LOG.warn("Could not read the worksheet for a pane context", e);
         notes.add("The worksheet could not be read, so no tables, columns or variables are " +
                   "listed: " +
                   (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      }

      List<WorksheetModel.TableModel> tables =
         model == null || model.tables() == null ? List.of() : model.tables();
      List<PaneContext.Component> components = tables.stream()
         .map(t -> new PaneContext.Component(t.name(), t.type())).toList();

      List<String> functions;
      List<String> operators = List.of();

      if(sql) {
         // The same fixed SQL lists the viewsheet calculated field context serves.
         functions = PaneContextService.SQL_FUNCTIONS;
         operators = PaneContextService.SQL_OPERATORS;
      }
      else {
         functions = apiService.functionNames();
      }

      if(grant != null && grant.pending()) {
         notes.add("This location is pending: the column does not exist until the user clicks OK " +
                   "in the dialog. Its table's columns are listed as they are now.");
      }

      return new PaneContext(
         grant == null ? PaneContextService.SHEET_KIND : grant.kind(), grant,
         syntaxOf(grant, sql), components, parameters(model), columns(tables, grant, notes),
         functions, operators, notes);
   }

   private static PaneContext.Syntax syntaxOf(EditorContext grant, boolean sql) {
      if(grant == null) {
         return new PaneContext.Syntax("javascript",
            "Whole-sheet worksheet session: no single location. Pair from a formula dialog for " +
            "its syntax, or use list_script_targets to see the worksheet's expression and " +
            "condition locations.");
      }

      return PaneContextService.syntaxOf(grant, sql);
   }

   /** The standard user variables plus the worksheet's own (non-hidden) variables. */
   private static List<String> parameters(WorksheetModel model) {
      Set<String> names = new LinkedHashSet<>(PaneContextService.STANDARD_PARAMETERS);

      if(model != null && model.variables() != null) {
         for(WorksheetModel.VariableModel v : model.variables()) {
            if(v.name() != null && !v.name().isBlank() && !Boolean.TRUE.equals(v.hidden())) {
               names.add(v.name());
            }
         }
      }

      return names.stream().sorted().toList();
   }

   /** The table the location belongs to, or every table when it names none or it is not found. */
   private static List<PaneContext.Table> columns(List<WorksheetModel.TableModel> tables,
                                                  EditorContext grant, List<String> notes)
   {
      String owner = PaneContextService.ownerOf(grant);

      if(owner != null) {
         List<WorksheetModel.TableModel> own = tables.stream()
            .filter(t -> Objects.equals(t.name(), owner)).toList();

         if(!own.isEmpty()) {
            return own.stream().map(t -> project(t, Boolean.TRUE)).toList();
         }

         notes.add("Table '" + owner + "' is not among the worksheet's tables, so every " +
                   "table's columns are listed.");
      }

      return tables.stream().map(t -> project(t, null)).toList();
   }

   private static PaneContext.Table project(WorksheetModel.TableModel t, Boolean current) {
      List<PaneContext.Field> fields = t.columns() == null ? List.of() : t.columns().stream()
         .map(c -> new PaneContext.Field(c.name(), c.type())).toList();

      return new PaneContext.Table(t.name(), current, fields);
   }

   private final WorksheetReadService readService;
   private final ScriptApiService apiService;
}
