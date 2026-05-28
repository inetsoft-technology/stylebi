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

// Fix VSViewsheet ↔ VSObjectContainer circular dependency.
//
// Both components import each other in their @Component.imports arrays. In Jest's CommonJS
// module loader, when vs-object-container.component.ts is first loaded (triggered by
// VSViewsheet's import of it), it tries to re-import VSViewsheet — but VSViewsheet's module
// is still mid-execution, so require() returns a partial export where VSViewsheet is undefined.
// The @Component decorator runs immediately, storing undefined in ɵcmp.dependencies.
//
// By eagerly importing both here, VSObjectContainer's dependency chain (VSCalendar→MonthCalendar,
// VSSelection→SelectionListCell, etc.) all load in the correct order with proper ɵcmp.dependencies
// arrays. Only VSObjectContainer itself ends up with an undefined slot, which we fix below.
//
// This file must run in setupFilesAfterEnv (not setupFiles) because it imports Angular components
// that require @angular/compiler to be available.
import { VSViewsheet } from "./app/vsobjects/objects/viewsheet/vs-viewsheet.component";
import { VSObjectContainer } from "./app/vsobjects/objects/vs-object-container.component";

const _ocDef = (VSObjectContainer as any).ɵcmp;
if (_ocDef) {
   const _rawDeps = typeof _ocDef.dependencies === "function"
      ? _ocDef.dependencies()
      : _ocDef.dependencies;
   if (Array.isArray(_rawDeps)) {
      const _fixed = _rawDeps.map((d: any) => (d === undefined || d === null) ? VSViewsheet : d);
      _ocDef.dependencies = _fixed;
   }
}
