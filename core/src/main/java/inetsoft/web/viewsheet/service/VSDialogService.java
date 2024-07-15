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

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import org.springframework.stereotype.Service;

import java.awt.*;

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
}