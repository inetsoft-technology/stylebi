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
import { DataTransfer, BindingDropTarget, ObjectType } from "../../../common/data/dnd-transfer";
import { ChartRef } from "../../../common/data/chart-ref";

export class ChartTransfer implements DataTransfer {
   constructor(protected dragType: string, public refs: Array<ChartRef>, assembly: string) {
      this.assembly = assembly;
   }

   public classType: string = "ChartTransfer";
   public objectType: ObjectType = "vschart";
   public assembly: string;
}

export class ChartAestheticTransfer extends ChartTransfer {
   constructor(public dragType: string, public refs: Array<ChartRef>,
               protected aggr: ChartRef, assembly: string, public targetField: string)
   {
      super(dragType, refs, assembly);
   }

   public classType: string = "ChartAestheticTransfer";
}

export class ChartAestheticDropTarget extends BindingDropTarget {
   constructor(public dropType: string, public replace: boolean, private aggr: ChartRef,
               assembly: string, objectType: ObjectType, public targetField: string)
   {
      super(dropType, 0, replace, objectType, assembly);
      this.assembly = assembly;
   }

   public classType: string = "ChartAestheticDropTarget";
}
