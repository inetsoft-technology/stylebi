/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.service.VSObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.InvalidPathException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class ImagePreviewPaneService {

   public ImagePreviewPaneService(ViewsheetService viewsheetService,
                                  VSObjectService vsObjectService)
   {
      this.viewsheetService = viewsheetService;
      this.vsObjectService = vsObjectService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BinaryTransfer getImagePreviewImageByteBuffer(@ClusterProxyKey String runtimeId, String name,
                                                Principal principal) throws Exception  {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      String key = "/" + ImagePreviewPaneService.class.getName() + "_" + runtimeId + "_" + name;
      BinaryTransfer dataTransfer = new BinaryTransfer(key);
      byte[] data = VSUtil.getVSImageBytes(null, name, vs, -1, -1, null, new VSPortalHelper());
      dataTransfer.setData(data);
      return dataTransfer;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel uploadImage(@ClusterProxyKey String runtimeId, String mpfName,
                                    BinaryTransfer dataTransfer, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      boolean uploaded = false;
      byte[] mpfBytes = dataTransfer.getData();

      try {
         if(!vsObjectService.isImage(mpfBytes)) {
            throw new MessageException(Catalog.getCatalog().getString("composer.uploadImageFailed"));
         }

         vs.addUploadedImage(mpfName, mpfBytes);
         uploaded = true;
      }
      catch(MessageException messageException) {
         throw messageException;
      }
      catch(Exception ex) {
         LoggerFactory.getLogger(ImagePreviewPaneController.class).debug("Failed to get uploaded file data", ex);
      }

      if(uploaded) {
         return getImageTree(rvs);
      }
      else {
         return null;
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean deleteImage(@ClusterProxyKey String runtimeId, String name,
                              Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return false;
      }

      vs.removeUploadedImage(name);
      return true;
   }

   /**
    * Get Tree model for images, mimic of GetImageTreeModelEvent
    * @param rvs  The runtime viewsheet
    * @return the image tree model
    */
   public TreeNodeModel getImageTree(RuntimeViewsheet rvs) {
      Viewsheet viewsheet = rvs.getViewsheet();
      Catalog catalog = Catalog.getCatalog();

      if(viewsheet == null) {
         return TreeNodeModel.builder().build();
      }

      List<TreeNodeModel> children = new ArrayList<>();
      children.add(TreeNodeModel.builder()
                      .label(catalog.getString("Current Image"))
                      .data("_CURRENT_IMAGE_")
                      .type("current")
                      .icon("image-icon")
                      .leaf(true)
                      .build());

      final String imageDir = SreeEnv.getProperty("html.image.directory");

      if(!Tool.isEmptyString(imageDir)) {
         try {
            FileSystemService fileSystemService = FileSystemService.getInstance();
            File imageFile = fileSystemService.getFile(imageDir);
            String imgPath = fileSystemService.getPath(imageDir).toString();

            if(!imageFile.isAbsolute()) {
               final String homeDir = SreeEnv.getProperty("sree.home");
               imgPath = fileSystemService.getPath(homeDir, imageDir).toString();
            }

            getImagesFromDir(children, imgPath, "");
         }
         catch(InvalidPathException ipe) {
            LOG.error(
               "Failed to get Image tree from image directory, the image path is invalid.", ipe);
         }
      }

      List<TreeNodeModel> skinNodes = new ArrayList<>();
      skinNodes.add(TreeNodeModel.builder()
                       .label(catalog.getString("Title"))
                       .data(Tool.escape(ImageVSAssemblyInfo.SKIN_TITLE.split("&")[0]))
                       .leaf(true)
                       .type(ImageVSAssemblyInfo.SKIN_IMAGE)
                       .build());
      skinNodes.add(TreeNodeModel.builder()
                       .label(catalog.getString("Theme_background"))
                       .data(Tool.escape(ImageVSAssemblyInfo.SKIN_BACKGROUND.split("&")[0]))
                       .leaf(true)
                       .type(ImageVSAssemblyInfo.SKIN_IMAGE)
                       .build());
      skinNodes.add(TreeNodeModel.builder()
                       .label(catalog.getString("Background1"))
                       .data(Tool.escape(ImageVSAssemblyInfo.SKIN_NEUTER1.split("&")[0]))
                       .leaf(true)
                       .type(ImageVSAssemblyInfo.SKIN_IMAGE)
                       .build());
      skinNodes.add(TreeNodeModel.builder()
                       .label(catalog.getString("Background2"))
                       .data(Tool.escape(ImageVSAssemblyInfo.SKIN_NEUTER2.split("&")[0]))
                       .leaf(true)
                       .type(ImageVSAssemblyInfo.SKIN_IMAGE)
                       .build());
      skinNodes.add(TreeNodeModel.builder()
                       .label(catalog.getString("Background3"))
                       .data(Tool.escape(ImageVSAssemblyInfo.SKIN_NEUTER3.split("&")[0]))
                       .leaf(true)
                       .type(ImageVSAssemblyInfo.SKIN_IMAGE)
                       .build());

      children.add(TreeNodeModel.builder()
                      .label(catalog.getString("Skin"))
                      .data("Skin")
                      .leaf(false)
                      .children(skinNodes)
                      .build());

      List<TreeNodeModel> uploaded = Arrays.stream(viewsheet.getUploadedImageNames())
         .map(u -> TreeNodeModel.builder()
            .label(u)
            .data(u)
            .leaf(true)
            .type(ImageVSAssemblyInfo.UPLOADED_IMAGE)
            .build())
         .collect(Collectors.toList());

      if(!uploaded.isEmpty()) {
         children.add(TreeNodeModel.builder()
                         .label(catalog.getString("Uploaded"))
                         .data("Uploaded")
                         .leaf(false)
                         .children(uploaded)
                         .build());
      }

      return TreeNodeModel.builder().children(children).build();
   }

   private void getImagesFromDir(List<TreeNodeModel> nodes, String root, String prefix) {
      final List<TreeNodeModel> children = new ArrayList<>();
      final DataSpace dataspace = DataSpace.getDataSpace();
      final FileSystemService fileSystemService = FileSystemService.getInstance();
      final String currentPath = dataspace.getPath(fileSystemService.getPath(
         root, prefix).toString(), "");
      final String[] fileNames = dataspace.list(currentPath);
      assert fileNames != null;

      Arrays.sort(fileNames, (file1, file2) -> {
         final String path1 = dataspace.getPath(currentPath, file1);
         final String path2 = dataspace.getPath(currentPath, file2);

         if(dataspace.isDirectory(path1)) {
            return dataspace.isDirectory(path2) ? path1.compareTo(path2) : -1;
         }
         else {
            return dataspace.isDirectory(path2) ? 1 : 0;
         }
      });

      for(String fileName : fileNames) {
         final String fullPath = dataspace.getPath(currentPath, fileName);
         final String relativePath = fileSystemService.getPath(prefix, fileName).toString();

         if(dataspace.isDirectory(fullPath) && checkImageExisted(dataspace, fullPath)) {
            getImagesFromDir(children, root, relativePath);
            continue;
         }

         final String lowerCaseName = fileName.toLowerCase();

         if(lowerCaseName.endsWith(".gif") || lowerCaseName.endsWith(".jpg") ||
            lowerCaseName.endsWith(".png") || lowerCaseName.endsWith(".svg") ||
            lowerCaseName.endsWith(".tif"))
         {
            children.add(TreeNodeModel.builder()
                            .label(fileName)
                            .data(relativePath)
                            .icon("image-icon")
                            .leaf(true)
                            .type(ImageVSAssemblyInfo.SERVER_IMAGE)
                            .build());
         }
      }

      final String folderName = fileSystemService.getPath(currentPath).getFileName().toString();
      nodes.add(TreeNodeModel.builder()
                   .label(folderName)
                   .data(folderName)
                   .leaf(false)
                   .children(children)
                   .build());
   }

   /**
    * Check if there is an image in the directory, if not existed, the folder
    * should not be displayed in the tree.
    */
   private boolean checkImageExisted(DataSpace dataSpace, String dirPath) {
      final String[] fileNames = dataSpace.list(dirPath);

      if(fileNames == null) {
         return false;
      }

      for(final String fileName : fileNames) {
         final String lowerCase = fileName.toLowerCase();

         if(lowerCase.endsWith(".gif") || lowerCase.endsWith(".jpg") ||
            lowerCase.endsWith(".png") || lowerCase.endsWith(".svg")) {
            return true;
         }
      }

      for(final String fileName : fileNames) {
         final String filePath = dataSpace.getPath(dirPath, fileName);

         if(dataSpace.isDirectory(filePath) && checkImageExisted(dataSpace, filePath)) {
            return true;
         }
      }

      return false;
   }

   private ViewsheetService viewsheetService;
   private VSObjectService vsObjectService;


   private static final Logger LOG =
      LoggerFactory.getLogger(ImagePreviewPaneService.class);
}
