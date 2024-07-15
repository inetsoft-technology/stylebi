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
import { Injectable } from "@angular/core";
import { Subject, Observable } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ScriptModel } from "../../data/script/script";
import { HttpClient } from "@angular/common/http";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";


export interface NodeClickedEvent {
   node: TreeNodeModel;
   target: string;
}

@Injectable()
export class ScriptService {
   private clickedNode = new Subject<NodeClickedEvent>();

   constructor(private http: HttpClient, private modalService: NgbModal) {
   }

   setClickedNode(node: TreeNodeModel, target: string): void {
      this.clickedNode.next({
         node: node,
         target: target
      });
   }

   getClickedNode(): Observable<NodeClickedEvent> {
      return this.clickedNode.asObservable();
   }
}