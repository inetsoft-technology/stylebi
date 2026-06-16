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

import java.util.List;

/**
 * Layer-B live scripting environment context for the joined viewsheet.
 *
 * <p>Answers the agent's question "what exists in this viewsheet and what can I
 * reference in a script?"</p>
 *
 * @param assemblies  every assembly visible from script, with name, type, and
 *                    scriptable API type hint
 * @param globals     names of globally-available functions registered by
 *                    {@link inetsoft.report.script.viewsheet.ViewsheetScope}
 * @param contextVars names of the predefined context variables always in scope
 *                    ({@code thisViewsheet}, {@code parameter}, {@code _USER_}, etc.)
 */
public record ScriptContext(
   List<AssemblyEntry> assemblies,
   List<String> globals,
   List<String> contextVars)
{

   /**
    * A single assembly entry in the context.
    *
    * @param name         assembly name as it appears in script (e.g. {@code "Chart1"})
    * @param type         human-readable type string (e.g. {@code "chart"}, {@code "table"},
    *                     {@code "text"})
    * @param scriptableType the Tern-type-tree key for this assembly's scriptable API
    *                       (e.g. {@code "CALC"}); may be {@code null} when no prototype
    *                       API is indexed for this assembly type
    */
   public record AssemblyEntry(String name, String type, String scriptableType) {}
}
