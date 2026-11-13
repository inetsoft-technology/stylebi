/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

/**
 * Names the script location a pane-scoped agent pairing session is bound to.
 * Sent as part of the mint request payload so the server can scope the
 * resulting session to a single script/formula location rather than the
 * whole sheet. The server validates and consumes this field at mint time
 * (see SheetPairingService.validateEditorContext).
 */
export interface EditorContext {
   kind: string;
   assembly?: string;
   name?: string;
   table?: string;
   /**
    * For kind == "calcField" only: true when this names a field being created (the browser's
    * "New Calculated Field" dialog pre-assigns a real, unique name before the user has clicked
    * OK), so the field intentionally does not exist on the runtime yet. Omitted/false for an
    * edit, where the named field must already exist. Ignored for every other kind.
    */
   pending?: boolean;
}

/**
 * Where a viewsheet Condition (filter) or Highlight dialog's condition value lives, for kind ==
 * "vsConditionValue". `assembly` is the viewsheet assembly that owns the condition (NOT the
 * worksheet table the condition's field comes from); `highlightName` is the highlight being
 * edited, or null for the assembly's Conditions (filter) list.
 */
export interface VSConditionPairingScope {
   assembly: string;
   highlightName: string | null;
}

/**
 * How a "dynamicValue" context's `name` reads on the Connect button: a bare property key as it
 * is, a namespaced one ("dateComparison:periodsBack") as the namespace and the key.
 */
export function dynamicValueLabel(name: string | null | undefined): string {
   const namespace = /^([A-Za-z]+):(.*)$/.exec(name || "");
   return namespace ? `${namespace[1]} ${namespace[2]}` : (name || "");
}

/** The `name` of a "vsConditionValue" context: "filter" or "highlight:<highlight name>". */
export function vsConditionValueName(scope: VSConditionPairingScope): string {
   return scope.highlightName != null ? "highlight:" + scope.highlightName : "filter";
}
