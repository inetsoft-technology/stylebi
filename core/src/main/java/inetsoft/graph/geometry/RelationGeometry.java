/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.visual.*;

import java.awt.*;
import java.util.Objects;

/**
 * This represents a relation area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class RelationGeometry extends ElementGeometry {
   public RelationGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                           VisualModel vmodel, mxCell mxcell)
   {
      super(elem, graph, var, tidx, vmodel);
      this.mxcell = mxcell;
   }

   public Shape getShape() {
      return mxcell.getGeometry().getRectangle();
   }

   public mxCell getMxCell() {
      return mxcell;
   }

   public void setCellSize(double w, double h) {
      // fractions may cause layout problem. (58981)
      this.mxcell.getGeometry().setWidth(Math.round(w));
      this.mxcell.getGeometry().setHeight(Math.round(h));
   }

   public Color getColor() {
      RelationElement elem = (RelationElement) getElement();
      ColorFrame frame = elem.getNodeColorFrame();

      if(frame.getField() != null && !elem.isApplyAestheticsToSource()) {
         // if target is bound, source just use the static color.
         if(Objects.equals(getVar(), elem.getSourceDim())) {
            // should apply highlight when there are color binding. (57364, 57373)
            if(frame instanceof CompositeColorFrame) {
               CompositeColorFrame cframe = (CompositeColorFrame) frame;

               for(int i = 0; i < cframe.getFrameCount(); i++) {
                  ColorFrame sub = (ColorFrame) cframe.getFrame(i);

                  if(sub.isApplicable(getVar())) {
                     Color color = sub.getColor(getVisualModel().getDataSet(), getVar(),
                                                getSubRowIndex());

                     if(color != null) {
                        return color;
                     }
                  }
               }

               return super.getColor(0);
            }
            // check getVisualField() instead of getField() for highlight. (57303)
            else if(!frame.getVisualField().equals(getVar())) {
               return super.getColor(0);
            }
         }
      }

      // need to use var for highlight. (56936)
      return frame.getColor(getVisualModel().getDataSet(), getVar(), getSubRowIndex());
   }

   public double getSize() {
      RelationElement elem = (RelationElement) getElement();
      SizeFrame frame = elem.getNodeSizeFrame();

      if(frame.getField() != null) {
         return frame.getSize(getVisualModel().getDataSet(), frame.getField(), getSubRowIndex());
      }

      return frame.getSize(getVisualModel().getDataSet(), getVar(), getSubRowIndex());
   }

   public Object getText() {
      RelationElement elem = (RelationElement) getElement();

      // text frame applied to target and not source.
      if(Objects.equals(getVar(), elem.getSourceDim()) && elem.getTextFrame() != null) {
         return getMxCell().getValue();
      }

      return getText(0);
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      RelationVO vo = new RelationVO(this, coord);
      vo.setSubRowIndex(subridx);
      vo.setRowIndex(ridx);
      vo.setColIndex(cidx);
      return vo;
   }

   /**
    * Set row index.
    */
   public void setRowIndex(int ridx) {
      this.ridx = ridx;
   }

   /**
    * Get row index.
    */
   public int getRowIndex() {
      return ridx;
   }

   /**
    * Set sub row index.
    */
   public void setSubRowIndex(int subridx) {
      this.subridx = subridx;
   }

   /**
    * Get sub row index.
    */
   public int getSubRowIndex() {
      return subridx;
   }

   public String getOverlayId(ElementVO vo, DataSet data) {
      return mxcell.getId();
   }

   /**
    * Get the col index.
    */
   public int getColIndex() {
      return cidx;
   }

   /**
    * Set the col index.
    */
   public void setColIndex(int cidx) {
      this.cidx = (short) cidx;
   }

   public boolean isMultiParent() {
      return multiParent;
   }

   public void setMultiParent(boolean multiParent) {
      this.multiParent = multiParent;
   }

   @Override
   public int compareTo(Object obj) {
      // draw point on top of edge
      return obj instanceof RelationGeometry ? 0 : 1;
   }

   private int ridx; // row index
   private int subridx; // sub row index
   private short cidx; // root col
   private mxCell mxcell;
   private boolean multiParent = false;
}
