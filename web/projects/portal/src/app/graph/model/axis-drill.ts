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
import { Axis } from "./axis";

/**
 * A chart axis field that can be drilled up, down or both, from the server's per-field drill data.
 */
export interface AxisDrillField {
   /** "x" or "y". */
   axisType: string;
   /** The full name of the field, such as "Month(OrderDate)". */
   field: string;
   /** The name to show for the field, without its date level: "OrderDate". */
   label: string;
   /** "+", "-" or "-+". */
   op: string;
   /** The date level of the field ("Month"), or "" if not known. */
   level: string;
   /** The date level a drill up goes to, or "" if not known or not possible. */
   up: string;
   /** The date level a drill down goes to, or "" if not known or not possible. */
   down: string;
}

/**
 * The fields of the axes that can be drilled, once each however many axes carry them, in the
 * order of the axes and of the fields on them.
 */
export function getAxisDrillFields(axes: Axis[]): AxisDrillField[] {
   const result: AxisDrillField[] = [];
   const seen = new Set<string>();

   for(const axis of axes || []) {
      const fields = axis?.axisFields;
      const ops = axis?.axisFieldOps;

      if(!fields || !ops) {
         continue;
      }

      for(let i = 0; i < fields.length && i < ops.length; i++) {
         const key = axis.axisType + "\n" + fields[i];

         if(!ops[i] || seen.has(key)) {
            continue;
         }

         seen.add(key);
         result.push({
            axisType: axis.axisType,
            field: fields[i],
            label: axisFieldLabel(fields[i]),
            op: ops[i],
            level: axis.axisLevels?.[i] ?? "",
            up: axis.axisUpLevels?.[i] ?? "",
            down: axis.axisDownLevels?.[i] ?? ""
         });
      }
   }

   return result;
}

/**
 * The name of a field without the date level it is grouped by: "Month(OrderDate)" is "OrderDate".
 */
export function axisFieldLabel(field: string): string {
   const match = /^[A-Za-z]+\((.*)\)$/.exec(field || "");
   return match ? match[1] : (field || "");
}

/**
 * The translated name of a date level the server sends, or "" if it is not one the drill buttons
 * name (the caller then uses a label without a level).
 */
export function drillLevelLabel(level: string): string {
   switch(level) {
   case "Year": return "_#(js:Year)";
   case "Quarter": return "_#(js:Quarter)";
   case "Month": return "_#(js:Month)";
   case "Week": return "_#(js:Week)";
   case "Day": return "_#(js:Day)";
   case "Hour": return "_#(js:Hour)";
   case "Minute": return "_#(js:Minute)";
   case "Second": return "_#(js:Second)";
   default: return "";
   }
}
