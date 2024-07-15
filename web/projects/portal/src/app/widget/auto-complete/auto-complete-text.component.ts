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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   Renderer2,
   TemplateRef,
   ViewChild,
   ViewEncapsulation
} from "@angular/core";
import { AutoCompleteModel } from "./auto-complete-model";
import { Point } from "../../common/data/point";
import { Dimension } from "../../common/data/dimension";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { DropdownRef } from "../fixed-dropdown/fixed-dropdown-ref";

@Component({
   selector: "auto-complete-text",
   templateUrl: "auto-complete-text.component.html",
   styleUrls: ["auto-complete-text.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class AutoCompleteText implements OnInit {
   @Input() adhoc: boolean = false;
   @Input() model: AutoCompleteModel;
   @Output() commitText = new EventEmitter<string>();
   @ViewChild("textArea") textArea: ElementRef;
   @ViewChild("autoComplete") autoCompleteTemp: TemplateRef<any>;
   downInAutoList: boolean = false;
   row: number = 0;
   col: number = 0;
   cursorPos: Point = new Point(0, 0);
   cursorString: string = null;
   filterParameters: string[] = [];
   selIndex: number = 0;
   // auto complete is visible. when press up/down/enter, it always show.
   autoCompleteShow: boolean = false;
   // auto complete is valid. when press . after parameter, it is valid, if click auto list to
   // select one or enter on auto list, it will finished and will not show.
   autoCompleteValid: boolean = false;
   focusNode: Node = null;
   focusOffset: number = 0;
   focusIn: boolean = true;
   private autoCompleteRef: DropdownRef;
   private valueList: Element;
   private autoComplete: Element;

   constructor(private renderer: Renderer2, private dropdownService: FixedDropdownService) {
   }

   ngOnInit(): void {
   }

   initCursorPosition() {
      let selection = window.getSelection();
      let elem = this.textArea.nativeElement;

      for(let i = 0; i < elem.childNodes.length; i++) {
         if(elem.childNodes[i] === selection.anchorNode ||
            elem.childNodes[i].firstChild === selection.anchorNode) {
            this.cursorPos.y = i;
            this.cursorPos.x = selection.anchorOffset;
            this.cursorString = elem.childNodes[i].textContent.substring(0, selection.anchorOffset);
            break;
         }
      }
   }

   private getTextWdith(): number {
      return this.getTextSize().width;
   }

   private getTextHeight(): number {
      return this.getTextSize().height;
   }

   private getTextSize(): Dimension {
      let result = new Dimension(0, 0);
      let selObj = window.getSelection();
      let selRange = selObj.getRangeAt(0);
      // Get cursor position and show auto complete on the left-bottom corner of cursor.
      // The cursor position is the selection position reduce parent position.
      result.width = selRange.getBoundingClientRect().left;
      result.height = selRange.getBoundingClientRect().top + 15;

      return result;
   }

   editorKeyDown(evt: KeyboardEvent) {
      evt.stopPropagation();

      // If up/down when auto complete show, change selected item in auto list. And when enter apply
      // the selected list to text.
      if(this.autoCompleteShow && ("ArrowUp" == evt.key || "ArrowDown" == evt.key ||
         "Enter" == evt.key))
      {
         evt.preventDefault();

         if("ArrowUp" == evt.key && this.selIndex > 0) {
            this.selIndex--;
            this.scrollToSelectedItem();
         }

         if("ArrowDown" == evt.key && this.selIndex < this.filterParameters.length - 1) {
            this.selIndex++;
            this.scrollToSelectedItem();
         }

         if("Enter" == evt.key) {
            this.updateParameter(this.filterParameters[this.selIndex]);
            this.hideAutoList();
         }

         return;
      }

      if(evt.key == ".") {
         this.autoCompleteValid = true;
      }
   }

   // Using scrollIntoView will scroll not only auto complete list but also the vs element, so do
   // not using it, we implement simple by scroll to select item to the first item.
   scrollToSelectedItem() {
      if(this.selIndex >= 0 && this.selIndex < this.valueList?.children.length && this.autoComplete)
      {
         let height = this.valueList.children[0].clientHeight;
         this.autoComplete.scrollTop = this.selIndex * height;
      }
   }

   editorKeyUp(evt: KeyboardEvent) {
      evt.stopPropagation();

      // If show auto complete and input enter, should apply parameter to text not wrap text.
      if(this.autoCompleteShow && ("ArrowUp" == evt.key || "ArrowDown" == evt.key ||
         "Enter" == evt.key))
      {
         evt.preventDefault();
      }
   }

   input() {
      this.focusIn = true;
      this.initCursorPosition();

      if(this.autoCompleteValid && this.showAutoComplete()) {
         this.focusNode = window.getSelection().focusNode;
         this.focusOffset = window.getSelection().focusOffset;
         this.selIndex = 0;
         this.showAutoList();
         this.scrollToSelectedItem();
      }
      else {
         this.downInAutoList = false;
         this.hideAutoList();
      }
   }

   updateParameter(param: string) {
      this.autoCompleteValid = false;

      if(param != null) {
         let ostring = this.textArea.nativeElement.childNodes[this.cursorPos.y].textContent;
         let node = this.focusNode;
         let offset = this.focusOffset;

         if(ostring.endsWith("parameter.")) {
            node.textContent += param;
            this.cursorPos.x += param.length;
            this.resetCursor(node, offset, param.length);
         }
         else if(ostring.indexOf("parameter.") >= 0) {
            let idx = ostring.lastIndexOf("parameter.");
            let nstring = ostring.substring(0, idx + 10);

            if(node.textContent != nstring + param) {
               let olen = node.textContent.length;
               node.textContent = nstring + param;
               this.cursorPos.x -= ostring.length - idx - 10;
               this.cursorPos.x += param.length;
               this.resetCursor(node, offset, (nstring + param).length - olen);
            }
         }
      }
   }

   showAutoComplete(): boolean {
      if(this.cursorString == null || this.cursorString.indexOf("parameter.") == -1) {
         return false;
      }

      // if cursor strings ends with parameter.  should show auto complete to select.
      if(this.cursorString.endsWith("parameter.")) {
         this.filterParameters = this.model.parameters;
         return true;
      }

      let idx = this.cursorString.lastIndexOf("parameter.");
      let pname = this.cursorString.substring(this.cursorString.lastIndexOf("parameter.") + 10);

      // If input some parameter match with model.parameters, should filter the parameters in list.
      if(this.matchSomeParameter(pname)) {
         return true;
      }

      return false;
   }

   matchSomeParameter(pname: string): boolean {
      this.filterParameters = [];

      for(let i = 0; i < this.model.parameters.length; i++) {
         let param = this.model.parameters[i];

         if(param.startsWith(pname)) {
            this.filterParameters.push(param);
         }
      }

      return this.filterParameters.length > 0;
   }

   mouseClick(evt: MouseEvent) {
      this.downInAutoList = false;
      evt.stopPropagation();
      evt.stopImmediatePropagation();
      evt.preventDefault();
      this.hideAutoList();
   }

   paramMouseClick(evt: MouseEvent) {
      this.downInAutoList = false;
      evt.stopPropagation();
      evt.stopImmediatePropagation();
      evt.preventDefault();
      let param = (evt.target as HTMLElement).innerText;
      this.updateParameter(param);
      this.hideAutoList();
   }

   resetCursor(node: any, offset: number, len: number) {
      let elem = this.textArea.nativeElement;
      let range = document.createRange();
      elem.focus();
      offset = offset + len;
      range.setStart(node, offset);
      range.setEnd(node, offset);
      let selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      elem.focus();
   }

   showAutoList() {
      let options: DropdownOptions = {
         position: {x: this.getTextWdith(), y: this.getTextHeight()},
      };

      if(this.autoCompleteRef) {
         this.autoCompleteRef.close();
      }

      this.autoCompleteRef = this.dropdownService.open(this.autoCompleteTemp, options);
      this.findAutoCompleteElement();
      this.autoCompleteShow = true;
   }

   private findAutoCompleteElement() {
      if(this.autoCompleteRef && this.autoCompleteRef.viewRef.rootNodes) {
         let rootNodes = this.autoCompleteRef.viewRef.rootNodes;

         for(let node of rootNodes) {
            if(!node) {
               continue;
            }

            if(node.className === "auto-complete-pane") {
               this.autoComplete = node;
            }
         }

         if(this.autoComplete) {
            this.valueList = this.autoComplete.getElementsByClassName("autoList")?.item(0);
         }
      }
   }

   hideAutoList() {
      if(this.autoCompleteRef) {
         this.autoCompleteRef.close();
         this.autoCompleteRef = null;
      }

      this.autoCompleteShow = false;
   }

   mouseDownUp(evt: MouseEvent) {
      evt.stopPropagation();
      evt.stopImmediatePropagation();
   }

   textClick(evt: MouseEvent) {
      evt.stopPropagation();
      evt.stopImmediatePropagation();
      this.hideAutoList();
   }

   changeText(evt: FocusEvent) {
      if(this.downInAutoList) {
         return;
      }

      this.hideAutoList();
      this.focusIn = false;
      evt.stopPropagation();
      this.downInAutoList = false;
      this.model.text = (evt.target as any).innerText;
      this.commitText.emit(this.model.text);
   }
}
