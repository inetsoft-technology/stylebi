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
package inetsoft.web.wiz.pairing;

/**
 * Names the script/formula location a pane-scoped agent pairing session is bound to.
 *
 * <p>Sent by the browser as part of the mint request payload so the resulting
 * {@link PairingGrant} can be scoped to a single script/formula location rather than the whole
 * sheet. A toolbar ("Connect to Claude") mint names no location and sends no
 * {@code editorContext} at all — {@code null} is the normal, whole-sheet case and is never an
 * error.
 *
 * <p>{@code kind} is the only required component — it is the wire vocabulary produced by the
 * browser's script panes and formula editor (e.g. {@code "viewsheetOnInit"},
 * {@code "viewsheetOnLoad"}, {@code "assemblyMain"}, {@code "assemblyOnClick"},
 * {@code "calcField"}, {@code "worksheetExpression"}, {@code "worksheetCondition"}).
 *
 * <p>{@code assembly} names the assembly the location belongs to, for the kinds that are
 * scoped to one; it is {@code null} for the two whole-viewsheet script kinds
 * ({@code viewsheetOnInit}/{@code viewsheetOnLoad}).
 *
 * <p>For {@code kind == "calcField"}, the field is addressed by (table, name) rather than by
 * assembly — {@code Viewsheet.getCalcField} is keyed by table, not by an assembly reference.
 * {@code table} is the owning table's name; when the caller sends it in {@code assembly}
 * instead (as the current browser wiring does, mirroring {@code ScriptTarget}'s own
 * {@code assemblyName()} javadoc), {@code assembly} is accepted as the table name too — the
 * intent is unambiguous either way. {@code name} is the calc field's own name.
 */
public record EditorContext(String kind, String assembly, String name, String table) {
}
