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
package inetsoft.web.wiz.script.model;

import inetsoft.web.wiz.pairing.EditorContext;

import java.util.List;

/**
 * What the dialog's own tree shows for the location a session is paired at, for
 * {@code get_pane_context}: a read-only inventory of names, never a handle.
 *
 * <p>Deliberately holds nothing a composer service accepts as an identifier (no runtime id, no
 * session token, no assembly API tree): a pane token is a write handle for ONE expression, and this
 * answer must not become a way to reach anything else.</p>
 *
 * @param kind          the session's editor-context kind, or {@code "sheet"} for a whole-sheet
 *                      session
 * @param editorContext the session's own scope, {@code null} for a whole-sheet session
 * @param syntax        the language the location is evaluated in
 * @param components    every component of the viewsheet, name and type only
 * @param parameters    the parameter names a script can read as {@code parameter.<name>}
 * @param columns       the table(s) whose columns the location can read, names and types only
 * @param functions     the callable global function names; {@code get_function_signature} gives
 *                      each one's signature
 * @param notes         what could not be listed and why, empty when everything was
 */
public record PaneContext(String kind, EditorContext editorContext, Syntax syntax,
                          List<Component> components, List<String> parameters,
                          List<Table> columns, List<String> functions, List<String> notes)
{
   /** @param language always {@code "javascript"} today; @param note how to write it here. */
   public record Syntax(String language, String note) {}

   public record Component(String name, String type) {}

   /** @param current whether this is the table the location is bound to; null when unknown. */
   public record Table(String name, Boolean current, List<Field> fields) {}

   public record Field(String column, String dataType) {}
}
