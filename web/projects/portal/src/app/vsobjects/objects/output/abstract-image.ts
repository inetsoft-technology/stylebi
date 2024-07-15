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
import { Directive, Input, NgZone, OnDestroy } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { ContextProvider } from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { VSOutputModel } from "../../model/output/vs-output-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";

/**
 * Abstract base class for image-like components such as VSGauge and VSImage. These
 * components are nearly identical in terms of their design here.
 */
@Directive()
export abstract class AbstractImageComponent<T extends VSOutputModel>
extends AbstractVSObject<T> implements OnDestroy
{
   @Input() alwaysAllowClickPropagation: boolean = false;

   @Input() set selected(selected: boolean) {
      this._selected = selected;

      if(!selected && this.model) {
         this.model.selectedAnnotations = [];
      }
   }

   get selected(): boolean {
      return this._selected;
   }

   protected actionSubscription: Subscription;
   private _actions: AbstractVSActions<T>;
   private _selected: boolean;
   loading: boolean = false;

   @Input()
   set actions(actions: AbstractVSActions<T>) {
      this._actions = actions;
      this.unsubscribe();

      if(actions) {
         this.actionSubscription = actions.onAssemblyActionEvent.subscribe(
            (event) => this.onAssemblyActionEvent(event));
      }
   }

   get actions(): AbstractVSActions<T> {
      return this._actions;
   }

   constructor(protected viewsheetClient: ViewsheetClientService,
               private modalService: NgbModal,
               protected dropdownService: FixedDropdownService,
               zone: NgZone,
               protected contextProvider: ContextProvider,
               protected hyperlinkService: ShowHyperlinkService,
               protected dataTipService: DataTipService,
               private richTextService: RichTextService)
   {
      super(viewsheetClient, zone, contextProvider, dataTipService);
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();
      this.unsubscribe();
   }

   private unsubscribe(): void {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   public abstract getSrc(): string;

   protected abstract getHyperlinks(): HyperlinkModel[];

   protected abstract onAssemblyActionEvent(event: AssemblyActionEvent<T>): void;

   /**
    * Show the annotation editor dialog.
    *
    * @param event          the event that triggered the annotation used to position the
    *                       annotation after the dialog is closed
    * @param includeContent whether to populate the bound assembly data into the text
    *                       area of the dialog
    */
   protected showAnnotationDialog(event: MouseEvent, includeContent: boolean = true): void {
      this.richTextService.showAnnotationDialog((content) => {
         this.addAnnotation(content, event);
      }).subscribe(dialog => {
         if(includeContent) {
            dialog.initialContent = this.model.defaultAnnotationContent;
         }
      });
   }

   clickHyperlink(event: MouseEvent) {
      let links = this.getHyperlinks();
      let mobile: boolean = GuiTool.isMobileDevice();

      if(this.viewer && (!mobile || this.hyperlinkService.singleClick) &&
         links != null && links.length == 1)
      {
         const link = Tool.clone(links[0]);

         if(this.context.preview && !link.targetFrame) {
            link.targetFrame = "previewTab";
         }

         this.hyperlinkService.clickLink(link, this.viewsheetClient.runtimeId,
            this.vsInfo.linkUri);

         return;
      }

      const clickActions = this.actions ? this.actions.clickAction : null;

      if(clickActions && event.button === 0) {
         if(!this.alwaysAllowClickPropagation) {
            event.stopPropagation();
         }
      }
   }

   loaded(success: boolean) {
      this.loading = false;
   }
}
