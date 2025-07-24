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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { createAssetEntry } from "../../../../../../../shared/data/asset-entry";
import { ChartConfig } from "../../../../common/util/chart-config";
import { StyleConstants } from "../../../../common/util/style-constants";
import { ModelService } from "../../../../widget/services/model.service";

const NUM_SHAPES: number = 16;

@Component({
   selector: "static-shape-pane",
   templateUrl: "static-shape-pane.component.html",
   styleUrls: ["static-shape-pane.component.scss"]
})
export class StaticShapePane implements OnInit {
   @Input() nilSupported: boolean = false;
   @Input() assetId: string;
   @Output() shapeChanged = new EventEmitter<string>();
   @ViewChild("uploadInput") uploadInput: ElementRef;

   currentPage: number = 0;
   shapes = ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES);
   private _shapeStr: string;

   @Input()
   set shapeStr(_shape: string) {
      this._shapeStr = _shape;
      this.initCurrentPage(this._shapeStr);
   }

   get shapeStr(): string {
      return this._shapeStr;
   }

   constructor(private http: HttpClient,
               private modelService: ModelService)
   {
   }

   ngOnInit(): void {
      this.loadShapes(null, this.getAssetOrgId());
   }

   get currentViewIndices(): number[] {
      return Array.from(new Array(NUM_SHAPES),
         (x, i) => i + NUM_SHAPES * this.currentPage);
   }

   get previousEnabled(): boolean {
      return this.currentPage > 0;
   }

   showPrevious(): void {
      if(this.previousEnabled) {
         this.currentPage--;
      }
   }

   get nextEnabled(): boolean {
      return this.currentPage < this.shapes.length / NUM_SHAPES - 1;
   }

   showNext(): void {
      if(this.nextEnabled) {
         this.currentPage++;
      }
   }

   selectShape(nshape: string): void {
      this.shapeChanged.emit(nshape);
   }

   clearShape(): void {
      this.shapeChanged.emit(StyleConstants.NIL + "");
   }

   addShape(): void {
      this.uploadInput.nativeElement.value = "";
      this.uploadInput.nativeElement.click();
   }

   public fileChanged(event: any) {
      let fileList: FileList = event.target.files;

      if(fileList.length > 0) {
         let recentFile = fileList.item(fileList.length - 1).name;
         let formData: FormData = new FormData();

         for(let i = 0; i < fileList.length; i++) {
            formData.append("file", fileList.item(i));
         }

         this.http.post("../api/chart/shape/upload", formData)
            .subscribe(result => {
               if(result) {
                  this.loadShapes(recentFile, this.getAssetOrgId());
               }
            });
      }
   }

   loadShapes(shapeName?: string, orgId?: string): void {
      let httpParams: HttpParams = new HttpParams();

      if(orgId) {
         httpParams = httpParams.set("orgId", orgId);
      }

      this.modelService.getModel<string[]>("../api/composer/imageShapes", httpParams)
         .subscribe(data => {
            this.shapes = ChartConfig.SHAPE_STYLES.concat(data);
            this.initCurrentPage(shapeName || this._shapeStr);
         });
   }

   initCurrentPage(shapeName: string): void {
      this.currentPage = this.shapes?.indexOf(shapeName) >= NUM_SHAPES ?
         Math.floor(this.shapes?.indexOf(shapeName) / NUM_SHAPES) : this.currentPage;
   }

   private getAssetOrgId(): string {
      if(this.assetId) {
         return createAssetEntry(this.assetId).organization;
      }

      return null;
   }

}
