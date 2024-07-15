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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.aesthetic.*;

/**
 * ChartRef, as a DataRef, it also stores the layout and format information
 * about axis, label, etc.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public interface ChartAggregateRef extends ChartRef, XAggregateRef, ChartBindable, HighlightRef {
   /**
    * The prefix added to discrete aggregate in full name.
    */
   public static final String DISCRETE_PREFIX = "discrete_";

   /**
    * Get the title descriptor of the ref.
    * @return the title descriptor.
    */
   public TitleDescriptor getTitleDescriptor();

   /**
    * Set the title descriptor of the ref.
    * @param titleDesc the title descriptor.
    */
   public void setTitleDescriptor(TitleDescriptor titleDesc);

   /**
    * Get the highlight group of this ref.
    * @return the highlight group.
    */
   @Override
   public HighlightGroup getHighlightGroup();

   /**
    * Get the hyperlink of the ref.
    * @return the hyperlink.
    */
   public Hyperlink getHyperlink();

   /**
    * Set the highlight group.
    */
   @Override
   public void setHighlightGroup(HighlightGroup group);

   /**
    * Set the hyperlink.
    */
   public void setHyperlink(Hyperlink link);

   /**
    * Check if color frame information has been modified from the default.
    */
   public boolean isColorChanged();

   /**
    * Check if shape frame information has been modified from the default.
    */
   public boolean isShapeChanged();

   /**
    * Check if size frame information has been modified from the default.
    */
   public boolean isSizeChanged();

   /**
    * Set the aggregate column of this reference.
    */
   @Override
   public void setDataRef(DataRef ref);

   /**
    * Set calculation of this reference.
    */
   @Override
   public void setCalculator(Calculator calc);

   /**
    * Get calculation of this reference.
    */
   @Override
   public Calculator getCalculator();

   /**
    * Check if equqls another object by content.
    */
   public boolean equalsContent(Object obj);

   /**
    * Set whether to display this measure on the secondary Y axis.
    */
   public void setSecondaryY(boolean y2);

   /**
    * Check whether to display this measure on the secondary Y axis.
    */
   public boolean isSecondaryY();

   /**
    * Get the summary color frame wrapper.
    */
   public ColorFrameWrapper getSummaryColorFrameWrapper();

   /**
    * Set the summary color frame wrapper.
    */
   public void setSummaryColorFrameWrapper(ColorFrameWrapper wrapper);

   /**
    * Get the summary texture frame wrapper.
    */
   public TextureFrameWrapper getSummaryTextureFrameWrapper();

   /**
    * Set the summary texture frame wrapper.
    */
   public void setSummaryTextureFrameWrapper(TextureFrameWrapper wrapper);

   /**
    * Get the summary color frame of the ref.
    * @return the summary color frame.
    */
   public ColorFrame getSummaryColorFrame();

   /**
    * Get the summary texture frame of the ref.
    * @return the summary texture frame.
    */
   public TextureFrame getSummaryTextureFrame();

   /**
    * Sets whether this aggregate should be treated like a dimension during
    * graph construction.
    */
   public void setDiscrete(boolean discrete);

   /**
    * Check whether this aggregate should be treated like a dimension during
    * graph construction.
    */
   public boolean isDiscrete();

   /**
    * Get shape/texture frame of this ref.
    */
   @Override
   public ShapeFrame getShapeFrame();

   /**
    * Set the shape/texture frame of this ref.
    */
   @Override
   public void setShapeFrame(ShapeFrame shframe);

   /**
    * Get the size frame for this ref.
    */
   @Override
   public SizeFrame getSizeFrame();

   /**
    * Set the size frame for this ref.
    */
   @Override
   public void setSizeFrame(SizeFrame zframe0);

   /**
    * Get the color frame of this ref.
    */
   @Override
   public ColorFrame getColorFrame();

   /**
    * Set the color frame of this ref.
    */
   @Override
   public void setColorFrame(ColorFrame clFrame);

   /**
    * Get the line frame of this ref.
    */
   @Override
   public LineFrame getLineFrame();

   /**
    * Set the line frame of this ref.
    */
   @Override
   public void setLineFrame(LineFrame lineFrame);

   /**
    * Get the texture frame.
    */
   @Override
   public TextureFrame getTextureFrame();

   /**
    * Set the texture frame.
    */
   @Override
   public void setTextureFrame(TextureFrame teFrame);

   @Override
   public ShapeFrameWrapper getShapeFrameWrapper();
   @Override
   public SizeFrameWrapper getSizeFrameWrapper();
   @Override
   public ColorFrameWrapper getColorFrameWrapper();
   @Override
   public LineFrameWrapper getLineFrameWrapper();
   @Override
   public TextureFrameWrapper getTextureFrameWrapper();

   void initDefaultFormat();

   void setSupportsLine(boolean supports);

   /**
    * Get name without discrete prefix.
    */
   static String getBaseName(String aggr) {
      if(aggr != null && aggr.startsWith(ChartAggregateRef.DISCRETE_PREFIX)) {
         aggr = aggr.substring(ChartAggregateRef.DISCRETE_PREFIX.length());
      }

      return aggr;
   }
}
