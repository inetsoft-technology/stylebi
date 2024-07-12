/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.graph.visual;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.TextSpec;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.aesthetic.ValueTextFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * This class defines visual text.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VOText extends VLabel implements PlotObject {
   /**
    * Constructor.
    * @param label the output string.
    * @param vo the element vo this text is a label of.
    * @param mname measure name.
    */
   public VOText(Object label, ElementVO vo, String mname) {
      this(label, vo, mname, null, -1, null);
   }

   /**
    * Constructor.
    * @param label the output string.
    * @param vo the element vo this text is a label of.
    * @param mname measure name.
    * @param data dataset to be used to evaluate highlight condition.
    * @param row row index of the corresponding data.
    */
   public VOText(Object label, ElementVO vo, String mname,
                 DataSet data, int row, Geometry gobj)
   {
      super(label, getLabelTextSpec(label, ((ElementGeometry) gobj).getElement(), mname));

      if(data != null && row >= 0) {
         String txtcol = null;

         // only use label value if it's a stacked (number). it could also be concated
         // string with both text binding and show values.
         if(label instanceof Number) {
            txtcol = getTextField(mname);
         }

         setTextSpec(getTextSpec().evaluate(data, row, gobj, txtcol, label));
      }

      this.mname0 = this.mname = mname;
      this.vo = vo;

      setMaxSize(new Dimension(150, 150));
      setZIndex(GDefaults.TEXT_Z_INDEX);
      setCollisionModifier(MOVE_FREE);
   }

   private String getTextField(String mname) {
      GraphElement elem = getGraphElement();
      TextFrame frame = elem != null ? elem.getTextFrame() : null;
      String txtcol = null;

      if(frame instanceof ValueTextFrame && valueText) {
         txtcol = ((ValueTextFrame) frame).getValueFrame().getField();
      }
      else if(frame != null) {
         txtcol = frame.getField();
      }

      // if no binding (show value), use measure name.
      return txtcol != null ? txtcol : mname;
   }

   private static TextSpec getLabelTextSpec(Object label, GraphElement elem, String mname) {
      TextSpec spec = elem.getLabelTextSpec(mname);

      if(label == null || !label.getClass().isArray() ||
         !(elem.getTextFrame() instanceof ValueTextFrame))
      {
         return spec;
      }

      ValueTextFrame fmt0 = (ValueTextFrame) elem.getTextFrame();
      spec = fmt0.applyCombinedFormat(spec);
      return spec;
   }

   /**
    * Get the measure/dimension associated with this point (for radar).
    */
   public String getPointMeasureName() {
      return mname0;
   }

   /**
    * Get measure name.
    */
   public String getMeasureName() {
      return mname;
   }

   /**
    * Set measure name.
    */
   public void setMeasureName(String mname) {
      this.mname = mname;
      GraphElement elem = getGraphElement();
      setTextSpec(getLabelTextSpec(getLabel(), elem, mname));
   }

   /**
    * Get the col index.
    */
   public int getColIndex() {
      return cidx;
   }

   /**
    * Set the col index.
    * @param cidx index the specified column index.
    */
   public void setColIndex(int cidx) {
      this.cidx = cidx;
   }

   /**
    * Check if this text should be kept in plot area.
    */
   @Override
   public boolean isInPlot() {
      return getGraphElement().isInPlot();
   }

   /**
    * Get the graph element.
    */
   public GraphElement getGraphElement() {
      return vo != null ? ((ElementGeometry) vo.getGeometry()).getElement() : null;
   }

   /**
    * Get the element vo this label is associated with.
    */
   public ElementVO getElementVO() {
      return vo;
   }

   /**
    * Check if this is a stack label.
    */
   public boolean isStacked() {
      return stacked;
   }

   /**
    * Set if this is a stack label.
    */
   public void setStacked(boolean stacked) {
      this.stacked = stacked;
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      if(pos == GraphConstants.TOP) {
         boolean hor = GTool.isHorizontal(coord.getCoordTransform());
         return hor ? getPreferredHeight() : getPreferredWidth();
      }
      else if(pos == GraphConstants.LEFT && getPlacement() == GraphConstants.LEFT) {
         boolean hor = GTool.isHorizontal(coord.getCoordTransform());
         return hor ? getPreferredWidth() : getPreferredHeight();
      }

      return 0;
   }

   @Override
   public boolean isRemovable() {
      GraphElement elem = getGraphElement();
      return !(elem instanceof PointElement && ((PointElement) elem).isWordCloud())
         && super.isRemovable();
   }

   /**
    * Set the bounds to clip text for painting.
    */
   public void setClipBounds(Rectangle2D b) {
      this.clipBounds = b;
   }

   /**
    * Get the bounds to clip text for painting.
    */
   public Rectangle2D getClipBounds() {
      return this.clipBounds;
   }

   @Override
   protected void clipGraphics(Graphics2D g2) {
      super.clipGraphics(g2);

      if(clipBounds != null) {
         g2.clip(clipBounds);
      }
   }

   /**
    * Get the placement of this label. It would be the actual placement if the element
    * label placement is AUTO.
    */
   public int getPlacement() {
      return placement;
   }

   /**
    * Set the placement of this label. It would be the actual placement if the element
    * label placement is AUTO.
    */
   public void setPlacement(int placement) {
      this.placement = (byte) placement;
   }

   /**
    * Check if this is displaying show-values instead of text binding value.
    */
   public boolean isValueText() {
      return valueText;
   }

   /**
    * Set whether this is displaying show-values instead of text binding value.
    */
   public void setValueText(boolean valueText) {
      this.valueText = valueText;
   }

   /**
    * Return sub-regions if this text is composed of multiple parts. This is used
    * for UI to select and set individual properties.
    */
   public VOText[] getSubTexts() {
      GraphElement elem = getGraphElement();

      if(getLabel() != null && getLabel().getClass().isArray() &&
         elem.getTextFrame() instanceof ValueTextFrame)
      {
         Rectangle2D bounds = getBounds();
         VOText textText = (VOText) clone();
         VOText valueText = (VOText) clone();
         textText.valueText = false;
         textText.setBounds(bounds.getX(), bounds.getY() + bounds.getHeight() / 2,
                            bounds.getWidth(), bounds.getHeight() / 2);
         valueText.valueText = true;
         valueText.setBounds(bounds.getX(), bounds.getY(),
                            bounds.getWidth(), bounds.getHeight() / 2);
         return new VOText[] { textText, valueText };
      }

      return new VOText[] { this };
   }

   private int cidx;
   private byte placement;
   // measure/dimension name for this point, may be different from the line measure name
   // for radar.
   private String mname0;
   private String mname;
   private ElementVO vo;
   private boolean stacked;
   private boolean valueText;
   private Rectangle2D clipBounds;
}
