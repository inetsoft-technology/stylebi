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
import { PhysicalTableModel } from "../physical-model/physical-table-model";
import { JoinModel } from "../physical-model/join-model";

export class EditJoinsEvent {
   constructor(public joinItems: EditJoinEventItem[], public id: string) {
   }
}

export class EditJoinEventItem {
   public actionType: string = "add";

   constructor(public table: PhysicalTableModel, public join?: JoinModel,
               public foreignTable?: string, public oldJoin?: JoinModel)
   {
   }
}

export class ModifyJoinEventItem extends EditJoinEventItem {
   constructor(public table: PhysicalTableModel, public oldJoin: JoinModel, public join: JoinModel) {
      super(table, join);
      this.actionType = "modify";
   }
}

export class RemoveJoinEventItem extends EditJoinEventItem {
   constructor(public table: PhysicalTableModel, public join?: JoinModel,
               public foreignTable?: string)
   {
      super(table, join);
      this.actionType = "remove";
   }
}


