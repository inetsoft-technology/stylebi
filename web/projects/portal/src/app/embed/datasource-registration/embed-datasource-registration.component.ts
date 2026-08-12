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

import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import {
   DataSourceDefinitionModel
} from "../../../../../shared/util/model/data-source-definition-model";
import {
   DatasourcesDatasourceEditorComponent
} from "../../portal/data/data-datasource-browser/datasources-datasource/datasources-datasource-editor/datasources-datasource-editor.component";
import {
   DatasourceListing, EmbedDatasourceRegistrationService
} from "./embed-datasource-registration.service";

/**
 * Registers a StyleBI data source from outside the portal shell.
 *
 * This component owns the LIFECYCLE only — pick a type, seed from the server, hand the model to
 * StyleBI's own editor, save. Every form control, editor type, dependent-field refresh and OAuth
 * flow comes from DatasourcesDatasourceEditorComponent and the TabularView descriptor it renders.
 * That is the entire reason for wrapping rather than rebuilding: a new datasource plugin ships a
 * new descriptor and this element renders it with no change.
 */
@Component({
   selector: "embed-datasource-registration",
   templateUrl: "./embed-datasource-registration.component.html",
   styleUrls: ["./embed-datasource-registration.component.scss"],
   imports: [FormsModule, DatasourcesDatasourceEditorComponent],
   providers: [EmbedDatasourceRegistrationService],
})
export class EmbedDatasourceRegistrationComponent implements OnInit {
   /** Skip the picker and go straight to this type. */
   @Input() listingName?: string;

   @Output() registered = new EventEmitter<{ name: string; type: string }>();
   @Output() cancelled = new EventEmitter<void>();
   @Output() failed = new EventEmitter<string>();

   listings: DatasourceListing[] = [];
   datasource: DataSourceDefinitionModel = null;
   usedNames: string[] = [];
   filter = "";
   valid = false;
   saving = false;
   loading = false;

   constructor(private service: EmbedDatasourceRegistrationService) {
   }

   ngOnInit(): void {
      this.service.listings().subscribe({
         next: (l) => this.listings = l,
         error: (e) => this.failed.emit(this.message(e, "Could not load data source types")),
      });

      // Names are needed by the editor to reject a duplicate BEFORE the server does.
      this.service.existingNames().subscribe({
         next: (n) => this.usedNames = n,
         error: () => this.usedNames = [],
      });

      if(this.listingName) {
         this.pick(this.listingName);
      }
   }

   visibleListings(): DatasourceListing[] {
      const f = (this.filter || "").toLowerCase();

      return f ? this.listings.filter((l) => l.name.toLowerCase().includes(f)) : this.listings;
   }

   pick(listingName: string): void {
      this.loading = true;

      this.service.seedFromListing(listingName).subscribe({
         next: (d) => {
            this.loading = false;
            // Assigning `datasource` is what reveals the editor. On failure it stays null, so a
            // failed seed leaves the picker on screen rather than an empty form.
            this.datasource = d;
         },
         error: (e) => {
            this.loading = false;
            this.failed.emit(this.message(e, `Could not load the form for "${listingName}"`));
         },
      });
   }

   onChanged(ds: DataSourceDefinitionModel): void {
      this.datasource = ds;
   }

   cancel(): void {
      this.datasource = null;
      this.cancelled.emit();
   }

   save(): void {
      if(!this.datasource || this.saving) {
         return;
      }

      this.saving = true;

      this.service.save(this.datasource).subscribe({
         next: () => {
            this.saving = false;
            this.registered.emit({ name: this.datasource.name, type: this.datasource.type });
            this.datasource = null;
         },
         error: (e) => {
            this.saving = false;
            // Stay on the form: the operator's input must survive a rejected save.
            this.failed.emit(this.message(e, "Could not save the data source"));
         },
      });
   }

   private message(e: any, fallback: string): string {
      return e?.error?.message || e?.message || fallback;
   }
}
