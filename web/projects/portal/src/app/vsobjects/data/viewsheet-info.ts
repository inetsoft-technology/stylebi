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
import { VSObjectModel } from "../model/vs-object-model";

// this class is the common info used in vsobjects between composer/viewer. a Viewsheet
// is used in composer, and a ViewsheetInfo is used in viewer
export class ViewsheetInfo {
   formatPainterMode = false;

   constructor(public vsObjects: VSObjectModel[], public linkUri: string,
               public metadata?: boolean, public runtimeId?: string, public orgId?: string)
   {
   }

   public selectAssembly(assembly: any): void {
      // noop
   }

   public deselectAssembly(assembly: any): void {
      // noop
   }

   public clearFocusedAssemblies(): void {
      // noop
   }

   public isAssemblyFocused(assembly: any): boolean {
      return false;
   }
}
