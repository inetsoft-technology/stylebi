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
import { DOCUMENT, ɵparseCookieValue as parseCookieValue } from "@angular/common";
import {
   AfterViewChecked,
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Inject,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output, Renderer2,
   SecurityContext,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { GuiTool } from "../../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { TextActions } from "../../../action/text-actions";
import { ForceEditModeCommand } from "../../../command/force-edit-mode-command";
import { UpdateExternalUrlCommand } from "../../../command/update-external-url-command";
import { ContextProvider } from "../../../context-provider.service";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { ChangeVSObjectTextEvent } from "../../../event/change-vs-object-text-event";
import { PrintLayoutSection } from "../../../model/layout/print-layout-section";
import { VSTextModel } from "../../../model/output/vs-text-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { AbstractVSObject } from "../../abstract-vsobject.component";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { AutoCompleteModel } from "../../../../widget/auto-complete/auto-complete-model";

const DEFAULT_WHITESPACE_FORMAT = "normal";

interface ExternalUrlsMessage {
   type: string;
   token: string;
   urls: {[name: string]: string};
}

@Component({
   selector: "vs-text",
   templateUrl: "vs-text.component.html",
   styleUrls: ["vs-text.component.scss"]
})
export class VSText extends AbstractVSObject<VSTextModel>
   implements OnInit, OnDestroy, OnChanges, AfterViewInit, AfterViewChecked
{
   @ViewChild("textView") textView: ElementRef;
   @Input() editableLayout: boolean = false;
   @Input() layoutRegion: PrintLayoutSection = PrintLayoutSection.HEADER;
   @Input() alwaysAllowClickPropagation: boolean = false;
   @Input() set selected(selected: boolean) {
      this._selected = selected;

      if(!selected && this.model) {
         this.model.selectedAnnotations = [];
         this.model.editing = false;

         if(this.createdByDblClick) {
            this.createdByDblClick = false;
            this.updateWidth.emit(this.model.objectFormat.width);
         }
      }
   }

   get selected(): boolean {
      return this._selected;
   }

   @ViewChild("objectContentTd") objectContentTd: ElementRef;
   @ViewChild("objectContentTextarea") objectContentTextarea: ElementRef;
   @ViewChild("externalFrame") externalFrame: ElementRef;
   @Output() textHeightChanged: EventEmitter<number> = new EventEmitter<number>();
   @Output() updateWidth: EventEmitter<number> = new EventEmitter<number>();
   @Output() onOpenFormatPane = new EventEmitter<VSTextModel>();
   textHeight: number = 18;
   cursor: string = "cursor";
   tooltip: string = "";
   wordWrap: boolean = true;
   private actionSubscription: Subscription;
   private _actions: TextActions;
   private _updateOnChange = true;
   private pendingChange = false;
   private _selected: boolean;
   htmlText: string;
   displayText: string;
   createdByDblClick: boolean = false;
   safeUrlText: SafeResourceUrl;
   private _clientText: string = null;
   private _clientWidth: number = 0;
   private _clientHeight: number = 0;
   private messageListener: (event: any) => void;

   @Input()
   set actions(actions: TextActions) {
      this._actions = actions;
      this.unsubscribe();

      if(actions) {
         this.actionSubscription = actions.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "text annotate":
               this.showAnnotateDialog(event.event);
               break;
            case "text show-hyperlink":
               this.hyperlinkService.showHyperlinks(event.event,
                                    this.model.hyperlinks, this.dropdownService,
                                    this.viewsheetClient.runtimeId, this.vsInfo.linkUri,
                                    this.isForceTab());
               break;
            case "text show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            }
         });
      }
   }

   get actions(): TextActions {
      return this._actions;
   }

   @Input()
   set updateOnChange(value: boolean) {
      if(!this._updateOnChange && value && this.pendingChange) {
         this.sendChangeEvent();
      }

      this._updateOnChange = value;
   }

   get updateOnChange(): boolean {
      return this._updateOnChange;
   }

   @Input() set model(m: VSTextModel) {
      // don't lose edited text after resize
      if(this.model && m && this.model.editing) {
         m.text = this.model.text;
      }

      this._model = m;
   }

   get model(): VSTextModel {
      return this._model;
   }

   constructor(protected viewsheetClient: ViewsheetClientService,
               private popComponentService: PopComponentService,
               protected dataTipService: DataTipService,
               private modalService: NgbModal,
               private dropdownService: FixedDropdownService,
               private contextProvider: ContextProvider,
               private changeDetectionRef: ChangeDetectorRef,
               private hyperlinkService: ShowHyperlinkService,
               private domSanitizer: DomSanitizer,
               private debounceService: DebounceService,
               zone: NgZone,
               private renderer: Renderer2,
               private richTextService: RichTextService,
               @Inject(DOCUMENT) private document: HTMLDocument)
   {
      super(viewsheetClient, zone, contextProvider, dataTipService);
   }

   ngOnInit() {
      this.textChanged();

      if(!!this.model?.url && !!this.document.defaultView) {
         this.messageListener = evt => {
            if(evt.origin === this.getExternalFrameOrigin() &&
               evt.data?.type === "inetsoftGetExternalUrls")
            {
               this.sendExternalUrls();
            }
         };
         this.document.defaultView.addEventListener("message", this.messageListener, false);
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      this.textChanged();
      // wait the html render to complete, because the calculate dependency dom size.
      setTimeout(() => this.changeHeightOfTextarea());
      const modelChanges = changes["model"];

      if(this.model.url && modelChanges != null) {
         const prevModel = modelChanges.previousValue as VSTextModel | null;

         if(prevModel == null || !prevModel.url || this.model.text != prevModel.text) {
            this.updateUrlText(this.model.text);
         }
      }
   }

   textChanged() {
      this.htmlText = this.getHTMLText();
      this.displayText = this.getDisplayText();
      this.wordWrap = !this.model.breakAll;
   }

   getDisplayText() {
      // If not auto size and html text, should using ... show longer content.
      if(this.viewer && !this.model.autoSize && !this.isHTMLContent(this.htmlText)) {
         return this.getEllipsisText(this.htmlText,  this.model.objectFormat.font);
      }

      return this.htmlText;
   }

   // EllipsisText according its width and height.
   // 1. is not wrap, using text-overflow to cut text and add ellipsis.
   // 2. is wrap, it will wrap by word, so using word to calculate if wrap, if one wrod is wrapped,
   //  cut it and add ellipsis to end.
   // 3. for br, add br to check if height is more than text height, if yes, do not add br and
   // add ellipsis before br.
   getEllipsisText(ostr, font): string {
      if(ostr == null) {
         return ostr;
      }

      // trailing space should be ignored
      while(ostr.endsWith("&nbsp;")) {
         ostr = ostr.substring(0, ostr.length - 6);
      }

      const lineH = this.model.objectFormat.lineHeight;
      const textW = this.model.objectFormat.width;
      let textH = this.model.objectFormat.height;
      const test = window.document.createElement("div");
      test.style.padding = "1px";
      test.style.overflowWrap = this.model.objectFormat.wrapping.wordWrap;
      test.style.whiteSpace = this.model.objectFormat.wrapping.whiteSpace;
      test.style.font = this.model.objectFormat.font;
      test.innerHTML = ostr;
      window.document.body.appendChild(test);

      test.style.width = "99999999px";
      // height without wrapping
      const nowrapH = test.clientHeight;

      // allow small margin of error where a small truncation is not noticeable.
      test.style.width = (textW + 1) + "px";
      const actualH = test.clientHeight;

      // if only one line, should not cut off the text.
      // if text fits in textW without wrapping, don't truncate.
      // if text is very samller to one line, should show as original.
      if(actualH <= textH || actualH == nowrapH || textH < lineH / 2) {
         window.document.body.removeChild(test);
         return ostr;
      }

      // if text is samller only to one line, using large text height to calculate to show one row.
      if(textH < lineH + 5 && textH > lineH / 2) {
         textH = lineH + lineH / 2;
      }

      // If only one string, should calculate according to every characters.
      if(ostr.indexOf(" ") < 0) {
         return this.getNoWordString(ostr, test, textH);
      }

      // For text is word wrap, so we should split string by word, so should add the string word by
      // word, then to check if wrap(height is larger), then if one word is longer to some rows,
      // should add one chart to check every chart to wrap.
      return this.getWordString(ostr, test, textH);
   }

   getNoWordString(ostr: string, test: HTMLElement, textH: number): string {
      // keep old string do not change, using nestringn to change.
      let nstring = "";
      let actualH = 0;

      // If only one string, should calculate according to every characters.
      test.innerHTML = "";
      let lastString = "";
      let textCut = false;

      for(let str of ostr) {
         nstring += str;
         test.innerHTML = nstring + "...";
         actualH = test.clientHeight;

         // if new html is large, should revert to old strings.
         if(actualH >= textH) {
            textCut = true;

            if(nstring.endsWith("<br>")) {
               lastString = nstring.substring(0, nstring.length - 4);
            }

            break;
         }

         lastString = nstring;
      }

      nstring = textCut ? lastString + "..." : lastString;
      window.document.body.removeChild(test);

      return nstring;
   }

   getWordString(ostr: string, test: HTMLElement, textH: number): string {
      let wordArray = ostr.split(" ");
      test.innerHTML = "";
      let lastString = "";
      let nstring = "";
      let actualH = 0;
      let textCut = false;

      // if height is larger, pop one word, if there is only one word, or current word is smaller
      // than one row, should add character by character to check warp characters.
      for(let i = 0; i < wordArray.length; i++) {
         let prefix = i == 0 ? "" : " ";
         nstring += prefix + wordArray[i];
         test.innerHTML = nstring + "...";
         actualH = test.clientHeight;

         // If current word is larger than text height, add one character by character to check.
         // since maybe last word is long, so cut it off and add "..." in the end.
         if(actualH >= textH) {
            textCut = true;
            nstring = lastString + prefix;
            test.innerHTML = nstring + "...";
            actualH = test.clientHeight;

            // If can not add one characters, should only return old string.
            if(actualH >= textH) {
               return lastString + "...";
            }

            for(let char of wordArray[i]) {
               nstring += char;
               test.innerHTML = nstring + "...";
               actualH = test.clientHeight;

               if(actualH >= textH) {
                  break;
               }

               lastString = nstring;
            }

            nstring = lastString;
         }

         lastString = nstring;
      }

      nstring = textCut ? lastString.substring(0, nstring.length - 1) + "..." : lastString;
      window.document.body.removeChild(test);

      return nstring;
   }

   isHTMLContent(str): boolean {
      let test = document.createElement("div");
      test.innerHTML = str;
      let c = test.childNodes;

      for(let i = c.length; i--; i > -1) {
         // if only enter in text, support it to cut and add ellipsis
         if(c[i].nodeType == 1 && c[i].nodeName != "BR") {
            return true;
         }
      }

      return false;
   }

   modelChanged() {
      if(this.model.tooltipVisible) {
         if(!!this.model.customTooltipString) {
            this.tooltip = this.model.customTooltipString;
         }
         else {
            // Tooltip should be a hyperlink tooltip if it is present
            if(this.model.hyperlinks && this.model.hyperlinks[0] &&
               this.model.hyperlinks[0].tooltip)
            {
               this.tooltip = this.model.hyperlinks[0].tooltip;
            }
            else if(!!this.model.defaultAnnotationContent) {
               this.tooltip = this.model.defaultAnnotationContent;
            }
            // Otherwise tooltip should be the text when it overflows the container
            else if(this.objectContentTd) {
               const size = this.getContentSize();
               this.updateClientSize();
               this.tooltip = this.model.text && (size.width < this._clientWidth ||
                  size.height < this._clientHeight || !Tool.isEquals(this.displayText, this.model.text))
                  ? this.model.text.replace(/&nbsp;/g, "") : "";
            }
         }
      }
      else {
         this.tooltip = "";
      }

      this.changeDetectionRef.detectChanges();
   }

   // optimization
   private updateClientSize() {
      const key = this.model.text + ":" + this.model.objectFormat.font;
      const isNoWrap = this.model.objectFormat.wrapping.whiteSpace == "nowrap";

      if(this._clientText != key && this.objectContentTd) {
         const element = this.objectContentTd.nativeElement;
         this._clientText = key;
         this._clientWidth = isNoWrap ? this.getNoWrapMaxWidth() : element.clientWidth;
         this._clientHeight = element.clientHeight;
      }
   }

   private getNoWrapMaxWidth(): number {
      let maxWidth: number = 0;
      let lines = this.model.text?.split("\n") || [];
      lines.forEach(l =>
         maxWidth = Math.max(GuiTool.measureText(l, this.model.objectFormat.font), maxWidth));
      return maxWidth;
   }

   private getHTMLText(): string {
      return GuiTool.getHTMLText(this.model.text);
   }

   ngAfterViewInit() {
      this.changeHeightOfTextarea();
   }

   ngAfterViewChecked() {
      this.modelChanged();
      this.textChanged();

      // check height on visibility change since height is returned as 0 when div is not visible
      if(this.textHeight == 0 || this.model.containerType == "VSTab") {
         if(this.changeHeightOfTextarea()) {
            this.changeDetectionRef.detectChanges();
         }
      }
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.pendingChange) {
         this.sendChangeEvent();
      }

      this.unsubscribe();

      if(!!this.messageListener) {
         this.document.defaultView.removeEventListener("message", this.messageListener);
      }
   }

   private unsubscribe(): void {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   onKeyDown(event: KeyboardEvent) {
      //Backspace
      if((event.keyCode === 8 || event.keyCode === 37 ||
         event.keyCode === 38 || event.keyCode === 39 ||
         event.keyCode === 40) && this.model.editing)
      {
         event.stopPropagation();
      }
   }

   getAutoTextModel() {
      let amodel = new AutoCompleteModel();
      amodel.parameters = this.model.parameters;
      amodel.text = this.model.text;
      amodel.shadow = this.model.shadow;
      amodel.overflow = this.createdByDblClick ? "hidden" : null;
      amodel.autoSize = this.model.autoSize;
      amodel.color = this.model.objectFormat.foreground;
      amodel.background = this.model.objectFormat.background;
      amodel.wordWrap = this.model.objectFormat.wrapping.wordWrap;
      amodel.textDecoration = this.model.objectFormat.decoration;
      amodel.height = this.textHeight;
      amodel.font = this.model.objectFormat.font;

      return amodel;
   }

   changeText(value: string) {
      this.model.text = value;

      if(!this.viewer) {
         if(this.updateOnChange) {
            this.debounceService.debounce(
               `ChangeVSObjectTextEvent.${this.model.absoluteName}`,
               () => this.sendChangeEvent(), 500, []);
         }
         else {
            this.pendingChange = true;
         }
      }
   }

   private sendChangeEvent() {
      this.pendingChange = false;
      const event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
         this.model.absoluteName, this.model.text);

      if(!this.editableLayout) {
         this.viewsheetClient.sendEvent("/events/composer/viewsheet/vsText/changeText", event);
      }
      else {
         this.viewsheetClient.sendEvent(
            "/events/composer/viewsheet/printLayout/vsText/changeText/" + this.layoutRegion, event);
      }
   }

   clicked(event: MouseEvent) {
      this.popComponentService.setPopLocation(this.model.popLocation);
      if(this.viewer && this.model.popComponent && !this.dataTipService.isDataTip(this.model.absoluteName)) {
         this.popComponentService.toggle(this.model.popComponent, event.clientX, event.clientY,
                                         this.model.popAlpha, this.model.absoluteName);
      }

      this.clickHyperlink(event);
      this.changeHeightOfTextarea();

      if(this.viewer) {
         let target = "/events/onclick/" + this.model.absoluteName + "/" + event.offsetX +
            "/" + event.offsetY;
         this.viewsheetClient.sendEvent(target);
      }
   }

   clickHyperlink(event: MouseEvent) {
      let links = this.model.hyperlinks;
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

      if(this._actions) {
         const clickActions = this._actions.clickAction;

         if(clickActions && event.button === 0) {
            if(!this.alwaysAllowClickPropagation) {
               event.stopPropagation();
            }
         }
      }
   }

   private changeHeightOfTextarea(): boolean {
      if(this.model.autoSize) {
         const oheight = this.textHeight;
         let borderHeight: number = 0;

         if(!!this.objectContainer) {
            const assemblyStyle: CSSStyleDeclaration =
               window.getComputedStyle(this.objectContainer.nativeElement);
            borderHeight = parseInt(assemblyStyle.borderBottomWidth, 10) +
               parseInt(assemblyStyle.borderTopWidth, 10);
         }

         if(!!this.objectContentTextarea) {
            this.textHeight = this.objectContentTextarea.nativeElement.scrollHeight + borderHeight;
            this.textHeightChanged.emit(this.textHeight);
         }
         else if(!!this.objectContentTd) {
            this.textHeight = this.objectContentTd.nativeElement.scrollHeight + borderHeight;
            this.textHeightChanged.emit(this.textHeight);
         }

         return oheight != this.textHeight;
      }

      return false;
   }

   changeCursor(): void {
      if(this.viewer && !!this.model.hyperlinks && this.model.hyperlinks.length > 0) {
         this.cursor = "pointer";
      }
   }

   private showAnnotateDialog(event: MouseEvent): void {
      this.richTextService.showAnnotationDialog((content) => {
         this.addAnnotation(content, event);
      }).subscribe(dialog => {
         dialog.initialContent = this.model.defaultAnnotationContent;
      });
   }

   get presenter(): string {
      return this.vsInfo.linkUri + "vs/text/presenter/" +
         encodeURIComponent(this.model.absoluteName) +
         "/" + this.model.objectFormat.width +
         "/" + this.model.objectFormat.height +
         "/" + this.editableLayout +
         "/" + this.layoutRegion +
         "/" + Tool.encodeURIPath(this.viewsheetClient.runtimeId) +
         "?" + this.model.genTime;
   }

   getContentSize(): {width: number, height: number} {
      let w: number = this.model.objectFormat.width;
      let h: number = this.model.autoSize ? this.textHeight : this.model.objectFormat.height;

      w -= Tool.getMarginSize(this.model.objectFormat.border.left);
      w -= Tool.getMarginSize(this.model.objectFormat.border.right);
      h -= Tool.getMarginSize(this.model.objectFormat.border.top);
      h -= Tool.getMarginSize(this.model.objectFormat.border.bottom);

      return {width: w, height: h};
   }

   /**
    * Force this object to go into select and edit mode.
    * @param command the command.
    */
   private processForceEditModeCommand(command: ForceEditModeCommand): void {
      this.createdByDblClick = true;
      this.model.editing = true;
      this.vsInfo.selectAssembly(this.model);

      if(!this.context.vsWizard) {
         this.model.objectFormat.wrapping.whiteSpace = "nowrap";
         this.model.text = "";
      }

      //if text assembly is create by dbclick in composer or insert action in wizard,
      // it should not support resize initially.
      this.model.interactionDisabled = true;
   }

   private processUpdateExternalUrlCommand(command: UpdateExternalUrlCommand): void {
      const urls = {};
      urls[command.name] = command.url;
      this.sendMessageToExternalFrame(this.createExternalUrlsMessage(urls));
   }

   private sendExternalUrls(): void {
      if(!!this.model?.url && !!this.model?.externalUrls) {
         this.sendMessageToExternalFrame(this.createExternalUrlsMessage(this.model.externalUrls));
      }
   }

   private sendMessageToExternalFrame(message: any): void {
      if(!!this.externalFrame && !!this.externalFrame.nativeElement &&
         !!this.externalFrame.nativeElement.contentWindow)
      {
         this.externalFrame.nativeElement.contentWindow.postMessage(message, this.getExternalFrameOrigin());
      }
   }

   private createExternalUrlsMessage(urls: {[name: string]: string}): ExternalUrlsMessage {
      const cookies = this.document.cookie || "";
      const token = parseCookieValue(cookies, "XSRF-TOKEN");
      const normalizedUrls = Object.assign({}, urls);

      for(const key in normalizedUrls) {
         if(normalizedUrls.hasOwnProperty(key)) {
            const url = normalizedUrls[key];

            if((typeof url === "string" && url.startsWith("../"))) {
               normalizedUrls[key] = GuiTool.resolveUrl(url);
            }
         }
      }

      return {
         type: "inetsoftExternalUrls",
         token: token,
         urls: normalizedUrls
      };
   }

   private getExternalFrameOrigin(): string {
      let domain = this.domSanitizer.sanitize(SecurityContext.RESOURCE_URL, this.safeUrlText);
      let index = domain.indexOf("//");

      if(index < 0) {
         // relative
         domain = GuiTool.resolveUrl(domain);
         index = domain.indexOf("//");
      }

      index = domain.indexOf("/", index + 2);

      if(index >= 0) {
         domain = domain.substring(0, index);
      }

      return domain;
   }

   onEnterDown(event: KeyboardEvent): void {
      if(event.keyCode !== 13) {
         return;
      }

      if(this.createdByDblClick) {
         event.preventDefault();
         this.sendChangeEvent();
         this.vsInfo.deselectAssembly(this.model);
         this.createdByDblClick = false;
         this.updateWidth.emit(this.model.objectFormat.width);
      }
   }

   get width(): number {
      if(this.createdByDblClick && this.objectContentTextarea) {
         this.model.objectFormat.width =
            Math.max(this.model.objectFormat.width,
               this.objectContentTextarea.nativeElement.scrollWidth);
      }

      return this.model.objectFormat.width;
   }

   isForceTab(): boolean {
      return this.contextProvider.composer;
   }

   private updateUrlText(urlText: string): void {
      const safeUrlText = this.domSanitizer.sanitize(SecurityContext.URL, urlText);
      this.safeUrlText = this.domSanitizer.bypassSecurityTrustResourceUrl(safeUrlText);
   }

   get whiteSpace(): string {
      const whiteSpace = this.model.objectFormat.wrapping.whiteSpace;

      // Bug #37752. The white-space default value is 'pre-wrap' in the textarea(chrome ff etc..).
      // So don't set it when the style value is the default format value,
      // so that the textarea applies the default value of browser.
      return whiteSpace == DEFAULT_WHITESPACE_FORMAT ? null : whiteSpace;
   }

   protected isPopupOrDataTipSource(): boolean {
      return this.popComponentService.isPopSource(this.model.absoluteName);
   }
}
