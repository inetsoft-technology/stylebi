/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Component,
   DoCheck,
   ElementRef,
   KeyValueDiffer,
   KeyValueDiffers,
   AfterViewInit,
   ViewChild
} from "@angular/core";
import { AestheticIconCell } from "./aesthetic-icon-cell";

@Component({
   selector: "size-cell",
   template: `<canvas #canvasElem style="vertical-align:middle;"></canvas>`
})
export class SizeCell extends AestheticIconCell implements AfterViewInit, DoCheck {
   @ViewChild("canvasElem") canvasElem: ElementRef;
   differ: KeyValueDiffer<any, any>;

   constructor(differs: KeyValueDiffers) {
      super();
      this.differ = differs.find({}).create();
   }

   ngAfterViewInit() {
      this.paintsizeCell(this.frameModel, this.canvasElem.nativeElement);
   }

   /**
    * When sizeFrame size property changed, we should auto paint size cell.
    */
   ngDoCheck() {
      let changes = this.differ.diff(this.frameModel);

      if(changes) {
         changes.forEachChangedItem((elt: any) => {
            if(elt.key === "size") {
               this.paintsizeCell(this.frameModel, this.canvasElem.nativeElement);
            }
        });
      }
   }

   private paintsizeCell(sframe: any, canvas: any) {
      let imgSrc: string = "";
      canvas.width = this.cellWidth;
      canvas.height = this.cellHeight;
      canvas.style.border = "1px solid #cccccc";

      let cxt = canvas.getContext("2d");
      cxt.clearRect(0, 0, canvas.width, canvas.height);

      if(!sframe) {
         imgSrc = "assets/size_linear.png";
      }
      else if(sframe.clazz.indexOf("StaticSizeModel") != -1 && !this.isMixed) {
         let w0 = Math.ceil(canvas.width * sframe.size / 30);
         let x0 = (canvas.width - w0) / 2;

         cxt.fillStyle = "#a6e7e4";
         cxt.fillRect(x0, 0, w0, canvas.height);
      }
      else if(sframe.clazz.indexOf("SizeModel") != -1) {
         imgSrc = "assets/size_linear.png";
      }

      if(imgSrc) {
         let img = new Image();
         img.src = imgSrc;

         img.onload = function(e) {
            cxt.drawImage(img, 0, 0);
         };
      }
   }
}
