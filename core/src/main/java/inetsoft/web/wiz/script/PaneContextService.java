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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.binding.BindableFieldsService;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.PaneContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * The read-only inventory behind {@code get_pane_context}: what the formula/script dialog's own
 * tree shows (components, parameters, columns, functions) plus the syntax the location is
 * evaluated in, served the same way for every pane kind and for a whole-sheet session.
 *
 * <p>Every part is projected from the sources the Composer's own editor tree reads
 * ({@link VSScriptableService#getScriptDefinition}, {@link BindableFieldsService}, the function
 * catalogue), reduced to names and types. Nothing here is addressed by an identifier a composer
 * service accepts, and nothing is written, so a pane token needs no wider grant to ask: the
 * location the session is bound to only decides WHICH columns are listed and WHICH syntax is
 * described, never whether the question may be asked.</p>
 */
@Service
public class PaneContextService {
   private static final Logger LOG = LoggerFactory.getLogger(PaneContextService.class);

   static final String SHEET_KIND = "sheet";

   @Autowired
   public PaneContextService(ScriptContextService contextService, ScriptApiService apiService,
                             VSScriptableService scriptableService,
                             BindableFieldsService fieldsService)
   {
      this.contextService = contextService;
      this.apiService = apiService;
      this.scriptableService = scriptableService;
      this.fieldsService = fieldsService;
   }

   /**
    * @param rvs     the session's runtime, already authorized through the pairing grant
    * @param session the live session; its editor context decides the columns and the syntax
    * @param user    the agent principal, as every other binding read passes it
    */
   public PaneContext paneContext(RuntimeViewsheet rvs, JoinSession session, Principal user)
      throws PairingException
   {
      EditorContext grant = session.editorContext();
      List<String> notes = new ArrayList<>();

      List<PaneContext.Component> components = contextService.context(rvs, null).assemblies()
         .stream().map(a -> new PaneContext.Component(a.name(), a.type())).toList();

      return new PaneContext(
         grant == null ? SHEET_KIND : grant.kind(), grant, syntaxOf(grant), components,
         parameters(rvs, notes), columns(session, grant, user, notes), apiService.functionNames(),
         notes);
   }

   /** The parameter names the editor's own "parameter" node lists, sorted, internals dropped. */
   private List<String> parameters(RuntimeViewsheet rvs, List<String> notes) {
      try {
         ObjectNode root = scriptableService.getScriptDefinition(rvs, null, null, false);
         JsonNode node = root.get("parameter");
         List<String> names = new ArrayList<>();

         if(node != null) {
            for(Iterator<String> it = node.fieldNames(); it.hasNext();) {
               String name = it.next();

               // "!type"-style metadata keys and the runtime's own "__principal__" slot are not
               // parameters a script author can name.
               if(!name.startsWith("!") && !name.startsWith("__")) {
                  names.add(name);
               }
            }
         }

         names.sort(null);
         return names;
      }
      catch(Exception e) {
         LOG.warn("Could not list the parameters for a pane context", e);
         notes.add("The parameter list could not be read: " + messageOf(e));
         return List.of();
      }
   }

   /**
    * The columns the location reads: a calc field's bound table, the bound table of the component
    * that owns an assembly script / dynamic value / condition / calc cell, or every table for a
    * viewsheet-level script and a whole-sheet session.
    */
   private List<PaneContext.Table> columns(JoinSession session, EditorContext grant,
                                           Principal user, List<String> notes)
   {
      String owner = ownerOf(grant);
      boolean calcField = grant != null &&
         ScriptTarget.Kind.CALC_FIELD.wireName().equals(grant.kind());

      try {
         List<BindableTable> tables;

         if(owner == null || calcField) {
            tables = fieldsService.list(session.runtimeId(), null, user);

            if(calcField) {
               List<BindableTable> own = tables.stream()
                  .filter(t -> Objects.equals(t.name(), owner)).toList();

               if(own.isEmpty()) {
                  notes.add("Table '" + owner + "' is not among the viewsheet's tables, so every " +
                            "table's columns are listed.");
               }
               else {
                  tables = own;
               }
            }
         }
         else {
            tables = fieldsService.list(session.runtimeId(), owner, user);
            List<BindableTable> current = tables.stream()
               .filter(t -> Boolean.TRUE.equals(t.current())).toList();

            // The scoped listing offers every source the component could be repointed to; the one
            // it is bound to is what the location reads.
            tables = current.isEmpty() ? tables : current;
         }

         return tables.stream().map(PaneContextService::project).toList();
      }
      catch(Exception e) {
         LOG.warn("Could not list the columns for a pane context", e);
         notes.add("The column list could not be read: " + messageOf(e));
         return List.of();
      }
   }

   private static PaneContext.Table project(BindableTable t) {
      List<PaneContext.Field> fields = t.fields().stream()
         .map(f -> new PaneContext.Field(f.column(), f.dataType())).toList();

      return new PaneContext.Table(t.name(), t.current(), fields);
   }

   /**
    * The table (calc field) or component (everything else) the location belongs to; null for a
    * viewsheet-level script and a whole-sheet session. Both {@code assembly} and {@code table}
    * are accepted, as {@link PaneScopeService#matchesGrant} accepts them.
    */
   static String ownerOf(EditorContext grant) {
      if(grant == null) {
         return null;
      }

      String owner = grant.table() != null ? grant.table() : grant.assembly();
      return owner == null || owner.isBlank() ? null : owner;
   }

   /** How to write the expression at this location. */
   static PaneContext.Syntax syntaxOf(EditorContext grant) {
      if(grant == null) {
         return new PaneContext.Syntax("javascript",
            "Whole-sheet session: no single location. Each script location is JavaScript; ask " +
            "for list_script_targets to see them.");
      }

      if(EditorContext.CALC_TABLE_CELL.equals(grant.kind())) {
         return new PaneContext.Syntax("javascript",
            "A freehand-table cell script. Another cell's computed value is referenced as " +
            "$<name> after naming that cell; a bareword identifier is not in scope.");
      }

      ScriptTarget.Kind kind;

      try {
         kind = ScriptTarget.Kind.fromWire(grant.kind());
      }
      catch(PairingException e) {
         return new PaneContext.Syntax("javascript", "Unrecognised location kind '" +
            grant.kind() + "'; treated as a JavaScript script.");
      }

      return switch(kind) {
         case DYNAMIC_VALUE -> new PaneContext.Syntax("javascript",
            "An Expression-mode dynamic value is JavaScript, never the $(Name) shorthand: $ is " +
            "not defined here, so $(Name) or any call such as String($(Name)) fails at run time " +
            "with 'ReferenceError: $ is not defined'. Reach a component by its name and a " +
            "parameter as parameter.<name>. $(Name) works only when the field's type is Variable.");
         case VS_CONDITION_VALUE -> new PaneContext.Syntax("javascript",
            "An Expression condition value is a JavaScript expression, parsed but not run when " +
            "pushed.");
         case CALC_FIELD -> new PaneContext.Syntax("javascript",
            "A calc field formula is JavaScript evaluated per row: read a column as " +
            "field['<column>'] and a parameter as parameter.<name>. No component or " +
            "thisViewsheet is in scope.");
         case VIEWSHEET_ON_INIT, VIEWSHEET_ON_LOAD, ASSEMBLY_MAIN, ASSEMBLY_ON_CLICK ->
            new PaneContext.Syntax("javascript",
               "A script: reach a component by its name and a parameter as parameter.<name>.");
         default -> new PaneContext.Syntax("javascript",
            "A worksheet-side expression: not served by this viewsheet context.");
      };
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private final ScriptContextService contextService;
   private final ScriptApiService apiService;
   private final VSScriptableService scriptableService;
   private final BindableFieldsService fieldsService;
}
