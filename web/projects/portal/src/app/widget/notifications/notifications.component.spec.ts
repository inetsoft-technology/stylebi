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
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TestBed, async, ComponentFixture } from "@angular/core/testing";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { NotificationsComponent } from "./notifications.component";

describe("NotificationsComponent Integration Tests", () => {
   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            // NoopAnimationsModule,
            NgbModule
         ],
         declarations: [
            NotificationsComponent
         ]
      });
      TestBed.compileComponents();
   }));


   function testAlert(type: string, message: string) {
      let fixture: ComponentFixture<NotificationsComponent> =
         TestBed.createComponent(NotificationsComponent);
      fixture.componentInstance[type](message);
      fixture.detectChanges();
      let alertElement: Element =
         fixture.nativeElement.querySelector("ngb-alert>div.alert-" + type);
      expect(alertElement).toBeTruthy();
      let alertText: string = "";
      let child: Node = alertElement.firstChild;

      while(child) {
         if(child.nodeType === 3) { // Node.TEXT_NODE
            alertText += child.nodeValue;
         }

         child = child.nextSibling;
      }

      alertText = alertText.replace(/^\s*(.+)\s*$/, "$1");
      expect(alertText).toEqual(message);
   }

   xit("should create success alert", () => { // broken
      testAlert("success", "This is a success message");
   });

   xit("should create info alert", () => { // broken
      testAlert("info", "This is a info message");
   });

   xit("should create warning alert", () => { // broken
      testAlert("warning", "This is a warning message");
   });

   xit("should create danger alert", () => { // broken
      testAlert("danger", "This is a danger message");
   });
});
