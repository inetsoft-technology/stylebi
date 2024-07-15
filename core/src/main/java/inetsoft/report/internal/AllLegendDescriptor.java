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
package inetsoft.report.internal;

import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.*;

import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is a wrapper for getting and setting legend descriptor properties
 * across multiple measures.
 *
 * @version 11.4
 * @author InetSoft Technology Corp.
 */
public class AllLegendDescriptor extends LegendDescriptor {
   /**
    * Create a proxy to all legend descriptors of the specified type.
    * @param type aesthetic type defined in ChartArea.
    */
   public AllLegendDescriptor(ChartInfo info, LegendsDescriptor desc,
                              String type)
   {
      this.info = info;
      this.desc = desc;
      this.type = type;
   }

   /**
    * Create a proxy to all legend descriptors.
    */
   public AllLegendDescriptor(List<LegendDescriptor> legends) {
      this.legends = legends;
   }

   @Override
   public void initDefaultFormat() {
      // don't change the format of child descriptor
   }

   @Override
   public boolean isVisible() {
      return (Boolean) applyGet("isVisible");
   }

   @Override
   public void setVisible(boolean visible) {
      applySet("setVisible", new Object[] {visible},
               new Class[] {boolean.class});
   }

   public boolean isMaxModeVisible() {
      return (Boolean) applyGet("isMaxModeVisible");
   }

   /**
    * Set if the legend is displayed in max mode.
    */
   public void setMaxModeVisible(boolean maxModeVisible) {
      applySet("setMaxModeVisible", new Object[] {maxModeVisible},
               new Class[] {boolean.class});
   }

   @Override
   public boolean isTitleVisible() {
      return (Boolean) applyGet("isTitleVisible");
   }

   @Override
   public void setTitleVisible(boolean titleVisible) {
      applySet("setTitleVisible", new Object[] {titleVisible},
               new Class[] {boolean.class});
   }

   @Override
   public boolean isLogarithmicScale() {
      return (Boolean) applyGet("isLogarithmicScale");
   }

   @Override
   public void setLogarithmicScale(boolean logScale) {
      applySet("setLogarithmicScale", new Object[] {logScale},
               new Class[] {boolean.class});
   }

   @Override
   public boolean isReversed() {
      return (Boolean) applyGet("isReversed");
   }

   @Override
   public void setReversed(boolean reversed) {
      applySet("setReversed", new Object[] {reversed},
               new Class[] {boolean.class});
   }

   @Override
   public boolean isNotShowNull() {
      return (Boolean) applyGet("isNotShowNull");
   }

   @Override
   public void setNotShowNull(boolean notShowNull) {
      applySet("setNotShowNull", new Object[] {notShowNull},
               new Class[] {boolean.class});
   }

   @Override
   public Dimension2D getPreferredSize() {
      return (Dimension2D) applyGet("getPreferredSize");
   }

   @Override
   public void setPreferredSize(Dimension2D size) {
      applySet("setPreferredSize", new Object[] {size},
               new Class[] {Dimension2D.class});
   }

   @Override
   public Point2D getPosition() {
      return (Point2D) applyGet("getPosition");
   }

   @Override
   public void setPosition(Point2D pos) {
      applySet("setPosition", new Object[] {pos},
               new Class[] {Point2D.class});
   }

   @Override
   public String getTitle() {
      return (String) applyGet("getTitle");
   }

   @Override
   public void setTitle(String title) {
      applySet("setTitle", new Object[] {title},
               new Class[] {String.class});
   }

   @Override
   public String getTitleValue() {
      return (String) applyGet("getTitleValue");
   }

   @Override
   public void setTitleValue(String title) {
      applySet("setTitleValue", new Object[] {title},
               new Class[] {String.class});
   }

   @Override
   public CompositeTextFormat getContentTextFormat() {
      List<LegendDescriptor> legends = getLegendDescriptors();
      List<CompositeTextFormat> fmts = new ArrayList<>();

      for(LegendDescriptor legend : legends) {
         fmts.add(legend.getContentTextFormat());
      }

      return new AllCompositeTextFormat(fmts);
   }

   @Override
   public void setContentTextFormat(CompositeTextFormat fmt0) {
      fmt0 = AllCompositeTextFormat.fixFormat(fmt0);
      applySet("setContentTextFormat", new Object[] {fmt0},
               new Class[] {CompositeTextFormat.class});
   }

   @Override
   public void setLabelAlias(String key, String value) {
      applySet("setLabelAlias", new Object[] {key, value},
               new Class[] {String.class, String.class});
   }

   @Override
   public String getLabelAlias(String key) {
      return (String) applyGet("getLabelAlias", key);
   }

   @Override
   public void clearLabelAlias() {
      applyGet("clearLabelAlias");
   }

   @Override
   public TextFrame getTextFrame() {
      return (TextFrame) applyGet("getTextFrame");
   }

   /**
    * Retrieve property from multiple objects.
    */
   private Object applyGet(String funcName, Object... args) {
      Object obj = null;

      try {
         Class[] params = new Class[args.length];
         List legends = getLegendDescriptors();

         for(int i = 0; i < args.length; i++) {
            params[i] = args[i].getClass();
         }

         if(legends.size() > 0) {
            Method func = LegendDescriptor.class.getMethod(
               funcName, params);
            obj = func.invoke(legends.get(0), args);
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
   private void applySet(String funcName, Object[] params, Class[] types) {
      try {
         List legends = getLegendDescriptors();
         Method func = LegendDescriptor.class.getMethod(
            funcName, types);

         for(int i = 0; i < legends.size(); i++) {
            func.invoke(legends.get(i), params);
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Get corresponding legend descriptors.
    */
   private List<LegendDescriptor> getLegendDescriptors() {
      return (legends != null) ? legends
         : GraphUtil.getLegendDescriptors(info, desc, type);
   }

   private ChartInfo info;
   private LegendsDescriptor desc;
   private String type;
   private List<LegendDescriptor> legends;
}
