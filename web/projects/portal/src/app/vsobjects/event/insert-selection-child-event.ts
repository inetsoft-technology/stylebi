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
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { VSObjectEvent } from "./vs-object-event";
import { ChangeVSObjectBindingEvent } from "../../composer/gui/vs/objects/event/change-vs-object-binding-event";
import { TableTransfer } from "../../common/data/dnd-transfer";
import { OutputColumnRefModel } from "../model/output-column-ref-model";

/**
 * Class that encapsulates an event sent to the server to instruct it to add a new object as a
 * selection child in a selection container.
 */
export class InsertSelectionChildEvent extends ChangeVSObjectBindingEvent {
   /**
    * The AssetEntry of the datasource to bind to;
    */
   public binding: AssetEntry[];
   /**
    * The OutputColumnRefModel of the datasource to bind to;
    */
   public columns: OutputColumnRefModel[];

   /**
    * The index the object will end up having within the selection container
    */
   public toIndex: number;

   /**
    * The new x position of item if creating new one;
    */
   public x: number;

   /**
    * The new y position of item if creating new one;
    */
   public y: number;

   /**
    * Creates a new instance of <tt>MoveSelectionChildEvent</tt>.
    *
    * @param objectName the name of the object
    * @param toIndex    the index to move to.
    * @param binding    the data source to bind to
    * @param tableData  the data source to bind to if coming from a table column
    */
   constructor(containerName: string, toIndex: number,
               binding: AssetEntry[], componentBinding?: TableTransfer,
               columns?: OutputColumnRefModel[])
   {
      super(containerName);

      this.toIndex = toIndex;
      this.binding = binding;
      this.componentBinding = componentBinding;
      this.columns = columns;
   }
}

