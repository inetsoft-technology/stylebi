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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.lang.reflect.Method;
import java.util.List;

/**
 * This class is a wrapper for getting and setting binding on a list of
 * aggregates.
 *
 * @version 11.4
 * @author InetSoft Technology Corp.
 */
public class AllChartAggregateRef extends VSChartAggregateRef {
   public AllChartAggregateRef(ChartInfo info, List<ChartAggregateRef> aggrs) {
      this.info = info;
      this.aggrs = aggrs;
   }

   /**
    * Get the aggregate from X/Y fields.
    */
   public static List<ChartAggregateRef> getXYAggregateRefs(ChartInfo info, boolean runtime) {
      return info.getAestheticAggregateRefs(runtime);
   }

   public List<ChartAggregateRef> getChartAggregateRefs() {
      return aggrs;
   }

   @Override
   public int getChartType() {
      if(!info.isMultiStyles()) {
         return info.getChartType();
      }

      Integer val = (Integer) applyGet("getChartType");
      return (val == null) ? 0 : val;
   }

   @Override
   public int getRTChartType() {
      Integer val = (Integer) applyGet("getRTChartType");
      return (val == null) ? 0 : val;
   }

   @Override
   public ShapeFrame getShapeFrame() {
      return (ShapeFrame) applyGet("getShapeFrame");
   }

   @Override
   public TextureFrame getTextureFrame() {
      return (TextureFrame) applyGet("getTextureFrame");
   }

   @Override
   public ColorFrame getColorFrame() {
      return (ColorFrame) applyGet("getColorFrame");
   }

   @Override
   public LineFrame getLineFrame() {
      return (LineFrame) applyGet("getLineFrame");
   }

   @Override
   public ColorFrame getSummaryColorFrame(){
      return (ColorFrame) applyGet("getSummaryColorFrame");
   }

   @Override
   public TextureFrame getSummaryTextureFrame() {
      return (TextureFrame) applyGet("getSummaryTextureFrame");
   }

   @Override
   public ColorFrameWrapper getSummaryColorFrameWrapper(){
      return (ColorFrameWrapper) applyGet("getSummaryColorFrameWrapper");
   }

   @Override
   public TextureFrameWrapper getSummaryTextureFrameWrapper() {
      return (TextureFrameWrapper) applyGet("getSummaryTextureFrameWrapper");
   }

   @Override
   public SizeFrame getSizeFrame() {
      return (SizeFrame) applyGet("getSizeFrame");
   }

   @Override
   public AestheticRef getColorField() {
      return (AestheticRef) applyGet("getColorField");
   }

   @Override
   public AestheticRef getShapeField() {
      return (AestheticRef) applyGet("getShapeField");
   }

   @Override
   public AestheticRef getSizeField() {
      return (AestheticRef) applyGet("getSizeField");
   }

   @Override
   public AestheticRef getTextField() {
      return (AestheticRef) applyGet("getTextField");
   }

   @Override
   public DataRef getRTColorField() {
      return (DataRef) applyGet("getRTColorField");
   }

   @Override
   public DataRef getRTShapeField() {
      return (DataRef) applyGet("getRTShapeField");
   }

   @Override
   public DataRef getRTSizeField() {
      return (DataRef) applyGet("getRTSizeField");
   }

   @Override
   public DataRef getRTTextField() {
      return (DataRef) applyGet("getRTTextField");
   }

   @Override
   public void setShapeFrame(ShapeFrame shFrame0) {
      applySet("setShapeFrame", shFrame0, ShapeFrame.class);
   }

   @Override
   public void setTextureFrame(TextureFrame textureFrame0) {
      applySet("setTextureFrame", textureFrame0, TextureFrame.class);
   }

   @Override
   public void setColorFrame(ColorFrame cFrame0) {
      applySet("setColorFrame", cFrame0, ColorFrame.class);
   }

   @Override
   public void setLineFrame(LineFrame lFrame) {
      applySet("setLineFrame", lFrame, LineFrame.class);
   }

   public void setSummaryColorFrame(ColorFrame scFrame) {
      applySet("setSummaryColorFrame", scFrame, ColorFrame.class);
   }

   public void setSummaryTextureFrame(TextureFrame sstFrame) {
      applySet("setSummaryTextureFrame", sstFrame, TextureFrame.class);
   }

   @Override
   public void setSizeFrame(SizeFrame zframe) {
      applySet("setSizeFrame", zframe, SizeFrame.class);
   }

   @Override
   public void setColorField(AestheticRef field) {
      applySet("setColorField", field, AestheticRef.class);
   }

   @Override
   public void setShapeField(AestheticRef field) {
      applySet("setShapeField", field, AestheticRef.class);
   }

   @Override
   public void setSizeField(AestheticRef field) {
      applySet("setSizeField", field, AestheticRef.class);
   }

   @Override
   public void setTextField(AestheticRef field) {
      applySet("setTextField", field, AestheticRef.class);
   }

   @Override
   public ColorFrameWrapper getColorFrameWrapper() {
      return (ColorFrameWrapper) applyGet("getColorFrameWrapper");
   }

   @Override
   public ShapeFrameWrapper getShapeFrameWrapper() {
      return (ShapeFrameWrapper) applyGet("getShapeFrameWrapper");
   }

   @Override
   public LineFrameWrapper getLineFrameWrapper() {
      return (LineFrameWrapper) applyGet("getLineFrameWrapper");
   }

   @Override
   public TextureFrameWrapper getTextureFrameWrapper() {
      return (TextureFrameWrapper) applyGet("getTextureFrameWrapper");
   }

   @Override
   public SizeFrameWrapper getSizeFrameWrapper() {
      return (SizeFrameWrapper) applyGet("getSizeFrameWrapper");
   }

   @Override
   public void setColorFrameWrapper(ColorFrameWrapper wrapper) {
      applySet("setColorFrameWrapper", wrapper, ColorFrameWrapper.class);
   }

   @Override
   public void setShapeFrameWrapper(ShapeFrameWrapper wrapper) {
      applySet("setShapeFrameWrapper", wrapper, ShapeFrameWrapper.class);
   }

   @Override
   public void setLineFrameWrapper(LineFrameWrapper wrapper) {
      applySet("setLineFrameWrapper", wrapper, LineFrameWrapper.class);
   }

   @Override
   public void setTextureFrameWrapper(TextureFrameWrapper wrapper) {
      applySet("setTextureFrameWrapper", wrapper, TextureFrameWrapper.class);
   }

   @Override
   public void setSizeFrameWrapper(SizeFrameWrapper wrapper) {
      applySet("setSizeFrameWrapper", wrapper, SizeFrameWrapper.class);
   }

   @Override
   public void setTextFormat(CompositeTextFormat fmt) {
      applySet("setTextFormat", fmt, CompositeTextFormat.class);
   }

   @Override
   public CompositeTextFormat getTextFormat() {
      CompositeTextFormat fmt = (CompositeTextFormat) applyGet("getTextFormat");
      return fmt != null ? (CompositeTextFormat) fmt.clone()
         : new CompositeTextFormat();
   }

   @Override
   public String getFullName() {
      return Catalog.getCatalog().getString("(all)");
   }

   @Override
   public String toView() {
      return getFullName();
   }

   /**
    * Check if the property contains different values from aggregates.
    */
   public boolean isMixedValue(String funcName) {
      try {
         Object obj = null;
         Method func = ChartAggregateRef.class.getMethod(funcName);

         for(int i = 0; i < aggrs.size(); i++) {
            Object obj2 = func.invoke(aggrs.get(i));

            // ignore whether it's stacked
            if("getRTChartType".equals(funcName)) {
               obj2 = ((Integer) obj2) & ~0x20;
            }

            if(i == 0) {
               obj = obj2;
            }
            else if(obj instanceof AestheticRef && obj2 instanceof AestheticRef) {
               if(!mixedEquals((AestheticRef) obj, (AestheticRef) obj2)) {
                  return true;
               }
            }
            else if(!Tool.equals(obj, obj2)) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }

      return false;
   }

   /**
    * Retrieve property from multiple objects.
    */
   private Object applyGet(String funcName) {
      Object obj = null;

      try {
         Method func = ChartAggregateRef.class.getMethod(funcName);

         for(int i = 0; i < aggrs.size(); i++) {
            Object obj2 = func.invoke(aggrs.get(i));

            if(i == 0) {
               obj = obj2;
            }
            else if(obj instanceof AestheticRef) {
               if(obj2 == null) {
                  return null;
               }

               String name1 = ((AestheticRef) obj).getFullName();
               String name2 = ((AestheticRef) obj2).getFullName();

               // @see AllChartAggregateRef.as
               if(!Tool.equals(name1, name2)) {
                  return null;
               }
            }
            else {
               Object obj1 = obj;

               if(obj1 instanceof VisualFrameWrapper && obj2 instanceof VisualFrameWrapper) {
                  // use id so we can ignore the irrelevant diff (e.g. default color). (57366)
                  obj1 = ((VisualFrameWrapper) obj1).getVisualFrame().getUniqueId();
                  obj2 = ((VisualFrameWrapper) obj2).getVisualFrame().getUniqueId();
               }

               if(!Tool.equals(obj1, obj2)) {
                  return null;
               }
            }
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }

      return obj;
   }

   /**
    * Make function call on all objects.
    */
   private void applySet(String funcName, Object param, Class<?> type) {
      try {
         Method func = ChartAggregateRef.class.getMethod(funcName, type);

         for(XAggregateRef aggr : aggrs) {
            // avoid multiple aggrs sharing a same object
            if(param instanceof CompositeTextFormat) {
               param = ((CompositeTextFormat) param).clone();
            }
            else if(param instanceof AestheticRef) {
               param = ((AestheticRef) param).clone();
            }

            func.invoke(aggr, param);
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Check if considered mixed in aesthetic gui.
    */
   private boolean mixedEquals(AestheticRef aref, AestheticRef aref2) {
      if(!mixedEquals(aref.getVisualFrame(), aref2.getVisualFrame())) {
         return false;
      }

      DataRef ref1 = aref.getDataRef();
      DataRef ref2 = aref2.getDataRef();

      if(ref1 instanceof ChartDimensionRef && ref2 instanceof ChartDimensionRef) {
         ChartDimensionRef dim1 = (ChartDimensionRef) ref1;
         ChartDimensionRef dim2 = (ChartDimensionRef) ref2;

         if(!mixedEquals(dim1, dim2)) {
            return false;
         }

         return Tool.equals(dim1.getTextFormat(), dim2.getTextFormat());
      }

      return Tool.equals(aref, aref2);
   }

   /**
    * Check if considered mixed in aesthetic gui.
    */
   private boolean mixedEquals(ChartDimensionRef obj1, ChartDimensionRef obj2) {
      if(!Tool.equals(obj1.getFullName(), obj2.getFullName()) ||
         (obj1.isDateTime() && obj2.isDateTime() &&
          obj1.getDateLevel() != obj2.getDateLevel()) ||
         obj1.getRankingOption() != obj2.getRankingOption() ||
         obj1.getOrder() != obj2.getOrder())
      {
         return false;
      }

      if((obj1.getRankingOption() == XCondition.TOP_N ||
          obj1.getRankingOption() == XCondition.BOTTOM_N))
      {
         if((obj1 instanceof VSChartDimensionRef &&
                  obj2 instanceof VSChartDimensionRef) &&
                 (obj1.isGroupOthers() != obj2.isGroupOthers() ||
                  obj1.getRankingN() != obj2.getRankingN() ||
                  !Tool.equals(obj1.getRankingCol(), obj2.getRankingCol())))
              {
                 return false;
              }

      }

      if(!Tool.equals(obj1.getNamedGroupInfo(), obj2.getNamedGroupInfo())) {
         return false;
      }

      if(obj1.getOrder() == XConstants.SORT_VALUE_ASC ||
         obj1.getOrder() == XConstants.SORT_VALUE_DESC)
      {
         return Tool.equals(obj1.getSortByCol(), obj2.getSortByCol());
      }

      return true;
   }

   /**
    * Check if two frames are the same.
    */
   private boolean mixedEquals(VisualFrame obj1, VisualFrame obj2) {
      if(!Tool.equals(obj1, obj2)) {
         return false;
      }

      if(obj1 instanceof SizeFrame && obj2 instanceof SizeFrame) {
         SizeFrame frame1 = (SizeFrame) obj1;
         SizeFrame frame2 = (SizeFrame) obj2;

         // @see SizeFrame.equals() comments
         return frame1.getSmallest() == frame2.getSmallest() &&
            frame1.getLargest() == frame2.getLargest();
      }

      return true;
   }

   private ChartInfo info;
   private List<ChartAggregateRef> aggrs; // list of ChartAggregateRef
}
