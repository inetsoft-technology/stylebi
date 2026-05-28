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
import { HttpClient } from "@angular/common/http";
import { importProvidersFrom } from "@angular/core";
import { Routes } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ChatModule } from "../common/chat/chat.module";
import { BindingService } from "../binding/services/binding.service";
import { VSBindingService } from "../binding/services/vs-binding.service";
import { BindingTreeService } from "../binding/widget/binding-tree/binding-tree.service";
import { VSBindingTreeService } from "../binding/widget/binding-tree/vs-binding-tree.service";
import { DefaultCodemirrorService } from "../../../../shared/util/codemirror/default-codemirror.service";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import { UIContextService } from "../common/services/ui-context.service";
import { FileUploadService } from "../common/services/file-upload.service";
import { ModelService } from "../widget/services/model.service";
import { ComposerAppComponent } from "./app.component";
import { ClipboardService } from "./gui/clipboard.service";
import { ComboBoxEditorValidationService } from "./dialog/vs/combo-box-editor-validation.service";
import { ComposerObjectService } from "./gui/vs/composer-object.service";
import { ComposerRecentService } from "./gui/composer-recent.service";
import { ComposerToken } from "../vsobjects/context-provider.service";
import { ComposerToolbarService } from "./gui/composer-toolbar.service";
import { DataTreeValidatorService } from "../vsobjects/dialog/data-tree-validator.service";
import { EventQueueService } from "./gui/vs/event-queue.service";
import { LineAnchorService } from "./services/line-anchor.service";
import { PropertyDialogService } from "../vsobjects/util/property-dialog.service";
import { ResizeHandlerService } from "./gui/resize-handler.service";
import { composerResolver } from "./services/composer-resolver.service";
import { SelectionContainerChildrenService } from "../vsobjects/objects/selection/services/selection-container-children.service";
import { VSTrapService } from "../vsobjects/util/vs-trap.service";
import { WsChangeService } from "./gui/ws/editor/ws-change.service";

export const composerRoutes: Routes = [
   {
      path: "",
      component: ComposerAppComponent,
      resolve: { setPrincipalCommand: composerResolver },
      providers: [
         ClipboardService,
         ComposerObjectService,
         ComposerToolbarService,
         DataTreeValidatorService,
         EventQueueService,
         ResizeHandlerService,
         SelectionContainerChildrenService,
         LineAnchorService,
         FileUploadService,
         UIContextService,
         VSTrapService,
         PropertyDialogService,
         VSBindingTreeService,
         ComboBoxEditorValidationService,
         { provide: ComposerToken, useValue: true },
         {
            provide: BindingService,
            useClass: VSBindingService,
            deps: [ModelService, HttpClient, UIContextService]
         },
         { provide: BindingTreeService, useExisting: VSBindingTreeService },
         WsChangeService,
         ComposerRecentService,
         NgbModal,
         { provide: CodemirrorService, useClass: DefaultCodemirrorService },
         importProvidersFrom(ChatModule.forRoot("5ba3fdd5c666d426648af5c9/default")),
      ]
   }
];
