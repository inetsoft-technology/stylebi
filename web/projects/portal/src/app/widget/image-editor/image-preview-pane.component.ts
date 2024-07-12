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
import { HttpClient } from "@angular/common/http";
import {
   AfterViewInit,
   ChangeDetectorRef,
   ElementRef,
   Component,
   Input,
   OnInit,
   ViewChild
} from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { getImageName, getImageType } from "../../composer/util/image-util";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { ImagePreviewPaneModel } from "./image-preview-pane-model";
import { ComponentTool } from "../../common/util/component-tool";
import { NotificationsComponent } from "../notifications/notifications.component";

declare const window: any;

@Component({
   selector: "image-preview-pane",
   templateUrl: "image-preview-pane.component.html",
})
export class ImagePreviewPane implements OnInit, AfterViewInit {
   @Input() model: ImagePreviewPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() runtimeId: string;
   @Input() animateGif: boolean = true;
   @Input() layoutObject: boolean = false;
   @ViewChild(TreeComponent, {static: true}) tree: TreeComponent;
   currentNode: TreeNodeModel = <TreeNodeModel>{};
   selectedImageNode: TreeNodeModel = <TreeNodeModel>{};
   private selectedImageNodeForTree: TreeNodeModel = <TreeNodeModel>{}; //for correct display in selecting image pane
   currentTime: number = 0;
   controller: string = "../composer/vs/image-preview-pane/";
   alphaInvalid: boolean = false;
   blackBackground: boolean;
   @ViewChild("uploadInput") uploadInput: ElementRef;
   @ViewChild("notifications") notifications: NotificationsComponent;

   constructor(private http: HttpClient, private changeDetectorRef: ChangeDetectorRef,
               private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initCurrentNode();
   }

   ngAfterViewInit(): void {
      if(this.tree && this.tree.root && this.tree.root.children &&
         this.tree.root.children.length > 0) {
         this.tree.selectAndExpandToNode(this.tree.root.children[0]);
      }

      this.tree.expandToNode(this.selectedImageNode);
      this.changeDetectorRef.detectChanges();
   }

   public initCurrentNode(): void {
      if(this.model.selectedImage) {
         let imageName: string = getImageName(this.model.selectedImage);
         let result = this.tree.getNodeByData("data", imageName, this.model.imageTree);

         if(result) {
            this.currentNode = result;
         }
         else if(this.model.selectedImage.startsWith("$") ||
            this.model.selectedImage.startsWith("=")) {
            this.currentNode.label = "Dynamic Image";
            this.currentNode.data = this.model.selectedImage;
         }
      }

      this.selectedImageNode = this.currentNode;
      let currentImageNode: TreeNodeModel = this.tree.getNodeByData("data", "_CURRENT_IMAGE_", this.model.imageTree);
      this.selectedImageNodeForTree = currentImageNode ? currentImageNode : this.selectedImageNode;
      this.currentTime = performance.now();
   }

   public selectImage(image: TreeNodeModel): void {
      this.selectedImageNodeForTree = image;

      if(!image || !image.leaf) {
         return;
      }

      if(image.type == "current") {
         this.selectedImageNode = this.currentNode;
      }
      else {
         this.selectedImageNode = image;
      }

      if(!!this.selectedImageNode.data) {
         this.model.selectedImage = getImageType(this.selectedImageNode.type) +
            this.selectedImageNode.data;
      }
      else {
         this.model.selectedImage = null;
      }

      this.currentTime = performance.now();
   }

   public get imageSrc(): string {
      if(this.selectedImageNode && this.selectedImageNode.data && this.selectedImageNode.type) {
         return this.controller + "image/" + Tool.byteEncode(this.selectedImageNode.data)
            + "/" + this.selectedImageNode.type + "/" + Tool.byteEncode(this.runtimeId)
            + "?" + this.currentTime;
      }
      else {
         return "assets/emptyimage.gif";
      }
   }

   public fileChanged(event: any) {
      let fileList: FileList = event.target.files;

      if(fileList.length > 0) {
         let file: File = fileList[0];
         let formData: FormData = new FormData();
         formData.append("file", file);
         this.http.post<TreeNodeModel>(this.controller + "upload/" +
            Tool.byteEncode(this.runtimeId), formData)
            .subscribe(
               (data: TreeNodeModel) => {
                  if(data) {
                     this.model.imageTree = data;
                     let uploadedNode: TreeNodeModel;

                     if(this.tree) {
                        this.tree.root = this.model.imageTree;
                        uploadedNode = this.tree.getNodeByData("data", file.name);
                        this.tree.selectAndExpandToNode(uploadedNode);
                     }

                     this.selectImage(uploadedNode);
                  }
                  else {
                     if(this.notifications) {
                        this.notifications.danger("_#(js:composer.uploadImageFailed)");
                     }
                  }
               },
               (err: any) => {
                  if(this.notifications) {
                     this.notifications.danger("_#(js:composer.uploadImageFailed)");
                  }
               }
            );
      }
   }

   public deleteUpload() {
      const message = "_#(js:composer.deleteImageOrNot)";
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {
            if(buttonClicked === "yes") {
               this.http.delete<boolean>(this.controller + "delete/" +
                  Tool.byteEncode(this.selectedImageNode.data) + "/" +
                  Tool.byteEncode(this.runtimeId))
                  .subscribe(
                     (data: boolean) => {
                        if(data) {
                           if(this.currentNode == this.selectedImageNode) {
                              this.currentNode = <TreeNodeModel>{};
                           }

                           this.model.selectedImage = !!this.currentNode &&
                           !!this.currentNode.data ? getImageType(this.currentNode.type) +
                              this.currentNode.data : null;

                           for(let node of this.model.imageTree.children) {
                              if(node.data == "Uploaded") {
                                 let index = node.children.indexOf(this.selectedImageNode);

                                 if(index > -1) {
                                    node.children.splice(index, 1);
                                 }

                                 break;
                              }
                           }

                           this.selectedImageNode = <TreeNodeModel>{};
                           const currentImageNode =
                              this.tree.getNodeByData("data", "_CURRENT_IMAGE_",
                                 this.model.imageTree);
                           this.selectedImageNodeForTree = currentImageNode ?
                              currentImageNode : this.selectedImageNode;
                        }
                        else {
                           //TODO delete failed , growl message?
                        }
                     },
                     (err: any) => {
                        // TODO handle error
                     }
                  );
            }
         });
   }

   clearSelected(): void {
      this.selectedImageNode = {};
      this.selectedImageNodeForTree = {};
      this.model.selectedImage = null;
      this.model.animateGifImage = false;
   }

   public getAlpha(): number {
      if(this.model.alpha != null) {
         let a: number = Math.floor(this.model.alpha);

         if(a > 100) {
            return 1;
         }
         else if(a < 0) {
            return 0;
         }

         return a / 100;
      }
      else {
         return 1;
      }
   }

   public changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   get selectedNodes(): TreeNodeModel[] {
      return this.selectedImageNodeForTree ? [this.selectedImageNodeForTree] : [];
   }

   openUpload() {
      this.uploadInput.nativeElement.value = "";
      this.uploadInput.nativeElement.click();
   }
}
