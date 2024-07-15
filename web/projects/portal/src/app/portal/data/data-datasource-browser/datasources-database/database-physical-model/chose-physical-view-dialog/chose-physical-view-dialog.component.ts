/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
const GET_DATABASE_PHYSICAL_VIEW_URI = "../api/data/physicalmodel/views";

@Component({
  selector: "chose-physical-view-dialog",
  templateUrl: "./chose-physical-view-dialog.component.html"
})
export class ChosePhysicalViewDialog implements OnInit {
  @Input() database: string;
  @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
  @Output() onCancel: EventEmitter<any> = new EventEmitter<any>();
  physicalViews: string[] = [];
  form: UntypedFormGroup;

  constructor(private http: HttpClient, protected modalService: NgbModal) { }

  ngOnInit(): void {
    this.form = new UntypedFormGroup({
      selectedView: new UntypedFormControl(null, [ Validators.required ])
    });

    let params: HttpParams = new HttpParams().set("database", this.database);
    this.http.get<string[]>(GET_DATABASE_PHYSICAL_VIEW_URI, {params: params})
       .subscribe((views) => {
         if(views && views.length > 0) {
           this.physicalViews = views;
           this.form.get("selectedView")?.patchValue(views[0]);
         }
       });
  }

  cancel(): void {
    this.onCancel.emit();
  }

  ok(): void {
    this.onCommit.emit(this.form?.get("selectedView")?.value);
  }
}
