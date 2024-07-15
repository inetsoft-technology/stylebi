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
import { WSAssemblyInfo } from "./ws-assembly-info";
import { WSDependency } from "./ws-dependency";

export interface WSAssembly {
   readonly name: string;
   readonly description: string;

   /** Absolute distance from top & left of the container */
   top: number;
   left: number;
   width?: number;
   height?: number;
   readonly dependeds: WSDependency[]; // Assemblies this assembly depends on
   readonly dependings: string[]; // Assemblies that depend on this assembly
   readonly primary: boolean;
   readonly info: WSAssemblyInfo;
   readonly classType: "TableAssembly" | "VariableAssembly" | "GroupingAssembly";
}