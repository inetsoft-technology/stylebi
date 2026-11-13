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

import inetsoft.web.wiz.pairing.DynamicValueName;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import inetsoft.web.wiz.viewsheet.PropertyAliases;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The synthetic, draft-only target of a {@code dynamicValue} pane session
 * ({@link ScriptTarget.Kind#DYNAMIC_VALUE}), the counterpart of {@link VsConditionValueTargets}.
 *
 * <p>The value lives only in the user's open formula dialog, so there is nothing on the runtime
 * viewsheet to enumerate, read or write: the target is derived from the session's own grant, the
 * read is an empty pending draft, and the expression is only parsed, never executed. What differs
 * from a condition value is the name grammar, the wording, and the write rules: an Expression-mode
 * dynamic value is always JavaScript, so a parse error is always refused. Static and pure so it
 * needs no Spring bean (the deployed instance runs AOT: a new bean or constructor change needs the
 * {@code server} module rebuilt).
 */
final class DynamicValueTargets {
   private DynamicValueTargets() {
   }

   /** A write text after normalization; {@code strippedEquals} says a leading '=' was removed. */
   record Prepared(String text, boolean strippedEquals) {
   }

   /**
    * The one target a pane-scoped {@code dynamicValue} session lists, or null if the grant is not
    * a well-formed dynamic value.
    *
    * @param assemblyTypeKey the {@link PropertyAliases#typeKeyOf} key of the assembly, or null when
    *                        it is not known (the label then omits the type and shows no alias)
    */
   static ScriptTargetInfo targetInfo(EditorContext grant, String assemblyTypeKey) {
      ScriptTarget target;

      try {
         target = ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, grant.assembly(), grant.name());
      }
      catch(PairingException ex) {
         return null;
      }

      String owner = "'" + target.assemblyName() + "'";
      String label = "Expression of " + describe(target, assemblyTypeKey,
         "; draft-only: the value is in the user's open dialog");

      return new ScriptTargetInfo(
         target.id(), target.kind().wireName(), target.assemblyName(), target.name(), null, null,
         label, "each time " + owner + " is refreshed, once the user applies the property",
         false, true, "dynamicValue:" + target.assemblyName() + ":" + target.name(),
         "viewsheet", null, null, Boolean.TRUE);
   }

   /** The empty, pending read: the dialog's text is not on the server. */
   static ScriptInfo read(ScriptTarget target) {
      return new ScriptInfo(target.toString(), "", true, Boolean.TRUE, note(target, false));
   }

   static String note(ScriptTarget target, boolean write) {
      String description = describe(target, null, null);
      String readTool = readTool(DynamicValueName.parse(target.name()));

      if(write) {
         return "Pushed to the open formula dialog for " + description + " as a draft. Nothing " +
            "is applied: the user must click OK in the formula dialog and then OK/Apply in the " +
            "dialog that holds the field; Cancel anywhere discards it. The server cannot see " +
            "whether the dialog showed it: if the user had typed, the draft waits behind the " +
            "dialog's reload prompt. Do not retry or read back to verify; after the user " +
            "applies it, verify with " + readTool + " from a toolbar session.";
      }

      return "Draft-only location: the expression of ONE dynamic value in the user's open " +
         "dialog (" + description + "). The dialog's current text lives only in the browser, " +
         "so the server cannot return it; the empty text is NOT an empty expression and NOT a " +
         "failed update_script. Nothing is applied until the user clicks OK in the formula " +
         "dialog and then OK/Apply in the dialog that holds the field. To read the value that " +
         "is currently applied, use " + readTool + " from a toolbar session (connect_sheet " +
         "with a toolbar code).";
   }

   /**
    * Normalizes and checks the text an agent wants pushed. The dialog's editor holds the
    * expression WITHOUT its leading '=' (the combo re-adds it), so ONE leading '=' is removed and
    * reported; {@code ==x} is left alone. Refuses, loudly and naming why, what would otherwise be
    * stored as a plausible but wrong value: a blank (a bare '='), a bare {@code $(Name)}
    * reference (it parses as a JavaScript call to {@code $} and silently does nothing useful), and
    * anything that does not parse.
    *
    * @param syntaxProblem the parse error of a text, or null when it parses; never executes
    */
   static Prepared prepareWrite(String text, UnaryOperator<String> syntaxProblem)
      throws PairingException
   {
      if(text == null) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT, "text is required.");
      }

      String normalized = text;
      boolean stripped = false;
      String lead = text.stripLeading();

      if(lead.startsWith("=") && !lead.startsWith("==")) {
         normalized = lead.substring(1).stripLeading();
         stripped = true;
      }

      if(normalized.isBlank()) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "The expression is empty: it would store a bare '=' and leave the field with no " +
            "value. Nothing was pushed.");
      }

      if(VARIABLE_REFERENCE.matcher(normalized.strip()).matches()) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "That text is a variable reference, not an expression: as an Expression it would " +
            "call a function named $. Ask the user to choose the Variable type for this field, " +
            "or push an expression that computes the value. Nothing was pushed.");
      }

      String problem = syntaxProblem.apply(normalized);

      if(problem != null) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "The expression does not parse as a JavaScript expression (" + problem +
            "). Nothing was pushed.");
      }

      return new Prepared(normalized, stripped);
   }

   /** Exactly one non-blank, parenthesis-free name in {@code $( )}, as the plugin reads a reference. */
   private static final Pattern VARIABLE_REFERENCE =
      Pattern.compile("\\$\\([^()]*[^()\\s][^()]*\\)");

   // ---- description ------------------------------------------------------------------------

   /**
    * {@code the <caption or key> property of <Type> '<assembly>' (<where>[extra])}. The type, and
    * with it the alias of a raw path, is omitted when {@code assemblyTypeKey} is null.
    */
   private static String describe(ScriptTarget target, String assemblyTypeKey, String extra) {
      DynamicValueName name = DynamicValueName.parse(target.name());
      String key = shownKey(name, assemblyTypeKey);
      String subject = DynamicValueName.PROP.equals(name.namespace())
         ? "the " + captionOrKey(key) + " property" : "the '" + name.wire() + "' value";
      String type = assemblyTypeKey == null ? "" : typeWord(assemblyTypeKey) + " ";

      return subject + " of " + type + "'" + target.assemblyName() + "' (" +
         where(name, key) + (extra == null ? "" : extra) + ")";
   }

   /** The name's key, with a raw path replaced by the alias the assembly's own type has for it. */
   private static String shownKey(DynamicValueName name, String assemblyTypeKey) {
      if(!DynamicValueName.PROP.equals(name.namespace()) || assemblyTypeKey == null ||
         name.key().indexOf('.') < 0)
      {
         return name.key();
      }

      String alias = PropertyAliases.aliasFor(assemblyTypeKey, name.key());

      return alias == null ? name.key() : alias;
   }

   private static String captionOrKey(String key) {
      String caption = PropertyAliases.labelFor(key);

      return caption == null ? key : caption;
   }

   private static String where(DynamicValueName name, String key) {
      return switch(name.namespace()) {
         case DynamicValueName.SOURCE -> "set_selection_source parameter '" + key + "'";
         case DynamicValueName.TABLE_OPTION -> "set_table_options key '" + key + "'";
         case DynamicValueName.DATE_COMPARISON -> "set_date_comparison parameter '" + key + "'";
         case DynamicValueName.BINDING -> bindingWhere(key);
         case DynamicValueName.CHART_TARGET -> chartTargetWhere(key);
         case DynamicValueName.CHART_REGION -> chartRegionWhere(key);
         default -> "set_assembly_properties key '" + key + "'";
      };
   }

   private static final Pattern BINDING_KEY = Pattern.compile("([^\\[]+)\\[([0-9]+)\\]\\.(.+)");

   private static String bindingWhere(String key) {
      Matcher m = BINDING_KEY.matcher(key);

      return m.matches()
         ? "the binding editor field, fieldConfigs key '" + m.group(3) + "' of shelf '" +
            m.group(1) + "' position " + m.group(2)
         : "the binding editor field '" + key + "'";
   }

   private static String chartTargetWhere(String key) {
      int dot = key.indexOf('.');
      String kind = key.substring(0, dot);
      String tool = kind.equals("line") ? "add_chart_target_line" : "add_chart_target";

      return kind + " target, " + tool + " parameter '" + key.substring(dot + 1) +
         "' (no edit tool for an existing target)";
   }

   private static String chartRegionWhere(String key) {
      String[] parts = key.split("/");

      return "set_chart_region_properties, region " + parts[0] + ", target " + parts[1] +
         ", properties.title";
   }

   /** The plugin tool that reads back what the same namespace's draft will have applied. */
   private static String readTool(DynamicValueName name) {
      return switch(name.namespace()) {
         case DynamicValueName.BINDING -> "get_binding";
         case DynamicValueName.TABLE_OPTION -> "list_table_options";
         case DynamicValueName.DATE_COMPARISON -> "get_date_comparison";
         case DynamicValueName.CHART_TARGET -> "list_chart_target_lines";
         case DynamicValueName.CHART_REGION -> "list_chart_region_properties";
         default -> "get_assembly_properties";
      };
   }

   /** The Composer's own two-word names, where the registry key runs the words together. */
   private static final Map<String, String> TYPE_WORDS = Map.of(
      "calctable", "Calc Table", "checkbox", "Check Box", "combobox", "Combo Box",
      "groupcontainer", "Group Container", "radiobutton", "Radio Button",
      "selectioncontainer", "Selection Container", "selectionlist", "Selection List",
      "selectiontree", "Selection Tree", "textinput", "Text Input", "timeslider", "Time Slider");

   private static String typeWord(String typeKey) {
      String word = TYPE_WORDS.get(typeKey);

      return word != null ? word : Character.toUpperCase(typeKey.charAt(0)) + typeKey.substring(1);
   }
}
