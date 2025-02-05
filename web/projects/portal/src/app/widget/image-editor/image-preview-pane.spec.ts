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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TreeComponent } from "../tree/tree.component";
import { ImagePreviewPaneModel } from "./image-preview-pane-model";
import { ImagePreviewPane } from "./image-preview-pane.component";

let createModel: () => ImagePreviewPaneModel = () => {
   return {
      selectedImage: "",
      animateGifImage: false,
      scaleImage: false,
      alpha: 100,
      allowNullImage: true,
      imageTree: {
         children: []
      },
      presenter: false
   };
};

let createUploadImgModel: () => ImagePreviewPaneModel = () => {
   return {
      selectedImage: "^UPLOADED^test.png",
      animateGifImage: false,
      scaleImage: false,
      alpha: 100,
      allowNullImage: true,
      imageTree: {
         expanded: true,
         leaf: false,
         children: [{
            data: "_CURRENT_IMAGE_",
            expanded: false,
            leaf: true,
            type: "current",
            children: []
         },
         {
            data: "Uploaded",
            expanded: true,
            leaf: false,
            label: "Uploaded",
            children: [{
               data: "test.png",
               label: "test.png",
               expanded: false,
               leaf: true,
               type: "^UPLOADED^",
               children: []
            }]
         }]
      },
      presenter: false
   };
};

describe("Image Preview Pane Test", () => {
   let fixture: ComponentFixture<ImagePreviewPane>;
   let imagePreviewPane: ImagePreviewPane;
   let changeDetectorRef: any;
   let modalService: any;
   let httpClient: HttpClient;
   let httpTestingController: HttpTestingController;

   beforeEach(async(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      modalService = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientTestingModule
         ],
         declarations: [
            ImagePreviewPane, TreeComponent
         ],
         providers: [
            { provide: NgbModal, useValue: modalService },
            { provide: ChangeDetectorRef, useValue: changeDetectorRef }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      httpClient = TestBed.inject(HttpClient);
      httpTestingController = TestBed.inject(HttpTestingController);
   }));

   // Bug 10286 10451 make sure http requests go to correct url.
   it("should send http request to correct url", () => {
      imagePreviewPane = new ImagePreviewPane(httpClient, changeDetectorRef,
         modalService);
      imagePreviewPane.model = createModel();
      imagePreviewPane.runtimeId = "Viewsheet1";

      imagePreviewPane.fileChanged({
         target: {
            files: [new File([], "file1")]
      }});

      const requests = httpTestingController.match((req) => {
         return req.url === "../api/composer/vs/image-preview-pane/upload/Viewsheet1";
      });
      expect(requests.length).toBe(1);
      requests.forEach(req => req.flush({}));
   });

   //bug #19410 should apply alpha in preview
   //Bug #21185 Image Alpha display error when the Alpha combobox select 0%
   it("should apply alpha in preview", () => {
      fixture = TestBed.createComponent(ImagePreviewPane);
      imagePreviewPane = <ImagePreviewPane>fixture.componentInstance;
      imagePreviewPane.model = createModel();
      imagePreviewPane.model.selectedImage = "test1";
      imagePreviewPane.model.alpha = 30;
      fixture.detectChanges();

      let previewImg = fixture.nativeElement.querySelector("div.bordered-box img");
      expect(previewImg.style["opacity"]).toBe("0.3");

      imagePreviewPane.model.alpha = 0;
      fixture.detectChanges();

      previewImg = fixture.nativeElement.querySelector("div.bordered-box img");
      expect(previewImg.style["opacity"]).toBe("0");
   });

   //Bug #19250 should disable Animate GIF Image in print layout
   it("should disable Animate GIF Image in print layout", (done) => {
      fixture = TestBed.createComponent(ImagePreviewPane);
      imagePreviewPane = <ImagePreviewPane>fixture.componentInstance;
      imagePreviewPane.layoutObject = true;
      imagePreviewPane.model = createModel();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let animateGif = fixture.nativeElement.querySelector("input.form-check-input");
         expect(animateGif.hasAttribute("disabled")).toBeTruthy();

         done();
      });
   });

   //Bug #19557 should clear Animate GIF Image after click clear
   //Bug #19558 clear should work
   it("check clear button function", (done) => {
      fixture = TestBed.createComponent(ImagePreviewPane);
      imagePreviewPane = <ImagePreviewPane>fixture.componentInstance;
      imagePreviewPane.model = createUploadImgModel();
      imagePreviewPane.model.animateGifImage = true;
      imagePreviewPane.selectedImageNode = {
         data: "test.png",
         label: "test.png",
         expanded: false,
         leaf: true,
         type: "^UPLOADED^",
         children: []
      };
      fixture.detectChanges();

      let clearBtn = fixture.nativeElement.querySelector("button.clear_button_id");
      let animateGif = fixture.nativeElement.querySelector("input.form-check-input");
      let selectedImg = fixture.nativeElement.querySelector("img");

      expect(animateGif.getAttribute("ng-reflect-model")).toBe("true");
      expect(selectedImg.getAttribute("src")).toBe(
         "../api/image/composer/vs/image-preview-pane/image/test~_2e_~png/^UPLOADED^/?" + imagePreviewPane.currentTime);

      clearBtn.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(animateGif.getAttribute("ng-reflect-model")).toBe("false");
         expect(selectedImg.getAttribute("src")).toBe("assets/emptyimage.gif");

         done();
      });
   });
});