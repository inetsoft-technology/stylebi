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

import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.HyperlinkRegionName;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;

import java.util.Map;

/**
 * The synthetic, draft-only target of a {@code vsHyperlinkExpression} pane session
 * ({@link ScriptTarget.Kind#VS_HYPERLINK_EXPRESSION}), the counterpart of
 * {@link DynamicValueTargets} for the Hyperlink dialog's Web Link Expression.
 *
 * <p>The expression lives only in the user's open Hyperlink dialog, so there is nothing on the
 * runtime viewsheet to enumerate, read or write: the target is derived from the session's own
 * grant, the read is an empty pending draft, and the applied hyperlink is read back with the
 * plugin's {@code get_hyperlink}. Static and pure so it needs no Spring bean (the deployed
 * instance runs AOT).
 */
final class VsHyperlinkTargets {
   private VsHyperlinkTargets() {
   }

   /**
    * The one target a pane-scoped {@code vsHyperlinkExpression} session lists, or null if the
    * grant is not a well-formed hyperlink region.
    *
    * @param assemblyTypeKey the {@code PropertyAliases.typeKeyOf} key of the assembly, or null when
    *                        it is not known (the label then omits the type)
    */
   static ScriptTargetInfo targetInfo(EditorContext grant, String assemblyTypeKey) {
      ScriptTarget target;

      try {
         target = ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, grant.assembly(),
                                  grant.name());
      }
      catch(PairingException ex) {
         return null;
      }

      HyperlinkRegionName region = HyperlinkRegionName.parse(target.name());
      String type = assemblyTypeKey == null ? "" : typeWord(assemblyTypeKey) + " ";
      String label = "Expression of the " + words(region) + " of " + type + "'" +
         target.assemblyName() + "' (" + toolArgs(region) + "; draft-only: the value is in the " +
         "user's open Hyperlink dialog)";

      return new ScriptTargetInfo(
         target.id(), target.kind().wireName(), target.assemblyName(), target.name(), null, null,
         label, "when the link is clicked, once the user applies the dialog",
         false, true,
         "vsHyperlinkExpression:" + target.assemblyName() + ":" + target.name(),
         "viewsheet", null, null, Boolean.TRUE);
   }

   /** The empty, pending read: the dialog's text is not on the server. */
   static ScriptInfo read(ScriptTarget target) {
      return new ScriptInfo(target.toString(), "", true, Boolean.TRUE, note(target, false));
   }

   static String note(ScriptTarget target, boolean write) {
      HyperlinkRegionName region = HyperlinkRegionName.parse(target.name());
      String description = words(region) + " of '" + target.assemblyName() + "'";
      String readBack = "get_hyperlink {assembly: '" + target.assemblyName() + "'" +
         argsSuffix(region) + "} from a toolbar session";
      String rowLink = HyperlinkRegionName.CELL.equals(region.form())
         ? " A row link (Apply to Row) cannot take a script expression (set_hyperlink refuses " +
           "it) and the server cannot see that checkbox, so ask the user to leave Apply to Row " +
           "unchecked."
         : "";
      String messageLink = " A text starting with =\"message: turns the link into a message link " +
         "when applied (the dialog does that).";

      if(write) {
         return "Pushed to the open Hyperlink dialog for the " + description + " as a draft. " +
            "Nothing is applied: the user must click OK in the formula dialog and then OK in " +
            "the Hyperlink dialog; Cancel anywhere discards it. The server cannot see whether " +
            "the dialog showed it: if the user had typed, the draft waits behind the dialog's " +
            "reload prompt. Do not retry or read back to verify; after the user applies it, " +
            "verify with " + readBack + "." + messageLink + rowLink;
      }

      return "Draft-only location: the Web Link Expression of ONE hyperlink in the user's open " +
         "Hyperlink dialog (the " + description + "). The dialog's current text lives only in " +
         "the browser, so the server cannot return it; the empty text is NOT an empty " +
         "expression and NOT a failed update_script. Nothing is applied until the user clicks " +
         "OK in the formula dialog and then OK in the Hyperlink dialog. To read the hyperlink " +
         "that is currently applied, use " + readBack + " (connect_sheet with a toolbar code)." +
         rowLink;
   }

   /** The region in words, as the dialog's user would say it. */
   private static String words(HyperlinkRegionName region) {
      return switch(region.form()) {
         case HyperlinkRegionName.TITLE -> "title hyperlink";
         case HyperlinkRegionName.EMPTY_PLOT -> "empty plot hyperlink";
         case HyperlinkRegionName.CELL ->
            "hyperlink of cell [" + region.row() + "," + region.col() + "]";
         case HyperlinkRegionName.FIELD -> "hyperlink of field '" + region.column() + "'";
         case HyperlinkRegionName.AXIS -> "hyperlink of the axis of '" + region.column() + "'";
         case HyperlinkRegionName.TEXT -> "hyperlink of text '" + region.column() + "'";
         default -> "hyperlink";
      };
   }

   /** The set_hyperlink / get_hyperlink arguments that address this region, as a short phrase. */
   private static String toolArgs(HyperlinkRegionName region) {
      return switch(region.form()) {
         case HyperlinkRegionName.TITLE -> "set_hyperlink titleLink: true";
         case HyperlinkRegionName.EMPTY_PLOT -> "set_hyperlink emptyPlotLink: true";
         case HyperlinkRegionName.CELL ->
            "set_hyperlink row " + region.row() + ", col " + region.col();
         case HyperlinkRegionName.FIELD -> "set_hyperlink colName '" + region.column() + "'";
         case HyperlinkRegionName.AXIS ->
            "set_hyperlink colName '" + region.column() + "', axis: true";
         case HyperlinkRegionName.TEXT ->
            "set_hyperlink colName '" + region.column() + "', text: true";
         default -> "set_hyperlink with no region argument";
      };
   }

   private static String argsSuffix(HyperlinkRegionName region) {
      return switch(region.form()) {
         case HyperlinkRegionName.TITLE -> ", titleLink: true";
         case HyperlinkRegionName.EMPTY_PLOT -> ", emptyPlotLink: true";
         case HyperlinkRegionName.CELL -> ", row: " + region.row() + ", col: " + region.col();
         case HyperlinkRegionName.FIELD -> ", colName: '" + region.column() + "'";
         case HyperlinkRegionName.AXIS -> ", colName: '" + region.column() + "', axis: true";
         case HyperlinkRegionName.TEXT -> ", colName: '" + region.column() + "', text: true";
         default -> "";
      };
   }

   /** The Composer's own names, where the registry key runs the words together. */
   private static final Map<String, String> TYPE_WORDS = Map.of("calctable", "Freehand Table");

   private static String typeWord(String typeKey) {
      String word = TYPE_WORDS.get(typeKey);

      return word != null ? word : Character.toUpperCase(typeKey.charAt(0)) + typeKey.substring(1);
   }
}
