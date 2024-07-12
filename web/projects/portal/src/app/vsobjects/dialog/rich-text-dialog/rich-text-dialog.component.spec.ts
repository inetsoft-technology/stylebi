/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Observable, of as observableOf } from "rxjs";
import { FontService } from "../../../widget/services/font.service";
import { RichTextDialog } from "./rich-text-dialog.component";

const singleTemplate = `<rich-text-dialog
       *ngIf="exist"
       (onCommit)="onCommit($event)"
       (onCancel)="onCancel($event)">
     </rich-text-dialog>`;

const doubleTemplate = `<rich-text-dialog
       *ngIf="exist"
       (onCommit)="onCommit($event)"
       (onCancel)="onCancel($event)">
     </rich-text-dialog>
     <rich-text-dialog
       (onCommit)="onCommit($event)"
       (onCancel)="onCancel($event)">
     </rich-text-dialog>`;

@Component({
   selector: "test-app",
   template: ``
})
class TestApp {
   public exist: boolean = true;

   onCommit(message: string): void {
   }

   onCancel(message: string): void {
   }
}

describe("Rich Text Dialog Tests", () => {
   let fontService: any;
   const fontObservable: Observable<string[]> = observableOf([]);

   beforeEach(async(() => {
      fontService = { getAllFonts: jest.fn() };
      fontService.getAllFonts.mockImplementation(() => fontObservable);

      TestBed.configureTestingModule({
         declarations: [TestApp, RichTextDialog],
         providers: [{ provide: FontService, useValue: fontService}],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
   }));

   xit("should create a rich text dialog", () => {
      TestBed.overrideTemplate(TestApp, singleTemplate);
      let fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
      let dialogElement = fixture.debugElement.query(By.directive(RichTextDialog));
      expect(dialogElement).not.toBeNull();
      expect(dialogElement.nativeElement
         .querySelector(".mce-tinymce"))
         .not
         .toBeNull();
      });
   });

   xit("should only remove its own editor when destroyed", () => {
      TestBed.overrideTemplate(TestApp, doubleTemplate);
      let fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let dialogElements: DebugElement[] = fixture.debugElement
            .queryAll(By.directive(RichTextDialog));

         expect(dialogElements.length).toBe(2);

         dialogElements.forEach((dialog) => {
            expect(dialog.nativeElement
               .querySelector(".mce-tinymce"))
               .not
               .toBeNull();
         });

         fixture.componentInstance.exist = false;
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            dialogElements = fixture.debugElement.queryAll(By.directive(RichTextDialog));
            expect(dialogElements.length).toBe(1);
         });
      });
   });

   xit("should get the rich text from tinymce", () => { // broken test
      TestBed.overrideTemplate(TestApp, doubleTemplate);
      let fixture = TestBed.createComponent(TestApp);
      let onCommitSpy = jest.spyOn(fixture.componentInstance, "onCommit");
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let dialogElement = fixture.debugElement.query(By.directive(RichTextDialog));
         let elementId = dialogElement.componentInstance.elementId;

         const testText = "<p>test content</p>";
         // let editor: Editor = TinyMce.Manager.get(elementId);
         // editor.setContent(testText);
         //
         // let okBtn = dialogElement.queryAll(By.css("button.btn"))
         //    .filter((element) => element.nativeElement.textContent === "OK")[0];
         //
         // okBtn.triggerEventHandler("click", null);
         // expect(onCommitSpy).toHaveBeenCalledWith(testText);
      });
   });
});
