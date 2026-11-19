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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.binding.BindableFieldsService;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.PaneState;
import inetsoft.web.wiz.script.model.PaneContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The read-only inventory behind {@code get_pane_context}: what the formula/script dialog's own
 * tree shows (components, parameters, columns, functions) plus the syntax the location is
 * evaluated in, served the same way for every pane kind and for a whole-sheet session.
 *
 * <p>Every part is projected from the sources the Composer's own editor tree reads (the
 * viewsheet's declared variables and runtime parameter table, {@link BindableFieldsService}, the
 * function catalogue), reduced to names and types. Nothing here is addressed by an identifier a composer
 * service accepts, and nothing is written, so a pane token needs no wider grant to ask: the
 * location the session is bound to only decides WHICH columns are listed and WHICH syntax is
 * described, never whether the question may be asked.</p>
 */
@Service
public class PaneContextService {
   private static final Logger LOG = LoggerFactory.getLogger(PaneContextService.class);

   static final String SHEET_KIND = "sheet";

   /**
    * The functions the calculated field dialog offers while it is on SQL, as the dialog's own
    * function tree lists them (formula-editor.service.ts, {@code sqlFunctions}), call syntax
    * {@code NAME(<column>)}.
    */
   static final List<String> SQL_FUNCTIONS = List.of("SUM", "COUNT", "AVG", "MIN", "MAX");

   /** The operators the dialog offers while it is on SQL ({@code sqlOperators} there). */
   static final List<String> SQL_OPERATORS =
      List.of("+", "-", "*", "/", "<", ">", "<=", ">=", "=", "<>");

   /** Always in scope as {@code parameter.<name>}: filled from the signed-in user. */
   static final List<String> STANDARD_PARAMETERS = List.of("_USER_", "_ROLES_", "_GROUPS_");

   @Autowired
   public PaneContextService(ScriptContextService contextService, ScriptApiService apiService,
                             ViewsheetService viewsheetService,
                             BindableFieldsService fieldsService)
   {
      this.contextService = contextService;
      this.apiService = apiService;
      this.viewsheetService = viewsheetService;
      this.fieldsService = fieldsService;
   }

   /**
    * @param rvs     the session's runtime, already authorized through the pairing grant
    * @param session the live session; its editor context decides the columns and the syntax
    * @param user    the agent principal, as every other binding read passes it
    * @param state   what the pane last reported about itself (its SQL/Script switch); only the
    *                location kinds that have such a switch read it
    */
   public PaneContext paneContext(RuntimeViewsheet rvs, JoinSession session, Principal user,
                                  PaneState state)
      throws PairingException
   {
      EditorContext grant = session.editorContext();
      boolean sql = isSqlMode(grant, state);
      boolean aggregate = isAggregateMode(grant, state);
      List<String> notes = new ArrayList<>();

      List<PaneContext.Component> components = contextService.context(rvs, null).assemblies()
         .stream().map(a -> new PaneContext.Component(a.name(), a.type())).toList();

      List<String> functions;
      List<String> operators = List.of();

      if(sql) {
         // The dialog's SQL lists are fixed (the aggregates and operators every data source
         // evaluates); the script function names must not be offered under a SQL syntax.
         functions = SQL_FUNCTIONS;
         operators = SQL_OPERATORS;
      }
      else {
         functions = apiService.functionNames();
      }

      return new PaneContext(
         grant == null ? SHEET_KIND : grant.kind(), grant, syntaxOf(grant, sql, aggregate), components,
         parameters(rvs, notes), columns(session, grant, user, notes), functions, operators, notes);
   }

   /** The kinds whose dialog has the SQL/Script switch: a calc field, a worksheet expression
    *  column and a worksheet condition's Expression value. */
   private static final List<String> SWITCHABLE_KINDS = List.of(
      ScriptTarget.Kind.CALC_FIELD.wireName(), ScriptTarget.Kind.WORKSHEET_EXPRESSION.wireName(),
      ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE.wireName());

   /**
    * Whether the pane is on SQL: only a calculated field formula, a worksheet expression column and
    * a worksheet condition value have the SQL/Script switch, so a state reported for any other
    * location is ignored.
    */
   static boolean isSqlMode(EditorContext grant, PaneState state) {
      return grant != null && SWITCHABLE_KINDS.contains(grant.kind()) &&
         state != null && state.sql();
   }

   /**
    * The parameters a script can read as {@code parameter.<name>}: the standard user variables,
    * the variables the viewsheet declares (what its own parameter prompt asks for), and the
    * parameters present on the runtime sheet. The editor's "parameter" scriptable is NOT used: it
    * also reports the JavaScript object's own {@code length} / {@code parameterNames} and the
    * runtime's per-component value slots (a RadioButton's name), none of which is a parameter.
    */
   private List<String> parameters(RuntimeViewsheet rvs, List<String> notes) {
      try {
         Viewsheet vs = rvs.getViewsheet();
         Set<String> declared = new LinkedHashSet<>();
         Set<String> runtime = new LinkedHashSet<>();
         Set<String> components = new LinkedHashSet<>();

         if(vs != null) {
            for(UserVariable variable : vs.getAllVariables()) {
               declared.add(variable.getName());
            }

            for(Assembly assembly : vs.getAssemblies()) {
               components.add(assembly.getName());
            }
         }

         Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

         if(box.isPresent()) {
            declared.addAll(promptedVariables(box.get(), vs, notes));
            runtime.addAll(runtimeParameters(box.get()));
         }

         return mergeParameterNames(declared, runtime, components);
      }
      catch(Exception e) {
         LOG.warn("Could not list the parameters for a pane context", e);
         notes.add("The parameter list could not be read: " + messageOf(e));
         return STANDARD_PARAMETERS.stream().sorted().toList();
      }
   }

   /** The variables the viewsheet's parameter prompt asks for; empty (with a note) on failure. */
   private List<String> promptedVariables(ViewsheetSandbox box, Viewsheet vs, List<String> notes) {
      List<UserVariable> prompted = new ArrayList<>();

      try {
         VSEventUtil.refreshParameters(viewsheetService, box, vs, false, null, prompted, true);
      }
      catch(Exception e) {
         LOG.debug("Could not read the viewsheet's prompted variables", e);
         notes.add("Declared variables that need a prompt could not be listed: " + messageOf(e));
      }

      return prompted.stream().filter(Objects::nonNull)
         .map(v -> v.getName().replaceAll("[.]drillMember$", "")).toList();
   }

   /** The keys of the runtime sheet's variable table, as set from a URL, a viewer or a tool. */
   private static List<String> runtimeParameters(ViewsheetSandbox box) {
      VariableScriptable scriptable = box.getScope().getVariableScriptable();

      if(scriptable == null || !(scriptable.unwrap() instanceof VariableTable table)) {
         return List.of();
      }

      List<String> names = new ArrayList<>();

      for(Enumeration<String> keys = table.keys(); keys.hasMoreElements();) {
         names.add(keys.nextElement());
      }

      return names;
   }

   /**
    * The sorted, distinct parameter names, the standard user variables always included.
    *
    * @param declared   variables the viewsheet declares; always parameters
    * @param runtime    keys of the runtime variable table; a key equal to a component's name is
    *                   that component's own value slot, not a parameter
    * @param components the viewsheet's component names
    */
   static List<String> mergeParameterNames(Collection<String> declared, Collection<String> runtime,
                                           Collection<String> components)
   {
      Set<String> names = new LinkedHashSet<>(STANDARD_PARAMETERS);

      for(String name : declared) {
         if(isParameterName(name)) {
            names.add(name);
         }
      }

      for(String name : runtime) {
         if(isParameterName(name) && !components.contains(name)) {
            names.add(name);
         }
      }

      return names.stream().sorted().toList();
   }

   /** Not blank, and not a runtime-internal slot or a built-in (those are not custom parameters). */
   private static boolean isParameterName(String name) {
      return name != null && !name.isBlank() && !name.startsWith("__") &&
         !name.startsWith("!") && !VariableTable.isBuiltinVariable(name);
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

   /** The syntax of a worksheet expression column or condition value on the SQL switch. */
   static final String WORKSHEET_SQL_NOTE =
      "A worksheet formula in SQL mode is a SQL value expression the data source evaluates per " +
      "row, not JavaScript: write a column by its name as the data source knows it and a " +
      "worksheet variable as $(<name>); the data source's own SQL functions and operators are " +
      "what the dialog offers (this context does not list them). Aggregate functions such as " +
      "SUM, COUNT, AVG, MIN and MAX cannot be used: the formula is evaluated per row, and an " +
      "aggregate makes the table fail. parameter.<name> and script functions are not available. A new Expression condition value starts on SQL.";

   static final String WORKSHEET_EXPRESSION_SCRIPT_NOTE =
      "A worksheet expression column in Script mode is JavaScript evaluated per row: read a " +
      "column as field['<column>'] and a worksheet variable as parameter.<name>. No viewsheet " +
      "component is in scope. The same dialog also has an SQL mode; the user can flip it after " +
      "pairing, so call this again before writing if the dialog may have changed.";

   static final String WORKSHEET_CONDITION_VALUE_SCRIPT_NOTE =
      "A worksheet condition's Expression value in Script mode is a JavaScript expression " +
      "evaluated against the table: read a column as field['<column>'] and a worksheet variable " +
      "as parameter.<name>. The same dialog also has an SQL mode (the default for a new " +
      "Expression value); the user can flip it after pairing, so call this again before writing.";

   /** The syntax of a calculated field formula whose SQL/Script switch is on SQL. */
   static final String CALC_FIELD_SQL_NOTE =
      "A calc field formula in SQL mode is a SQL value expression the data source evaluates per " +
      "row, not JavaScript: read a column as field['<column>'] and a parameter as $(<name>), " +
      "never parameter.<name>. No component, thisViewsheet or script function is in scope.";

   /** The syntax of a calculated field formula whose "Calculate Field From" is Aggregate. */
   static final String CALC_FIELD_AGGREGATE_NOTE =
      "A calc field formula in Aggregate mode is JavaScript evaluated on aggregate results, not " +
      "per row: there is no per-row field object. Read an aggregate as field['<Aggregate>(" +
      "<column>)'], e.g. field['Sum(Product:Total)'] / field['Count(Customer:Address)']; only " +
      "such aggregate tags compute, a bare column field['<column>'] does not. Bind each " +
      "aggregate the formula uses to the component that shows the field. Parameters are " +
      "parameter.<name>. No component or thisViewsheet is in scope.";

   /**
    * Whether the pane is on Aggregate: only a calculated field formula has the Detail/Aggregate
    * choice, so a state reported for any other location is ignored.
    */
   static boolean isAggregateMode(EditorContext grant, PaneState state) {
      return grant != null && ScriptTarget.Kind.CALC_FIELD.wireName().equals(grant.kind()) &&
         state != null && state.aggregate();
   }

   /** Syntax of a location that has no Detail/Aggregate choice, or is on Detail. */
   static PaneContext.Syntax syntaxOf(EditorContext grant, boolean sql) {
      return syntaxOf(grant, sql, false);
   }

   /**
    * How to write the expression at this location.
    *
    * @param sql       whether the pane's SQL/Script switch is on SQL; read for a calculated field
    *                  only
    * @param aggregate whether the pane's Detail/Aggregate choice is Aggregate; read for a
    *                  calculated field only (the dialog has no SQL mode under Aggregate)
    */
   static PaneContext.Syntax syntaxOf(EditorContext grant, boolean sql, boolean aggregate) {
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
            "pushed. $ is not defined here: $(Name) or String($(Name)) fails with " +
            "'ReferenceError: $ is not defined'. Use parameter.<name> or a component's own " +
            "property (RadioButton1.selectedObject). In a highlight whose value type is SQL, " +
            "$(var) is SQL text, not JavaScript.");
         case CALC_FIELD -> aggregate ?
            new PaneContext.Syntax("javascript", CALC_FIELD_AGGREGATE_NOTE) : sql ?
            new PaneContext.Syntax("sql", CALC_FIELD_SQL_NOTE) :
            new PaneContext.Syntax("javascript",
               "A calc field formula is JavaScript evaluated per row: read a column as " +
               "field['<column>'] and a parameter as parameter.<name>. No component or " +
               "thisViewsheet is in scope.");
         case VIEWSHEET_ON_INIT, VIEWSHEET_ON_LOAD, ASSEMBLY_MAIN, ASSEMBLY_ON_CLICK ->
            new PaneContext.Syntax("javascript",
               "A script: reach a component by its name and a parameter as parameter.<name>.");
         case VS_HYPERLINK_EXPRESSION -> new PaneContext.Syntax("javascript",
            "The Web Link Expression of one hyperlink is a JavaScript expression. The dialog's " +
            "text keeps its leading '=' (update_script adds it when missing), and " +
            "=\"message:...\" is the form that makes the link a message link. Reach a component " +
            "by its name and a parameter as parameter.<name>; a row, column or field value is " +
            "reached as the dialog's own tree offers it. Parsed but not run when pushed.");
         case WORKSHEET_VARIABLE_DEFAULT -> new PaneContext.Syntax("javascript",
            "The Expression default value of a worksheet variable is JavaScript. This session " +
            "is served by the worksheet editor tools, not by this viewsheet context.");
         case WORKSHEET_SQL_QUERY -> new PaneContext.Syntax("sql",
            "The SQL of a worksheet query table, in the data source's SQL dialect, run verbatim " +
            "with the user's own permissions; $(var) placeholders are not checked by the " +
            "server. This session is served by the worksheet editor tools, not by this " +
            "viewsheet context.");
         // Bug #81557: the Create/Edit Expression dialog and an Expression condition value are
         // either Script or SQL, the dialog's own switch.
         case WORKSHEET_EXPRESSION -> sql ? new PaneContext.Syntax("sql", WORKSHEET_SQL_NOTE) :
            new PaneContext.Syntax("javascript", WORKSHEET_EXPRESSION_SCRIPT_NOTE);
         case WORKSHEET_CONDITION_VALUE -> sql ? new PaneContext.Syntax("sql", WORKSHEET_SQL_NOTE) :
            new PaneContext.Syntax("javascript", WORKSHEET_CONDITION_VALUE_SCRIPT_NOTE);
         case WORKSHEET_CONDITION -> new PaneContext.Syntax("javascript",
            "A worksheet condition is written as an operator followed by its value(s) (for " +
            "example '> 0', 'BETWEEN 1 AND 10', 'ONE_OF a,b,c'), not as a script. Only its " +
            "single value can be an Expression, which is a separate location " +
            "(worksheetConditionValue) with its own Script/SQL switch.");
      };
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private final ScriptContextService contextService;
   private final ScriptApiService apiService;
   private final ViewsheetService viewsheetService;
   private final BindableFieldsService fieldsService;
}
