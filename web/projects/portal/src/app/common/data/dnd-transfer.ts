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
import { Rectangle } from "./rectangle";

export type ObjectType =
   "vstable" | "vscrosstab" | "vscalctable" | "vschart" | "table" | "crosstab" | "chart" | "vsselection";

export interface DataTransfer {
   classType: string;
   assembly?: string; // source assembly
   objectType?: ObjectType; // source object type
}

export interface DropTarget {
   classType: string;
   assembly?: string; // target assembly
   objectType?: ObjectType; // target object type
}

// DropTarget for table and chart binding pane.
export class BindingDropTarget implements DropTarget {
   constructor(public dropType: string, public dropIndex: number,
               public replace: boolean, objectType: ObjectType, assembly: string)
   {
      this.assembly = assembly;
      this.objectType = objectType;
   }

   public classType: string = "BindingDropTarget";
   public assembly: string; // target assembly
   public objectType: ObjectType; // target object type
   public transferType: TransferType = "field";
}

export class CalcDropTarget implements DropTarget {
   constructor(protected dropRect: Rectangle, assembly: string) {
      this.assembly = assembly;
   }

   public classType: string = "CalcDropTarget";
   public objectType: ObjectType = "vscalctable";
   public assembly: string;
}

export class ChartViewDropTarget extends BindingDropTarget {
   constructor(dropType: string, assembly: string) {
      super(dropType, 0, false, "vschart", assembly);
   }

   public classType: string = "ChartViewDropTarget";
}

export class TableTransfer implements DataTransfer {
   constructor(public dragType: string, public dragIndex: number, assembly: string) {
      this.assembly = assembly;
   }

   public classType: string = "TableTransfer";
   public objectType: ObjectType = "vstable";
   public assembly: string;
   public transferType: TransferType = "field";
}

export class CalcTableTransfer implements DataTransfer {
   constructor(private dragRect: Rectangle, assembly: string) {
      this.assembly = assembly;
   }

   public classType: string = "CalcTableTransfer";
   public objectType: ObjectType = "vscalctable";
   public assembly: string;
}

type TransferType = "table" | "field";
