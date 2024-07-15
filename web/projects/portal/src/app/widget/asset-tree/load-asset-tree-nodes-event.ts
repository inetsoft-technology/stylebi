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
import { AssetEntry } from "../../../../../shared/data/asset-entry";

export class LoadAssetTreeNodesEvent {
   private targetEntry: AssetEntry;
   private expandedDescendants: LoadAssetTreeNodesEvent[] = [];
   private path: string[] = null;
   private index: number = 0;
   private scope: number = 0;
   private loadAll: boolean = false;

   getTargetEntry(): AssetEntry {
      return this.targetEntry;
   }

   setTargetEntry(value: AssetEntry) {
      this.targetEntry = value;
   }

   getExpandedDescendants(): LoadAssetTreeNodesEvent[] {
      return this.expandedDescendants;
   }

   setExpandedDescendants(value: LoadAssetTreeNodesEvent[]) {
      this.expandedDescendants = value;
   }

   getPath(): string[] {
      return this.path;
   }

   setPath(path: string[]) {
      this.path = path;
   }

   getScope(): number {
      return this.scope;
   }

   setScope(scope: number) {
      this.scope = scope;
   }

   getIndex(): number {
      return this.index;
   }

   setIndex(index: number) {
      this.index = index;
   }

   isLoadAll(): boolean {
      return this.loadAll;
   }

   setLoadAll(value: boolean) {
      this.loadAll = value;
   }
}
