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
import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Tool } from "../../../../../../shared/util/tool";
import { PhysicalModelDefinition } from "../../data/model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../data/model/datasources/database/physical-model/physical-table-model";
import { AutoAliasJoinModel } from "../../data/model/datasources/database/physical-model/auto-alias-join-model";

const TABLE_ALIASES_URI: string = "../api/data/physicalmodel/aliases/";

@Component({
   selector: "physical-table-aliases-dialog",
   templateUrl: "physical-table-aliases-dialog.component.html",
   styleUrls: ["physical-table-aliases-dialog.component.scss"]
})
export class PhysicalTableAliasesDialog implements OnInit {
   @Input() physicalModel: PhysicalModelDefinition;
   @Input() isDuplicateTableName: (name: string) => boolean;
   @Output() onCommit: EventEmitter<PhysicalTableModel> = new EventEmitter<PhysicalTableModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   aliasTableSelections: Map<string, boolean> = new Map<string, boolean>();
   private initialized: boolean = false;
   private _table: PhysicalTableModel;

   @Input()
   set table(model: PhysicalTableModel) {
      if(this._table != model) {
         this._table = model;

         if(this.initialized) {
            this.refreshAliases();
         }
      }
   }

   get table(): PhysicalTableModel {
      return this._table;
   }

   constructor(private httpClient: HttpClient,
               private detectorRef: ChangeDetectorRef)
   {
   }

   ngOnInit(): void {
      this.initialized = true;
      this.refreshAliases();
   }

   private refreshAliases(): void {
      this.httpClient.get<AutoAliasJoinModel[]>(TABLE_ALIASES_URI + this.physicalModel.id
         + "/" + Tool.encodeURIComponentExceptSlash(this.table.qualifiedName))
         .subscribe(
            data => {
               this.table.autoAliases = data;
               this.aliasTableSelections = new Map<string, boolean>();
               data.forEach(alias => {
                  this.aliasTableSelections.set(alias.foreignTable, alias.selected);
               });
            },
            err => {}
         );
   }

   enableAutoAlias(enabled: boolean): void {
      this.detectorRef.detach();
      this.table.autoAliasesEnabled = enabled;
      this.detectorRef.reattach();
      this.detectorRef.detectChanges();
   }

   updateSelection(autoAlias: AutoAliasJoinModel, selected: boolean): void {
      this.aliasTableSelections.set(autoAlias.foreignTable, selected);

      if(!selected || (selected && autoAlias.alias != null)) {
         autoAlias.selected = selected;
      }
   }

   isTableSelected(name: string): boolean {
      return this.aliasTableSelections.get(name);
   }

   getExistsNames(autoAlias: AutoAliasJoinModel): string[] {
      const names: string[] = [];

      if(autoAlias.alias) {
         this.physicalModel.tables
            .filter((table) => this.table.qualifiedName !== table.qualifiedName)
            .forEach(table => {
               names.push(table.qualifiedName, table.alias);
               names.push(...table.autoAliases.filter(alias => !Tool.isEquals(alias, autoAlias)
                  && alias.selected)
                  .map(alias => alias.alias));
            });

         names.push(this.table.qualifiedName, this.table.alias);
         names.push(...this.table.autoAliases.filter(alias => !Tool.isEquals(alias, autoAlias)
            && alias.selected)
            .map(alias => alias.alias));
      }

      return names;
   }

   checkAliasValid(autoAlias: AutoAliasJoinModel): void {
      if(!autoAlias.alias) {
         autoAlias.alias = null;
         autoAlias.selected = false;
      }
      else {
         const name: string = autoAlias.alias;
         autoAlias.alias = null;
         // check if current value is a duplicate, if so set real selected value to false
         autoAlias.selected = !this.isDuplicateTableName(name);
         autoAlias.alias = name;
      }
   }

   cancel(): void {
      this.onCancel.emit();

   }

   ok(): void {
      this.onCommit.emit(this.table);
   }
}