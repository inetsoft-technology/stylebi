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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.controller.OnClickService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.AssetScriptFailureWarnings;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/**
 * An input assembly's value: combo box, check box, radio button, text input and spinner.
 *
 * <p><b>The unit's name overstated this half.</b> Every input assembly's action vocabulary is exactly
 * three items — {@code edit-script}, {@code properties}, {@code show-format-pane} — all already
 * shipped. There is no apply, clear, search or sort in any input's menu. What is missing is not menu
 * actions but the value itself, driven by direct interaction through four one-endpoint controllers
 * that all converge on {@code VSInputService}.
 *
 * <p>So this is one tool over one service method. {@code singleApplySelection} takes the value as a
 * bare {@code Object}, and a check box's several values arrive as that same parameter holding an
 * {@code Object[]} — which is why one method serves both the scalar and the multi-value inputs.
 *
 * <p><b>The endpoint is silent on everything.</b>
 * {@code applySelection} returns {@code 0} when the name is null, when the viewsheet is null, and —
 * the case that matters — when {@code !(vsAssembly instanceof InputVSAssembly)}. So pointing it at a
 * chart or a typo'd name reports success having done nothing. It is at least *consistently* silent,
 * unlike the selection verbs, which variously no-op, NPE and CCE. Either way there is nothing worth
 * forwarding, so this class resolves and type-checks first.
 *
 * <p><b>A value written here persists.</b> {@code CheckBoxVSAssembly}, {@code ComboBoxVSAssembly},
 * {@code RadioButtonVSAssembly} and {@code TextInputVSAssembly} all implement
 * {@code writeStateContent} the same way the selection assemblies do, and it is called on the save
 * path — so an input's value becomes what every future viewer opens with.
 *
 * <p><b>The assemblies' own setters adjust a value they cannot hold, without saying so.</b> A slider
 * or spinner ignores a non-number and clamps to its min/max
 * ({@code NumericRangeVSAssemblyInfo.setSelectedObject}); a check box drops values not in its list,
 * a radio button falls back to its first value and a non-editable combo box auto-selects its first
 * entry ({@code validate()} on each). The Composer UI only offers values those setters accept, so
 * this class refuses the rest up front ({@link #checkValue}) and reports back what was actually
 * stored, in {@code values}.
 */
@Service
public class InputValueService {
   /** Fixed-length so the mask reveals nothing about the secret's length. */
   static final String SECRET_MASK = TextInputVSAssemblyInfo.PASSWORD_MASK;

   @Autowired
   public InputValueService(ViewsheetSessionService sessions, VSInputService inputs,
                            CoreLifecycleService lifecycle, OnClickService onClickService)
   {
      this.sessions = sessions;
      this.inputs = inputs;
      this.lifecycle = lifecycle;
      this.onClickService = onClickService;
   }

   /** For callers that never submit; {@code submit:true} then fails loud instead of firing. */
   public InputValueService(ViewsheetSessionService sessions, VSInputService inputs,
                            CoreLifecycleService lifecycle)
   {
      this(sessions, inputs, lifecycle, null);
   }

   /**
    * Sets an input assembly's value.
    *
    * @param values one value for a combo box, radio button, text input or spinner; one or more for a
    *               check box. Empty means "no value selected", which is a legitimate state and not
    *               the same as leaving it alone.
    */
   public Map<String, Object> setValue(String sessionToken, Principal user, String assemblyName,
                                       List<Object> values, String linkUri)
      throws Exception
   {
      return setValue(sessionToken, user, assemblyName, values, linkUri, false);
   }

   /**
    * Sets an input assembly's value and, when {@code submit} is true, fires a text input's onClick
    * script the way the Viewer's Enter key does.
    *
    * <p><b>submit.</b> Only a text input (any Input Editor: Text, Password, Integer, Float, Date)
    * has the Enter-fires-onClick path; every other input type is refused, before anything is
    * written. The value is stored first, as the browser does, then the script runs as the
    * separate onClick event, then the value is read back (the script may have changed it and the
    * refresh it triggers may re-copy variables onto inputs). A script that throws does not undo
    * the stored value, and side effects it made before throwing are not undone either: one undo
    * checkpoint covers all of it. A {@code confirm()} in the script is captured and reported as
    * {@code confirmPending}, never answered.
    *
    * @param submit true to fire the onClick script after storing the value
    */
   public Map<String, Object> setValue(String sessionToken, Principal user, String assemblyName,
                                       List<Object> values, String linkUri, boolean submit)
      throws Exception
   {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required — name the input assembly.");
      }

      if(values == null) {
         throw new IllegalArgumentException(
            "'value' is required. Pass an empty list to clear the input rather than omitting it, so " +
            "that clearing is something you asked for rather than a default.");
      }

      final Map<String, Object> result = new LinkedHashMap<>();
      final List<String> scriptWarnings = new ArrayList<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         InputVSAssembly assembly = requireInput(rvs, assemblyName, false);
         boolean multi = assembly instanceof CheckBoxVSAssembly;

         if(submit) {
            // refused before any write: a value stored and then a refusal would leave the input
            // changed after the caller was told 'no'
            if(!(assembly instanceof TextInputVSAssembly)) {
               throw new IllegalArgumentException(
                  "'submit' applies only to a text input, which is the one input the Viewer fires " +
                  "an onClick script for on Enter; '" + assemblyName + "' is " +
                  describe(assembly) + ". Nothing was changed. Omit 'submit' to just set the value.");
            }

            if(onClickService == null) {
               throw new IllegalStateException("'submit' is not available: no onClick service.");
            }

            if(rvs.getViewsheetSandbox().isEmpty()) {
               throw new PairingException("Viewsheet sandbox not available for this runtime");
            }
         }

         if(!multi && values.size() > 1) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + describe(assembly) + ", which holds one value, but " +
               values.size() + " were given. Only a check box accepts several.");
         }

         VSAssemblyInfo info0 = assembly.getVSAssemblyInfo();
         // setSelectedObject does not clamp to a dynamic max, so neither does the check
         boolean dynamicMax = info0 instanceof NumericRangeVSAssemblyInfo range &&
            VSUtil.isDynamicValue(range.getMaxValue());
         checkValue(info0, assembly.getDataType(), values, dynamicMax, assemblyName,
                    describe(assembly));
         checkWriteBack(rvs, assemblyName, info0);

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));
         result.put("valueCount", values.size());

         // A check box's several values travel as an Object[] in the same 'selectedObject'
         // parameter the scalar inputs use, which is why one service method serves both.
         Object selected = multi
            ? values.toArray()
            : (values.isEmpty() ? null : values.get(0));

         inputs.singleApplySelection(runtimeId, assemblyName, selected, user, dispatcher, linkUri);
         commitIfNotSubmittedOnChange(rvs, assemblyName, info0, dispatcher, linkUri);

         List<Object> beforeScript = null;
         boolean secret = isSecret(info0);

         if(submit) {
            VSAssembly stored = rvs.getViewsheet().getAssembly(assemblyName);
            beforeScript = stored != null &&
               stored.getVSAssemblyInfo() instanceof InputVSAssemblyInfo held
               ? heldValues(held) : List.of();
            fireOnClick(rvs, runtimeId, assemblyName, user, dispatcher, linkUri, result,
                        scriptWarnings);
         }

         // Read back what the assembly actually holds -- a slider can still snap to its increment.
         VSAssembly applied = rvs.getViewsheet().getAssembly(assemblyName);

         if(applied != null && applied.getVSAssemblyInfo() instanceof InputVSAssemblyInfo info) {
            List<Object> held = heldValues(info);

            if(isSecret(info)) {
               // the write persisted; the secret itself must not travel back into the agent's
               // transcript, so report how many values are held and whether they were adjusted
               result.put("values", held.stream().map(v -> (Object) SECRET_MASK).toList());
               result.put("masked", true);
               result.put("adjusted", !sameValues(held, values));
            }
            else {
               result.put("values", held);
            }

            if(submit && beforeScript != null && !sameValues(held, beforeScript)) {
               // the script (or the refresh it triggered) changed what was just stored
               result.put("valuesBeforeScript",
                          secret ? beforeScript.stream().map(v -> (Object) SECRET_MASK).toList()
                                 : beforeScript);
               result.put("adjustedByScript", true);
            }
         }
      });

      if(!scriptWarnings.isEmpty()) {
         List<String> all = new ArrayList<>(warnings == null ? List.of() : warnings);
         scriptWarnings.stream().filter(w -> !all.contains(w)).forEach(all::add);
         warnings = all;
      }

      ViewsheetSessionService.relayWarnings(result, warnings);

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Fires the text input's onClick script the way {@code OnClickService.process0} would, after
    * checking the same gates it silently returns on, so {@code scriptFired} is computed here and
    * never inferred from {@code onClick} returning normally.
    */
   private void fireOnClick(RuntimeViewsheet rvs, String runtimeId, String assemblyName,
                            Principal user, CapturingCommandDispatcher dispatcher, String linkUri,
                            Map<String, Object> result, List<String> scriptWarnings)
      throws Exception
   {
      result.put("valueStored", true);
      VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
      String skipped = null;

      if(assembly == null ||
         !(assembly.getVSAssemblyInfo() instanceof ClickableInputVSAssemblyInfo clickable))
      {
         skipped = "the assembly is not clickable";
      }
      else if(!clickable.isScriptEnabled()) {
         skipped = "the assembly's script is disabled";
      }
      else if(clickable.getOnClick() == null || clickable.getOnClick().isEmpty()) {
         skipped = "the text input has no onClick script";
      }

      if(skipped != null) {
         result.put("scriptFired", false);
         result.put("scriptSkippedReason", skipped);
         return;
      }

      result.put("scriptFired", true);

      try {
         // a fresh ASSET_EXCEPTIONS list around the call surfaces other assemblies' scripts that
         // fail in the refresh onClick triggers
         scriptWarnings.addAll(AssetScriptFailureWarnings.collect(() -> {
            onClickService.onClick(runtimeId, assemblyName, "0", "0", null, linkUri, user,
                                   dispatcher);
            return List.of();
         }));
      }
      catch(Exception ex) {
         ScriptException script = scriptCause(ex);

         if(script == null) {
            throw ex;
         }

         // the value was stored before the script ran and stays stored, as in the browser
         String message = script.getMessage() == null ? script.toString() : script.getMessage();
         result.put("scriptFailed", true);
         result.put("scriptError", message);
         scriptWarnings.add("The onClick script of '" + assemblyName + "' failed: " + message +
                            "\nThe value was stored. Anything the script did before it threw " +
                            "was not undone.");
      }

      List<String> confirms = dispatcher.getConfirmMessages();

      if(!confirms.isEmpty()) {
         result.put("confirmPending", confirms.size() == 1 ? confirms.get(0) : confirms);
         scriptWarnings.add("The onClick script of '" + assemblyName + "' asked for confirmation " +
                            "(" + confirms.get(0) + "). It was not answered, so the script's " +
                            "confirmed branch did not run.");
      }
   }

   private static ScriptException scriptCause(Throwable e) {
      for(int depth = 0; e != null && depth < 10; depth++, e = e.getCause()) {
         if(e instanceof ScriptException script) {
            return script;
         }
      }

      return null;
   }

   /**
    * Reads the value(s) the runtime currently holds for the named input assemblies, without
    * writing, running a script or taking an undo checkpoint (the read-only counterpart of
    * {@link #setValue}). Goes through {@link ViewsheetSessionService#read}, the checkpoint-free
    * path.
    *
    * <p>The value is the one held by the paired runtime ({@code InputVSAssemblyInfo
    * .getSelectedObjects()}): a value the user or agent has set, or the design default before
    * either has. A single-value input reports a list of one; a check box reports every selected
    * value and a cleared input an empty list. A text input whose Input Editor is a password reports
    * {@link #SECRET_MASK} per held value and {@code masked: true}, exactly as {@code setValue}
    * does, so the secret never reaches the caller.
    *
    * @param assemblyNames the inputs to read; null or empty reads every input assembly in the
    *                      viewsheet.
    */
   public Map<String, Object> getValues(String sessionToken, Principal user,
                                        List<String> assemblyNames)
      throws Exception
   {
      List<String> names = assemblyNames == null ? List.of() :
         assemblyNames.stream().filter(n -> n != null && !n.isBlank()).distinct().toList();

      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         List<InputVSAssembly> inputsToRead = new ArrayList<>();

         if(names.isEmpty()) {
            Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

            if(vs != null) {
               for(inetsoft.uql.asset.Assembly assembly : vs.getAssemblies()) {
                  if(assembly instanceof InputVSAssembly input) {
                     inputsToRead.add(input);
                  }
               }
            }
         }
         else {
            for(String name : names) {
               inputsToRead.add(requireInput(rvs, name, true));
            }
         }

         List<Map<String, Object>> entries = new ArrayList<>();

         for(InputVSAssembly assembly : inputsToRead) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("assembly", assembly.getAbsoluteName());
            entry.put("type", describe(assembly));

            if(assembly.getVSAssemblyInfo() instanceof InputVSAssemblyInfo info) {
               List<Object> held = heldValues(info);

               if(isSecret(info)) {
                  entry.put("values", held.stream().map(v -> (Object) SECRET_MASK).toList());
                  entry.put("masked", true);
               }
               else {
                  entry.put("values", held);
               }
            }
            else {
               entry.put("values", List.of());
            }

            entries.add(entry);
         }

         Map<String, Object> result = new LinkedHashMap<>();
         result.put("inputs", entries);
         return result;
      });
   }

   /** The non-null values the assembly holds, in stored order. */
   private static List<Object> heldValues(InputVSAssemblyInfo info) {
      Object[] stored = info.getSelectedObjects();
      return stored == null ? List.of() :
         Arrays.stream(stored).filter(Objects::nonNull).toList();
   }

   /**
    * Commits the value just applied into the sandbox variable table when the input does not
    * submit on change. {@code singleApplySelection} only does that for a submit-on-change input,
    * so otherwise the table keeps the previous value and the next viewsheet refresh copies it back
    * onto the input ({@code ViewsheetSandbox.applyParameterToInput}). This is what the browser's
    * Submit button does ({@code VSInputService.commitInputTableWrites}), resolved on the sandbox
    * itself for the same proxy reason as {@link #checkWriteBack}.
    */
   private void commitIfNotSubmittedOnChange(RuntimeViewsheet rvs, String assemblyName,
                                             VSAssemblyInfo info, CommandDispatcher dispatcher,
                                             String linkUri)
      throws Exception
   {
      if(!(info instanceof InputVSAssemblyInfo input) || input.isSubmitOnChange()) {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         return;
      }

      ChangedAssemblyList clist = lifecycle.createList(true, dispatcher, rvs, linkUri);
      box.lockWrite();

      try {
         box.processChange(assemblyName, VSAssembly.OUTPUT_DATA_CHANGED, clist);
      }
      finally {
         box.unlockWrite();
      }

      // a later processChange does not carry this one's changes, so the dependents it found are
      // executed here (Bug #78878)
      lifecycle.execute(rvs, assemblyName, linkUri, clist, dispatcher, true);
   }

   /**
    * True for an input whose value must not be echoed: a text input whose Input Editor is a
    * password. The option is read from the assembly info itself, the same field the viewer uses to
    * render {@code type=password}.
    */
   static boolean isSecret(VSAssemblyInfo info) {
      return info instanceof TextInputVSAssemblyInfo text && text.isPassword();
   }

   private static boolean sameValues(List<Object> held, List<Object> requested) {
      if(held.size() != requested.size()) {
         return false;
      }

      for(int i = 0; i < held.size(); i++) {
         if(!Objects.equals(String.valueOf(held.get(i)), String.valueOf(requested.get(i)))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Refuses, before anything is stored, a value the write-back would refuse.
    *
    * <p>{@code applySelection0} stores the value first and writes it back after, so a target the
    * write refuses would leave the input holding a value the caller was told was refused (bug
    * #78579). This asks the same predicate the write uses, against the same sandbox, table,
    * design-time column and cached row, so it refuses exactly what the write would and nothing
    * else. A binding with no table or column stays the silent no-op it is in the UI.
    */
   private void checkWriteBack(RuntimeViewsheet rvs, String assemblyName, VSAssemblyInfo info0) {
      if(!(info0 instanceof InputVSAssemblyInfo info) || !info.getWriteBackValue()) {
         return;
      }

      // resolved on the sandbox itself, as applySelection0's write does -- never through a new
      // VSInputService method: the bean is proxied, and a method the (AOT pre-generated) proxy
      // class does not override runs on the proxy instance with null fields
      ViewsheetSandbox root = rvs.getViewsheetSandbox().orElse(null);
      ViewsheetSandbox box = root == null ? null : root.getSandboxForAssembly(assemblyName);

      if(box == null) {
         return;
      }

      String table = VSUtil.stripOuter(info.getTableName());
      String column = info.getColumnValue();
      int row = info.getRow();
      ViewsheetSandbox.WriteBackRefusal refusal = box.getWriteBackRefusal(table, column, row);

      if(refusal == null) {
         return;
      }

      String range = "";

      if(refusal.firstDataRow() >= 0) {
         range = refusal.lastDataRow() >= refusal.firstDataRow()
            ? " Its data rows are " + refusal.firstDataRow() + ".." + refusal.lastDataRow() + "."
            : " It has no data rows.";
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' writes its value back to worksheet table '" + table +
         "', column '" + column + "', rowValue '" + info.getRowValue() + "' (" +
         (row == -1 ? "-1, not evaluated" : "resolved row " + row) + "), and that target " +
         "cannot be written: " + refusal.message() + range + " Nothing was changed. Fix the " +
         "input's table/columnValue/rowValue binding, or turn writeBackDirectly off.");
   }

   /**
    * A free-entry text input holds what its Input Editor's type can represent. The shared
    * {@code option.validate} saturates an out-of-range integer and rolls an impossible date over
    * (both then count as valid), and the browser path stores the raw text either way, so the raw
    * value is parsed strictly here and only then handed to the editor for its min/max.
    */
   private static void checkTextInput(TextInputVSAssemblyInfo info, List<Object> values,
                                      String assemblyName, String what)
   {
      if(values.isEmpty() || values.get(0) == null) {
         return;
      }

      Object value = values.get(0);

      if(!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + ", which holds a string, number or boolean " +
            "value; got " + (value instanceof Map ? "an object" :
                             value instanceof Collection ? "an array" :
                             value.getClass().getSimpleName()) + ".");
      }

      ColumnOption option = info.getColumnOption();
      String type = option == null ? null : option.getType();
      String parseType = ColumnOption.INTEGER.equals(type) ? "integer" :
         ColumnOption.FLOAT.equals(type) ? "double" :
         ColumnOption.DATE.equals(type) ? "date" : null;
      String text = String.valueOf(value);

      if(parseType == null || text.isBlank()) {
         return;
      }

      Object parsed;

      try {
         parsed = WizStrictValueParser.parse(text, parseType);
      }
      catch(IllegalArgumentException ex) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + " with a " + type + " Input Editor; " +
            quote(value) + " is invalid: " + ex.getMessage(), ex);
      }

      if(!option.validate(parsed)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + " with a " + type + " Input Editor, and " +
            quote(value) + " is outside the range it allows.");
      }
   }

   /**
    * An editable or calendar combo accepts values outside its list, but the setter still converts
    * each one with {@code Tool.getData} against the dataType, which saturates an out-of-range
    * integer, clears unparseable text and rolls an impossible date over (bug #78590). A blank
    * value stays legal: it clears the combo.
    */
   private static void checkEditableComboValues(List<Object> values, String dataType,
                                                String assemblyName, String what)
   {
      for(Object value : values) {
         if(value == null || String.valueOf(value).isBlank()) {
            continue;
         }

         try {
            WizStrictValueParser.parse(String.valueOf(value), dataType);
         }
         catch(IllegalArgumentException ex) {
            Throwable cause = ex.getCause() != null && ex.getCause().getMessage() != null ?
               ex.getCause() : ex;

            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + " with dataType " + dataType + "; " +
               quote(value) + " is invalid: " + cause.getMessage() + formatHint(dataType) +
               ". It would be stored coerced or cleared rather than as given.", ex);
         }
      }
   }

   /**
    * The calendar picker offers no day outside the combo's minDate/maxDate, so a date written
    * outside them is a state a user cannot produce (bug #79697). The picker works in whole days,
    * so the bounds are compared by day. A bound that is unset or does not resolve to a date (a
    * variable or script that has not run) is not enforced. Run after
    * {@link #checkEditableComboValues}, so every non-blank value already parses.
    */
   private static void checkCalendarBounds(ComboBoxVSAssemblyInfo combo, List<Object> values,
                                           String dataType, String assemblyName, String what)
   {
      if(!Tool.DATE.equalsIgnoreCase(dataType) && !Tool.TIME_INSTANT.equalsIgnoreCase(dataType)) {
         return;
      }

      LocalDate min = toDay(combo.getMinDate());
      LocalDate max = toDay(combo.getMaxDate());

      if(min == null && max == null) {
         return;
      }

      for(Object value : values) {
         if(value == null || String.valueOf(value).isBlank()) {
            continue;
         }

         LocalDate day = toDay(WizStrictValueParser.parse(String.valueOf(value), dataType));

         if(day == null) {
            continue;
         }

         if(min != null && day.isBefore(min) || max != null && day.isAfter(max)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + " with a calendar limited to " +
               (min == null ? "any earlier date" : min) + " through " +
               (max == null ? "any later date" : max) + ", and " + quote(value) +
               " is outside it. The calendar offers no such date, so the value is refused and " +
               "the previous one kept.");
         }
      }
   }

   private static LocalDate toDay(Object date) {
      return date instanceof java.util.Date d ?
         new java.sql.Timestamp(d.getTime()).toLocalDateTime().toLocalDate() : null;
   }

   private static String formatHint(String dataType) {
      if(Tool.DATE.equalsIgnoreCase(dataType) || Tool.TIME_INSTANT.equalsIgnoreCase(dataType)) {
         return " (accepted format: yyyy-MM-dd or yyyy-MM-dd HH:mm:ss)";
      }

      return Tool.TIME.equalsIgnoreCase(dataType) ? " (accepted format: HH:mm:ss)" : "";
   }

   /**
    * Refuses a value the assembly's own setter would silently ignore, clamp, drop or replace. Only
    * checks what the assembly's info makes unambiguous: a list input whose list has not been
    * computed yet (no values) is passed through, as is an editable or calendar combo box, which
    * accept values outside their list.
    */
   static void checkValue(VSAssemblyInfo info, String dataType, List<Object> values,
                          boolean dynamicMax, String assemblyName, String what)
   {
      if(info instanceof NumericRangeVSAssemblyInfo range) {
         if(values.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + ", which always holds a number and cannot " +
               "be cleared. Pass the value to set instead.");
         }

         Object value = values.get(0);
         Object number = value == null ? null : Tool.getData(dataType, value);

         if(!(number instanceof Number num)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + ", which only holds numbers — got " +
               quote(value) + ".");
         }

         double min = range.getMin();
         double max = range.getMax();

         if(num.doubleValue() < min || !dynamicMax && num.doubleValue() > max) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + " with a range of " + format(min) + " to " +
               format(max) + ", and " + format(num.doubleValue()) + " is outside it. It would " +
               "be clamped to the nearest end rather than stored as given.");
         }

         return;
      }

      if(info instanceof TextInputVSAssemblyInfo text) {
         checkTextInput(text, values, assemblyName, what);
         return;
      }

      if(!(info instanceof ListInputVSAssemblyInfo list)) {
         return;
      }

      if(list instanceof ComboBoxVSAssemblyInfo combo &&
         (combo.isTextEditable() || combo.isCalendar()))
      {
         checkEditableComboValues(values, dataType, assemblyName, what);

         if(combo.isCalendar()) {
            checkCalendarBounds(combo, values, dataType, assemblyName, what);
         }

         return;
      }

      Object[] choices = list.getValues();

      if(choices == null || choices.length == 0) {
         return;
      }

      boolean checkBox = list instanceof CheckBoxVSAssemblyInfo;

      if(values.isEmpty() && !checkBox) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + ", which cannot be left empty — clearing it " +
            "would select one of its listed values instead. Pass the value to select.");
      }

      List<String> missing = new ArrayList<>();

      for(Object value : values) {
         Object converted = value == null ? null : Tool.getData(dataType, value);

         if(Arrays.stream(choices).noneMatch(c -> sameValue(converted, c))) {
            missing.add(quote(value));
         }
      }

      if(!missing.isEmpty()) {
         List<String> listed = new ArrayList<>();

         for(int i = 0; i < choices.length && i < MAX_LISTED; i++) {
            listed.add(quote(choices[i]));
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + " and " + String.join(", ", missing) +
            (missing.size() == 1 ? " is" : " are") + " not in its list, so " +
            (checkBox ? (missing.size() == 1 ? "it" : "they") + " would be dropped"
               : "it would be replaced by one of its listed values") +
            ". Its values are: " + String.join(", ", listed) +
            (choices.length > MAX_LISTED ? ", … (" + choices.length + " in all)" : "") + ".");
      }
   }

   /** The same comparison {@code ListInputVSAssemblyInfo.isSelectedObjectEqual} makes. */
   private static boolean sameValue(Object value, Object choice) {
      if(Tool.equals(value, choice)) {
         return true;
      }

      return value != null && choice != null &&
         Tool.equals(Tool.toString(value), Tool.toString(choice));
   }

   private static String quote(Object value) {
      return value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
   }

   private static String format(double value) {
      return value == Math.rint(value) && !Double.isInfinite(value)
         ? String.valueOf((long) value) : String.valueOf(value);
   }

   private static InputVSAssembly requireInput(RuntimeViewsheet rvs, String assemblyName,
                                               boolean read)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'." + (read
               ? " There is no input assembly with that name to read."
               : " The input endpoint returns success and changes nothing for a name it cannot " +
                  "find, so this is refused here instead."));
      }

      if(!(assembly instanceof InputVSAssembly input)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not an input assembly. Inputs are combo boxes, check boxes, radio buttons, text " +
            "inputs and spinners (a submit button is an output assembly and holds no value).");
      }

      return input;
   }

   private static String describe(InputVSAssembly assembly) {
      if(assembly instanceof CheckBoxVSAssembly) {
         return "a check box";
      }
      else if(assembly instanceof ComboBoxVSAssembly) {
         return "a combo box";
      }
      else if(assembly instanceof RadioButtonVSAssembly) {
         return "a radio button";
      }
      else if(assembly instanceof TextInputVSAssembly) {
         return "a text input";
      }
      else if(assembly instanceof SpinnerVSAssembly) {
         return "a spinner";
      }
      else if(assembly instanceof SliderVSAssembly) {
         return "a slider";
      }

      return "a " + assembly.getClass().getSimpleName();
   }

   private static final int MAX_LISTED = 20;

   private final ViewsheetSessionService sessions;
   private final VSInputService inputs;
   private final CoreLifecycleService lifecycle;
   private final OnClickService onClickService;
}
