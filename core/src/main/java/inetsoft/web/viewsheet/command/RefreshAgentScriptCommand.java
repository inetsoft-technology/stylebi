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
package inetsoft.web.viewsheet.command;

/**
 * Tells the browser holding a paired agent session that a script or calc-field expression an
 * agent just wrote now has new text, so an already-open {@code ViewsheetScriptPane} or
 * {@code FormulaEditorDialog} editing that EXACT location can refresh its own display.
 *
 * <p>{@code kind}/{@code assembly}/{@code name} mirror {@code ScriptTarget.Kind.wireName()}/
 * {@code assemblyName()}/{@code name()} (viewsheet's own {@code EditorContext} wire shape) --
 * the client matches this command against its own {@code editorContext} by those three fields,
 * never by the command's own identity alone, since several independent dialogs can be open on
 * the same runtime at once.
 *
 * <p>Deliberately distinct from {@code RefreshVSObjectCommand} (canvas repaint) and
 * {@code RefreshBindingTreeCommand}/{@code SetViewsheetInfoCommand} (read-only tree/status
 * chip): those three ONLY ever replace read-only client state, so they are safe to apply
 * unconditionally. A script/expression editor's bound text is user-EDITABLE, so a human can be
 * mid-typing in the exact dialog this command targets -- the client, not this command, is
 * responsible for deciding whether applying it would silently clobber an unsaved local edit
 * (compare the dialog's current bound text against the text it last applied/opened with; apply
 * only when they still match, otherwise surface a "stale, click to reload" affordance instead of
 * overwriting). See {@code ViewsheetScriptPane}/{@code FormulaEditorDialog}'s own handling for
 * that guard's exact logic.
 */
public class RefreshAgentScriptCommand implements ViewsheetCommand {
   public String getKind() {
      return kind;
   }

   public void setKind(String kind) {
      this.kind = kind;
   }

   /** The assembly name, or -- for {@code calcField} -- the TABLE the field belongs to. */
   public String getAssembly() {
      return assembly;
   }

   public void setAssembly(String assembly) {
      this.assembly = assembly;
   }

   /** The calc field's own name, or {@code null} for a kind with no third component. */
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   /** The new script/expression text, as just written. */
   public String getText() {
      return text;
   }

   public void setText(String text) {
      this.text = text;
   }

   private String kind;
   private String assembly;
   private String name;
   private String text;
}
