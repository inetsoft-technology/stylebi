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
package inetsoft.web.viewsheet.service;

import inetsoft.report.LibManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.StyleTreeModel;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
public class VSDialogService {
   public VSDialogService() {
   }

   public Point getAssemblyPosition(VSAssemblyInfo info, Viewsheet viewsheet) {
      Point pos = info.getLayoutPosition();
      return pos != null ? pos : viewsheet.getPixelPosition(info);
   }

   public Dimension getAssemblySize(VSAssemblyInfo info, Viewsheet viewsheet) {
      Dimension size = info.getLayoutSize();
      return (size != null && size.width > 0 && size.height > 0) ?
         size : viewsheet.getPixelSize(info);
   }

   public void setAssemblyPosition(VSAssemblyInfo info, SizePositionPaneModel model) {
      int left = model.getLeft();
      int top = model.getTop();

      if(left >= 0 && top >= 0) {
         Point pos = new Point(left, top);

         if(info.getLayoutPosition() != null) {
            info.setLayoutPosition(pos);
         }

         info.setPixelOffset(pos);
      }
   }

   public void setContainerPosition(ContainerVSAssemblyInfo containerInfo,
                                    SizePositionPaneModel model, String[] children, Viewsheet vs) {
      int left = model.getLeft();
      int top = model.getTop();
      int height = model.getHeight();

      if(left >= 0 && top >= 0) {
         Point originalPosition = containerInfo.getLayoutPosition() != null ?
            containerInfo.getLayoutPosition() :
            vs.getPixelPosition(containerInfo);

         int xchange = left - originalPosition.x;
         int ychange = top - originalPosition.y;

         if(containerInfo instanceof TabVSAssemblyInfo) {
            Dimension originalSize = containerInfo.getLayoutSize() != null ?
               containerInfo.getLayoutSize() : vs.getPixelSize(containerInfo);
            ychange += height - originalSize.height;
         }

         setAssemblyPosition(containerInfo, model);

         for(String child : children) {
            Assembly childAssembly = vs.getAssembly(child);

            if(childAssembly == null) {
               continue;
            }

            VSAssemblyInfo childInfo = ((VSAssembly) childAssembly).getVSAssemblyInfo();

            if(childInfo.getLayoutPosition() != null) {
               childInfo.getLayoutPosition().translate(xchange, ychange);
            }

            childInfo.getPixelOffset().translate(xchange, ychange);
         }
      }
   }

   public void setAssemblySize(VSAssemblyInfo info, SizePositionPaneModel model) {
      setAssemblySize(info, model.getWidth(), model.getHeight());
   }

   public void setAssemblySize(VSAssemblyInfo info, int width, int height) {
      if(width > 0 && height > 0) {
         Dimension size = new Dimension(width, height);

         if(info.getLayoutSize() != null) {
            info.setLayoutSize(size);
         }

         info.setPixelSize(size);
      }
   }

   public void setContainerSize(ContainerVSAssemblyInfo info, SizePositionPaneModel model,
                                String[] children, Viewsheet viewsheet) {
      int width = model.getWidth();
      int height = model.getHeight();
      setAssemblySize(info, width, height);

      for(String child : children) {
         Assembly childAssembly = viewsheet.getAssembly(child);

         if(childAssembly == null) {
            continue;
         }

         VSAssemblyInfo childInfo = ((VSAssembly) childAssembly).getVSAssemblyInfo();
         Dimension childSize = childInfo.getLayoutSize() != null ?
            childInfo.getLayoutSize() : viewsheet.getPixelSize(childInfo);
         setAssemblySize(childInfo, width, childSize.height);
      }
   }

   /**
    * Get Tree model for images, mimic of GetTableStyleEvent
    *
    * @param rvs The runtime viewsheet
    *
    * @param freehand
    * @return the image tree model
    */
   public TreeNodeModel getStyleTree(RuntimeViewsheet rvs, Principal principal, boolean freehand) {
      Viewsheet viewsheet = rvs.getViewsheet();
      Catalog catalog = Catalog.getCatalog();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      LibManager mgr = LibManager.getManager();

      if(viewsheet == null) {
         return TreeNodeModel.builder().build();
      }

      TreeNodeModel root = TreeNodeModel.builder()
         .label(catalog.getString("Styles"))
         .type("folder")
         .leaf(false)
         .children(getChildNodes(null, mgr, securityEngine, principal, freehand))
         .build();

      return TreeNodeModel.builder().
         children(Collections.singletonList(root))
         .build();
   }

   public TreeNodeModel getStyleTree(Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      LibManager mgr = LibManager.getManager();

      TreeNodeModel root = TreeNodeModel.builder()
         .label(catalog.getString("Styles"))
         .type("folder")
         .leaf(false)
         .children(getChildNodes(null, mgr, securityEngine, principal, false))
         .build();

      return TreeNodeModel.builder()
         .children(Collections.singletonList(root))
         .build();
   }


   private java.util.List<TreeNodeModel> getChildNodes(String parentData, LibManager mgr,
                                                       SecurityEngine securityEngine,
                                                       Principal principal, boolean freehand)
   {
      java.util.List<TreeNodeModel> children = new ArrayList<>();
      java.util.List<String> folders = Arrays.asList(mgr.getTableStyleFolders(parentData, true));
      folders.sort(comparator);

      for(String folder : folders) {
         try {
            if(!securityEngine.checkPermission(
               principal, ResourceType.TABLE_STYLE, folder, ResourceAction.READ))
            {
               continue;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to check table style folder permission", exc);
         }

         java.util.List<TreeNodeModel> folderChildren =
            getChildNodes(folder, mgr, securityEngine, principal, false);

         if(!folderChildren.isEmpty()) {
            children.add(TreeNodeModel.builder()
                            .label(getDisplayName(folder))
                            .data(folder)
                            .type("folder")
                            .leaf(false)
                            .children(folderChildren)
                            .build());
         }
      }

      List<XTableStyle> tstyles = Arrays.asList(mgr.getTableStyles(parentData, true));
      tstyles.sort(comparator);

      for(XTableStyle style : tstyles) {
         try {
            if(!securityEngine.checkPermission(
               principal, ResourceType.TABLE_STYLE, style.getName(), ResourceAction.READ))
            {
               continue;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to check table style folder permission", exc);
         }

         children.add(TreeNodeModel.builder()
                         .label(getDisplayName(style.getName()))
                         .data(style.getID())
                         .icon("fa fa-table")
                         .type("style")
                         .leaf(true)
                         .build());
      }

      if(freehand) {
         children.add(TreeNodeModel.builder()
                         .label("Crosstab Style")
                         .data("inetsoft.report.style.CrosstabStyle")
                         .icon("fa fa-table")
                         .type("style")
                         .leaf(true)
                         .build());
      }

      return children;
   }


   private Comparator comparator = (o1, o2) -> {
      String path1;
      String path2;

      if(o1 instanceof XTableStyle && o2 instanceof XTableStyle) {
         XTableStyle style1 = (XTableStyle) o1;
         XTableStyle style2 = (XTableStyle) o2;
         path1 = style1.getName();
         path2 = style2.getName();
         int idx1 = path1.indexOf(StyleTreeModel.SEPARATOR);
         int idx2 = path2.indexOf(StyleTreeModel.SEPARATOR);

         // always put top level styles at the bottom
         if(idx1 >= 0 && idx2 < 0) {
            return -1;
         }
         else if(idx1 < 0 && idx2 >= 0) {
            return 1;
         }
      }
      else {
         path1 = o1 + "";
         path2 = o2 + "";
      }

      return path1.compareTo(path2);
   };

   private String getDisplayName(String path) {
      int index = path.lastIndexOf(LibManager.SEPARATOR);
      String name;
      Catalog catalog = Catalog.getCatalog();

      if(index < 0) {
         name = path;
      }
      else {
         name = path.substring(index + 1);
      }

      return catalog.getString(name);
   }


   private static final Logger LOG =
      LoggerFactory.getLogger(VSDialogService.class);
}