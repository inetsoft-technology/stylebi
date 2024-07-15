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
package inetsoft.graph.aesthetic;

import inetsoft.graph.LegendSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CompositeColorFrame combines multiple color frames to provide a cascading
 * behavior. Each frame is checked, and the first frame that returns
 * a color for the value is used.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class CompositeColorFrame extends ColorFrame implements CompositeVisualFrame {
   /**
    * Constructor.
    */
   public CompositeColorFrame() {
      super();
      this.frames = new ArrayList();
   }

   /**
    * Add a legend frame.
    */
   @Override
   public void addFrame(VisualFrame frame) {
      frames.add(frame);
   }

   /**
    * Get the number of legend frames.
    */
   @Override
   public int getFrameCount() {
      return frames.size();
   }

   /**
    * Get the legend frame at the specified index.
    */
   @Override
   public VisualFrame getFrame(int idx) {
      return frames.get(idx);
   }

   /**
    * Remove the legend frame at the specified index.
    */
   @Override
   public void removeFrame(int idx) {
      frames.remove(idx);
   }

   /**
    * Set the scale for mapping the value from a dataset to the frame.
    */
   @Override
   public void setScale(Scale scale) {
      ColorFrame frame = (ColorFrame) getGuideFrame();

      if(frame != null) {
         frame.setScale(scale);
      }
   }

   /**
    * Get the scale for mapping the value from a dataset to the frame.
    */
   @Override
   public Scale getScale() {
      ColorFrame frame = (ColorFrame) getGuideFrame();
      return (frame != null) ? frame.getScale() : null;
   }

   /**
    * Set the scaleOption for suppressing null.
    */
   @Override
   public void setScaleOption(int option) {
      for(VisualFrame frame : frames) {
         frame.setScaleOption(option);
      }
   }

   /**
    * Set the column associated with this frame.
    */
   @Override
   public void setField(String field) {
      ColorFrame frame = (ColorFrame) getGuideFrame();

      if(frame != null) {
         frame.setField(field);
      }
   }

   /**
    * Get the column associated with this frame.
    */
   @Override
   public String getField() {
      String field1 = null, field2 = null;

      for(VisualFrame frame : frames) {
         if(frame.isVisible()) {
            field1 = frame.getField();
         }
         else if(frame.getField() != null) {
            field2 = frame.getField();
         }
      }

      // give priority to guide frame.
      return field1 != null ? field1 : field2;
   }

   @Override
   public String getVisualField() {
      for(VisualFrame frame : frames) {
         if(frame.getVisualField() != null) {
            return frame.getVisualField();
         }
      }

      return null;
   }

   /**
    * Initialize the legend frame with values from the dataset.
    */
   @Override
   public void init(DataSet data) {
      for(VisualFrame frame : frames) {
         frame.init(data);
      }
   }

   /**
    * Get the color for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      for(int i = 0; i < frames.size(); i++) {
         ColorFrame frame = (ColorFrame) frames.get(i);
         Color color = frame.getColor(data, col, row);

         if(color != null) {
            return color;
         }
      }

      return Color.gray;
   }

   /**
    * Get the color for the specified value.
    */
   @Override
   public Color getColor(Object val) {
      ColorFrame frame = (ColorFrame) getGuideFrame();

      if(frame == null) {
         for(VisualFrame frame2 : frames) {
            if(frame2 instanceof StaticColorFrame) {
               return ((StaticColorFrame) frame2).getColor(val);
            }
         }

         // a categorical color frame may be created from a static color frame
         // for a single measure in VSColorFrameStrategy. (52250)
         for(VisualFrame frame2 : frames) {
            if(frame2 instanceof CategoricalColorFrame) {
               return ((ColorFrame) frame2).getColor(val);
            }
         }
      }

      // don't return a value if it's not found so the default can be applied by
      // the caller. (51933)
      return frame == null ? null : frame.getColor(val);
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   public String getTitle() {
      ColorFrame frame = (ColorFrame) getGuideFrame();
      return frame == null ? GTool.getString("Color") : frame.getTitle();
   }

   /**
    * Get the values mapped by this frame.
    */
   @Override
   public Object[] getValues() {
      ColorFrame frame = (ColorFrame) getGuideFrame();
      return frame == null ? new Object[0] : frame.getValues();
   }

   /**
    * Get the labels of the values to show on the legend. The default
    * implementation will just convert values to labels.
    */
   @Override
   public Object[] getLabels() {
      ColorFrame frame = (ColorFrame) getGuideFrame();
      return frame == null ? new Object[0] : frame.getLabels();
   }

   /**
    * Get the legend frame to generate legend guide.
    */
   @Override
   public VisualFrame getGuideFrame() {
      for(int i = 0; i < frames.size(); i++) {
         VisualFrame frame = frames.get(i);

         if(frame.isVisible()) {
            return frame;
         }
      }

      return null;
   }

   /**
    * Set the brightness. The return color is adjusted by multiplying the
    * brightness value. A value of one (1) doesn't change the color.
    */
   @Override
   public void setBrightness(double bright) {
      super.setBrightness(bright);

      for(int i = 0; i < frames.size(); i++) {
         VisualFrame frame = frames.get(i);

         if(frame != null && frame instanceof ColorFrame) {
            ((ColorFrame) frame).setBrightness(bright);
         }
      }
   }

   /**
    * Get the legend specification.
    */
   @Override
   public LegendSpec getLegendSpec() {
      ColorFrame frame = (ColorFrame) getGuideFrame();

      if(frame != null) {
         return frame.getLegendSpec();
      }

      return super.getLegendSpec();
   }

   /**
    * Set the legend attributes.
    */
   @Override
   public void setLegendSpec(LegendSpec legendSpec) {
      ColorFrame frame = (ColorFrame) getGuideFrame();

      if(frame != null) {
         frame.setLegendSpec(legendSpec);
      }
      else {
         super.setLegendSpec(legendSpec);
      }
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CompositeColorFrame frame = (CompositeColorFrame) super.clone();
         List<VisualFrame> nframes = new ArrayList();

         for(int i = 0; i < frames.size(); i++) {
            nframes.add((VisualFrame) frames.get(i).clone());
         }

         frame.frames = nframes;

         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone color frame", ex);
         return null;
      }
   }

   /**
    * Check if this frame has been initialized and is ready to be used.
    */
   @Override
   public boolean isValid() {
      if(super.isValid()) {
         return true;
      }

      for(int i = 0; i < frames.size(); i++) {
         ColorFrame frame = (ColorFrame) frames.get(i);

         if(frame.isValid()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public Stream<VisualFrame> getFrames(Class type) {
      return frames.stream().filter(a -> a != null && type.isAssignableFrom(a.getClass()));
   }

   @Override
   public VisualFrame getLegendFrame() {
      for(VisualFrame frame : frames) {
         if(frame != null && frame.getLegendFrame() != null) {
            return frame.getLegendFrame();
         }
      }

      return null;
   }

   @Override
   public String getShareId() {
      VisualFrame frame = getGuideFrame();
      return frame != null ? frame.getShareId() : super.getShareId();
   }

   @Override
   public void setGraphDataSelector(GraphtDataSelector selector) {
      frames.forEach(a -> a.setGraphDataSelector(selector));
   }

   @Override
   public GraphtDataSelector getGraphDataSelector() {
      return frames.stream().findFirst().map(a -> a.getGraphDataSelector()).orElse(null);
   }

   @Override
   public String toString() {
      return super.toString() + frames;
   }

   private List<VisualFrame> frames;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(VisualFrame.class);
}
