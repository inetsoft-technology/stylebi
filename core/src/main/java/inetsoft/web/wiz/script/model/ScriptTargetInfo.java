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

/**
 * One scriptable location, as {@code list_script_targets} reports it.
 *
 * @param id         opaque and stable; copy it back verbatim rather than composing an identifier
 * @param kind       the wire vocabulary, e.g. {@code assemblyOnClick}
 * @param assembly   the owning assembly, or {@code null} for viewsheet-level kinds
 * @param name       the calculated field's own name, for {@code calcField}; {@code null} for every
 *                   kind addressed by (kind, assembly) alone. Populated for exactly one kind today
 *                   — it exists because a calc field is keyed by (table, field) and cannot be
 *                   addressed without it.
 * @param label      human-readable; for display and logs only, never an identifier
 * @param runsWhen   when StyleBI executes this script, in plain words
 * @param enableScope the flag {@code enabled} reflects. onInit and onLoad share ONE viewsheet
 *                    flag, and an assembly's main and onClick scripts share ONE assembly flag --
 *                    so disabling "the onClick" also disables that assembly's main script
 * @param hostSheet  {@code viewsheet} or {@code worksheet}
 * @param target     the v1 delimited string, or {@code null} for a kind the legacy grammar never
 *                   addressed
 */
public record ScriptTargetInfo(String id, String kind, String assembly, String name, String label,
                               String runsWhen, boolean hasScript, boolean enabled,
                               String enableScope, String hostSheet, String target) {}
