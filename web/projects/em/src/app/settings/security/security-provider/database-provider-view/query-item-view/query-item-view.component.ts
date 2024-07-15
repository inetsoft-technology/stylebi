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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, ValidatorFn, Validators } from "@angular/forms";

@Component({
  selector: "em-query-item-view",
  templateUrl: "./query-item-view.component.html",
  styleUrls: ["./query-item-view.component.scss"]
})
export class QueryItemViewComponent implements OnInit {
   @Input() form: UntypedFormGroup;
   @Input() formName: string;
   @Input() validators: ValidatorFn[];
   @Input() callback: () => void;
   @Input() label: string;
   @Input() hint: string;
   @Input() btnLabel: string;

   constructor(private fb: UntypedFormBuilder) {
   }

   ngOnInit(): void {
      this.form.addControl(this.formName, this.fb.control("", this.validators));
   }

   get required(): boolean {
      return !!this.validators?.some(v => v === Validators.required);
   }
}
