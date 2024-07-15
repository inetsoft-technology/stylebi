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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

const DEFAULT_CONNECTION: string = "(Default Connection)";
const GET_DATABASE_ADDITIONAL_CONNECTIONS_URI: string = "../api/portal/data/database/additionConnections";
const EXTENDED_LOGICAL_DUPLICATE_NAME_CHECK_URI: string = "../api/data/logicalModel/extended/checkDuplicate";
const EXTENDED_PHYSICAL_DUPLICATE_NAME_CHECK_URI: string = "../api/data/physicalModel/extended/checkDuplicate";

@Component({
  selector: "chose-additional-connection-dialog",
  templateUrl: "./chose-additional-connection-dialog.component.html",
  styleUrls: ["./chose-additional-connection-dialog.component.scss"]
})
export class ChoseAdditionalConnectionDialog implements OnInit {
  @Input() database: string;
  @Input() isView: boolean = false;
  @Input() physicalView: string;
  @Input() parent: string;
  @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
  @Output() onCancel: EventEmitter<any> = new EventEmitter<any>();
  connections: string[] = [];
  public static default_connection: string = DEFAULT_CONNECTION;
  selectedConnection: string = DEFAULT_CONNECTION;
  helpLinkKey: string;

  constructor(private http: HttpClient, protected modalService: NgbModal) { }

  ngOnInit(): void {
    this.connections = [DEFAULT_CONNECTION];
    let params: HttpParams = new HttpParams().set("database", this.database);
    this.http.get<string[]>(GET_DATABASE_ADDITIONAL_CONNECTIONS_URI, {params: params})
       .subscribe((connections) => {
         this.connections.push(...connections);
       });
  }

  /**
   * Check extended model duplicate name
   * @param name new name.
   * @param parent parent model name.
   * @param physicalModel physical model of logical model name.
   */
  private checkExtendedDuplicate(): Observable<boolean> {
    let checkUri;

    if(this.isView) {
      checkUri = EXTENDED_PHYSICAL_DUPLICATE_NAME_CHECK_URI;
    }
    else {
      checkUri = EXTENDED_LOGICAL_DUPLICATE_NAME_CHECK_URI;
    }

    let params: HttpParams = new HttpParams()
       .set("name", this.selectedConnection)
       .set("parent", this.parent)
       .set("database", this.database);

    if(!this.isView && this.physicalView) {
      params = params.set("physicalModel", this.physicalView);
    }

    return this.http.get<boolean>(checkUri, {params: params});
  }

  getTitle(): string {
    return this.isView ? "_#(js:Extended View)" : "_#(js:Extended Model)";
  }

  get helpLink(): string {
    return this.isView ? "ExtendingPhysicalView" : "ExtendedModelPortal";
  }

  cancel(): void {
    this.onCancel.emit();
  }

  ok(): void {
    this.checkExtendedDuplicate().subscribe(exist => {
      if(exist) {
        let message = this.isView ? "_#(js:designer.qb.common.wizard.physicalNameExists)" :
           "_#(js:designer.qb.common.wizard.logicalNameExists)";
        ComponentTool.showMessageDialog(this.modalService, "_#(js:Message)",
           message);
      }
      else {
        this.onCommit.emit(this.selectedConnection);
      }
    });
  }
}
