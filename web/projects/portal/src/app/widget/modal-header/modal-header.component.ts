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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";

@Component({
   selector: "modal-header",
   templateUrl: "modal-header.component.html",
   styleUrls: ["./modal-header.component.scss"]
})
export class ModalHeaderComponent {
   @Input() title: string = "";
   @Input() cshid: string = "";
   @Input() isShowAiAssistant: boolean = false;
   @Input() isShow: boolean = false;
   @Input() isDataTip: boolean = false;
   @Output() onCancel = new EventEmitter<any>();
   @Output() onShowStyle = new EventEmitter<any>();
   @Output() onExportTable = new EventEmitter<any>();

   constructor(public aiAssistantService: AiAssistantService) {
   }

   get showHelpLink(): boolean {
      return !!this.cshid && this.cshid.length > 0;
   }

   get showAssistant(): boolean {
      return !!this.isShowAiAssistant;
   }
}
