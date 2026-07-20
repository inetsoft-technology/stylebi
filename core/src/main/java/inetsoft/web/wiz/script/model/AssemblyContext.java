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
 * One assembly's live-scripting surface, for {@code get_script_context}.
 *
 * @param scriptableMembers a curated, source-verified fallback list (kept for quick scanning)
 * @param apiTree           this assembly's slice of the SAME live function/property tree the
 *                          Composer's own script editor autocomplete uses
 *                          ({@code VSScriptableService.getScriptDefinition}, narrowed to
 *                          {@code root.get(assemblyName)} — the full tree includes StyleBI's
 *                          entire global function library and is unusably large). Authoritative;
 *                          includes members {@code scriptableMembers} may not (e.g.
 *                          {@code addPercentileTarget}). {@code null} if the live lookup failed
 *                          for this assembly.
 */
public record AssemblyContext(String name, String type, boolean scriptable,
                              List<String> scriptableMembers, Object apiTree) {}
