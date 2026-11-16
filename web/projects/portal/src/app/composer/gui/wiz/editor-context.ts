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

import { ViewsheetClientService } from "../../../common/viewsheet-client";

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

/**
 * What an Expression editor host hands `ExpressionEditor` so the formula dialog it opens can pair:
 * the runtime and socket to mint against, and the exact location being edited. Supplied as a
 * function the editor evaluates once per open, never as a fresh object per change detection.
 */
export interface ExpressionPairing {
   runtimeId: string;
   socketConnection: ViewsheetClientService;
   editorContext: EditorContext;
}

/**
 * What the worksheet Database Query dialog hands its two direct-SQL-edit panes: the worksheet
 * runtime and socket, the query table's name, and whether that table is still being created (the
 * dialog was opened from Create > Database Query, so the table does not exist until OK).
 */
export interface SqlAgentPairing {
   runtimeId: string;
   socketConnection: ViewsheetClientService;
   table: string;
   pending: boolean;
}

/**
 * How a "vsHyperlinkExpression" context's `name` (the region key, `set_hyperlink`'s own
 * vocabulary) reads on the Connect button: "cell[2,1]" is "cell row 2, col 1", "field[X]" is
 * "field 'X'", and so on.
 */
export function hyperlinkRegionLabel(name: string | null | undefined): string {
   const region = name || "";

   if(region === "link") {
      return "link";
   }

   if(region === "title") {
      return "title link";
   }

   if(region === "emptyPlot") {
      return "empty plot link";
   }

   const cell = /^cell\[(\d+),(\d+)\]$/.exec(region);

   if(cell) {
      return `cell row ${cell[1]}, col ${cell[2]}`;
   }

   const column = /^(field|axis|text)\[([\s\S]*)\]$/.exec(region);

   if(column) {
      return column[1] === "axis" ? `axis of '${column[2]}'` : `${column[1]} '${column[2]}'`;
   }

   return region;
}

/**
 * The Connect button label of the draft-only kinds this module knows, or null for any other
 * kind (the caller then falls back to its generic `<assembly> → <kind>: <name>` shape).
 */
export function draftTargetLabel(target: EditorContext): string | null {
   switch(target.kind) {
   case "worksheetVariableDefault":
      return `${target.assembly} → default value${target.pending ? " (new variable)" : ""}`;
   case "vsHyperlinkExpression":
      return `${target.assembly} → hyperlink: ${hyperlinkRegionLabel(target.name)}`;
   case "worksheetSqlQuery":
      return `${target.assembly} → SQL${target.pending ? " (new query)" : ""}`;
   default:
      return null;
   }
}
