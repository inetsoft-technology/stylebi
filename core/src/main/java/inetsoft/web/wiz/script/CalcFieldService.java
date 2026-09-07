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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.DataVSAssemblyInfo;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptEnvRepository;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The only place that knows how a viewsheet calculated field is stored.
 *
 * <p>Its own service rather than another branch of {@link ScriptReadService}, because every other
 * script kind is reached through a {@code VSAssemblyInfo} accessor and this one is not: calc fields
 * live in a per-table map on the {@link Viewsheet} itself, keyed by table name, wrapping an
 * {@link ExpressionRef} inside a {@link CalculateRef}.
 *
 * <p><b>This is Tier 2a: read and edit-existing only.</b> Creating or deleting a calc field is
 * deliberately absent -- re-registering it on the viewsheet, invalidating dependent columns and
 * re-execution are the hard parts, and they are 2b's scope. A write to a name that does not exist
 * is refused rather than treated as a create, so a caller cannot get half of 2b by accident.
 */
@Service
public class CalcFieldService {
   /** One calc field, as enumeration reports it. */
   public record Found(String table, String name, boolean sql, boolean baseOnDetail) {}

   /**
    * The tables worth asking about.
    *
    * <p>{@code Viewsheet.getCalcFields} answers per table and its backing map is private with no
    * key accessor, so there is no supported way to ask which tables have calc fields. Every calc
    * field belongs to a table something is bound to, so the bound assemblies are the honest source
    * -- and it keeps this change inside the script package rather than reaching into
    * {@code uql/viewsheet} for an accessor.
    */
   public List<String> tablesWithCalcFields(Viewsheet vs) {
      if(vs == null) {
         return List.of();
      }

      List<String> tables = new ArrayList<>();

      for(Assembly a : vs.getAssemblies()) {
         if(!(a instanceof VSAssembly vsAssembly)) {
            continue;
         }

         if(vsAssembly.getVSAssemblyInfo() instanceof DataVSAssemblyInfo info) {
            String table = info.getTableName();

            if(table != null && !table.isBlank() && !tables.contains(table)) {
               tables.add(table);
            }
         }
      }

      return tables;
   }

   /** Every calc field on every bound table. */
   public List<Found> list(Viewsheet vs) {
      List<Found> found = new ArrayList<>();

      for(String table : tablesWithCalcFields(vs)) {
         // getCalcFields returns NULL, not an empty array, for a table with none.
         CalculateRef[] refs = vs.getCalcFields(table);

         if(refs == null) {
            continue;
         }

         for(CalculateRef ref : refs) {
            found.add(new Found(table, ref.getName(), ref.isSQL(), ref.isBaseOnDetail()));
         }
      }

      return found;
   }

   /** The expression text of one calc field. */
   public String read(Viewsheet vs, String table, String name) throws PairingException {
      ExpressionRef ref = expressionOf(vs, table, name);
      String expression = ref.getExpression();
      return expression == null ? "" : expression;
   }

   /**
    * Replaces the expression text of an EXISTING calc field.
    *
    * <p>Refuses a name that is not already there. Creating one is 2b, and silently creating it here
    * would hand the caller an unregistered field with no dependent-column invalidation -- which
    * looks like success and is not.
    *
    * <p>Validates the new text before storing it -- the same JS-compile/SQL-validity check the
    * native Formula Editor dialog's OK button runs ({@code ModifyCalculateFieldService
    * .checkScriptValid}) -- so a caller gets a loud, actionable rejection instead of an
    * {@code {ok:true}} that silently persists an uncompilable expression, only surfacing later as
    * a broken render.
    */
   public void write(RuntimeViewsheet rvs, String table, String name, String expression)
      throws PairingException
   {
      if(expression == null) {
         throw new PairingException("A calc field's expression text is required.");
      }

      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      CalculateRef cref = calcRefOf(vs, table, name);

      if(!(cref.getDataRef() instanceof ExpressionRef ref)) {
         throw new PairingException(
            "Calculated field '" + name + "' on '" + table + "' holds no expression.");
      }

      validateExpression(rvs, cref, expression);
      ref.setExpression(expression);
   }

   /**
    * Mirrors the INTENT of {@code ModifyCalculateFieldService.checkScriptValid}'s syntax check,
    * not its JS implementation: that method (and the shared {@code ExpressionDialogService.check}
    * the native "Edit Expression" dialog also uses) validates JS via {@code ScriptEnv.compile()},
    * which -- confirmed live -- does NOT actually parse anything under the Graal engine; it only
    * builds a lazy {@code Source} literal, so a syntax error like an unbalanced paren is silently
    * accepted and only surfaces later at render time (reproduced live: the native Formula Editor
    * dialog accepts it too, then `FormulaTableLens` logs a `SyntaxError` at data-refresh time).
    * That is a pre-existing StyleBI-side gap in {@code checkScriptValid}/{@code
    * ExpressionDialogService.check} themselves, out of scope for this lane (operator decision:
    * fix only this path, not the shared dialog method). {@link ScriptEnv#checkFunction} is the
    * primitive that actually parses eagerly (`context.parse("js", cmd)`, throwing on a real
    * syntax error) -- the same one StyleBI's own Composer script-editor "check script" endpoint
    * (`OpenScriptController`) already uses for an equivalent syntax-only check.
    *
    * <p>Also does not reuse {@code checkScriptValid} itself for another reason: that method is
    * wired to a {@code CommandDispatcher}/{@code MessageCommand} pair -- a WebSocket-session
    * concept this session-token-based agent path has none of -- and also handles the cube-measure
    * (MDX) branch, which {@link #calcRefOf} never returns (every ref this Tier 2a path resolves
    * is a plain {@link ExpressionRef}-backed field).
    */
   private void validateExpression(RuntimeViewsheet rvs, CalculateRef cref, String expression)
      throws PairingException
   {
      boolean issql = cref.isSQL();
      String str = ExpressionRef.getSQLExpression(issql, expression);

      if(issql) {
         if(!XUtil.isSQLExpressionValid(str)) {
            throw new PairingException(
               "Invalid SQL expression for calculated field '" + cref.getName() + "'.");
         }

         return;
      }

      ScriptEnv env = scriptEnvOf(rvs);

      if(env == null) {
         // ScriptEnvRepository.getScriptEnv() (scriptEnvOf's fallback) can only fail if the
         // Graal engine class itself is missing from the deployment -- not a case worth
         // silently skipping validation for, but also not one this path can recover from.
         return;
      }

      try {
         env.checkFunction(cref.getName(), str);
      }
      catch(Exception ex) {
         String suggestion = env.getSuggestion(ex, null);
         throw new PairingException(
            "Script error in calculated field '" + cref.getName() + "': " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : ""));
      }
   }

   /**
    * Prefers the live runtime's own {@link ScriptEnv} when one is wired up, but calc fields are
    * only ever reachable through a pane-scoped session ({@code PaneScopeService} requires it for
    * every real call) -- and that session's {@code RuntimeViewsheet} is StyleBI's own preview
    * copy backing the Formula Editor dialog, which is never given a live {@link ViewsheetSandbox}
    * the way the dialog's own OK-button controller's runtime is
    * ({@code ModifyCalculateFieldService} resolves a different, non-preview runtime). Relying on
    * {@code rvs}'s own sandbox alone made this validation a no-op on every real call -- confirmed
    * live, not just suspected: {@code rvs.getViewsheetSandbox()} returns {@code Optional.empty()}
    * for that preview runtime.
    *
    * <p>Falls back to {@link ScriptEnvRepository#getScriptEnv()} -- a standalone engine with no
    * data-sandbox dependency, the same mechanism {@code AssetQuerySandbox}/{@code ViewsheetScope}
    * lazily create their own env from, and what {@code OpenScriptController} already uses for a
    * comparable syntax-only check. Compiling doesn't need a live data context: referencing an
    * undeclared {@code field} is a runtime {@code ReferenceError}, not a compile-time syntax
    * error, so a bare engine still catches the failure mode this check exists for (e.g. an
    * unbalanced paren).
    */
   private ScriptEnv scriptEnvOf(RuntimeViewsheet rvs) {
      if(rvs != null) {
         Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

         if(box.isPresent()) {
            AssetQuerySandbox wbox = box.get().getAssetQuerySandbox();
            ScriptEnv env = wbox == null ? null : wbox.getScriptEnv();

            if(env != null) {
               return env;
            }
         }
      }

      return ScriptEnvRepository.getScriptEnv();
   }

   private ExpressionRef expressionOf(Viewsheet vs, String table, String name)
      throws PairingException
   {
      CalculateRef cref = calcRefOf(vs, table, name);

      if(!(cref.getDataRef() instanceof ExpressionRef expression)) {
         throw new PairingException(
            "Calculated field '" + name + "' on '" + table + "' holds no expression.");
      }

      return expression;
   }

   private CalculateRef calcRefOf(Viewsheet vs, String table, String name)
      throws PairingException
   {
      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      CalculateRef[] refs = vs.getCalcFields(table);

      if(refs == null || refs.length == 0) {
         throw new PairingException(
            "Table '" + table + "' has no calculated fields. Tables on this viewsheet: " +
            String.join(", ", tablesWithCalcFields(vs)) + ".");
      }

      for(CalculateRef ref : refs) {
         if(name.equals(ref.getName())) {
            return ref;
         }
      }

      throw new PairingException(
         "No calculated field '" + name + "' on table '" + table + "'. This tool cannot " +
         "create a new calculated field -- it edits existing ones only. Present on '" +
         table + "': " + Arrays.stream(refs).map(CalculateRef::getName)
            .collect(Collectors.joining(", ")) + ".");
   }
}
