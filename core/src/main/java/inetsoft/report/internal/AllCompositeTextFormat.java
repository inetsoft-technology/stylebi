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

import inetsoft.report.composition.graph.GraphFormatUtil;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.viewsheet.graph.*;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

/**
 * This class is a wrapper for getting and setting text format properties
 * across multiple formats.
 *
 * @version 11.4
 * @author InetSoft Technology Corp.
 */
public class AllCompositeTextFormat extends CompositeTextFormat {
   public static CompositeTextFormat fixFormat(CompositeTextFormat fmt) {
      if(!(fmt instanceof AllCompositeTextFormat)) {
         return fmt;
      }

      AllCompositeTextFormat afmt = (AllCompositeTextFormat) fmt;
      return afmt.fmts.get(0);
   }

   /**
    * Create a proxy to all formats.
    */
   public AllCompositeTextFormat(List<CompositeTextFormat> fmts) {
      this.fmts = fmts;
   }

   /**
    * Create a proxy to all value format in a chart.
    */
   public AllCompositeTextFormat(ChartInfo info, PlotDescriptor plot) {
      Map<Set, Set> groups = info.getRTFieldGroups();
      Set all = new HashSet();
      fmts = new ArrayList<>();

      if(!info.isMultiAesthetic()) {
         fmts.add(GraphFormatUtil.getTextFormat(info, null, plot));
      }

      for(Set group : groups.keySet()) {
         all.addAll(group);
         all.addAll(groups.get(group));
      }

      for(Object obj : all) {
         if(obj instanceof ChartRef) {
            if(obj instanceof ChartAggregateRef) {
               ChartAggregateRef aggr = (ChartAggregateRef) obj;
               fmts.add(GraphFormatUtil.getTextFormat(aggr, aggr, plot));
               continue;
            }

            fmts.add(((ChartRef) obj).getTextFormat());
         }
      }
   }

   @Override
   public Font getFont() {
      return (Font) applyGet("getFont");
   }

   @Override
   public int getAlignment() {
      return (Integer) applyGet("getAlignment");
   }

   @Override
   public Color getColor() {
      return (Color) applyGet("getColor");
   }

   @Override
   public Color getBackground() {
      return (Color) applyGet("getBackground");
   }

   @Override
   public int getAlpha() {
      return (Integer) applyGet("getAlpha");
   }

   @Override
   public Number getRotation() {
      return (Number) applyGet("getRotation");
   }

   @Override
   public XFormatInfo getFormat() {
      return (XFormatInfo) applyGet("getFormat");
   }

   /**
    * Get user defined format.
    */
   @Override
   public TextFormat getUserDefinedFormat() {
      List<TextFormat> tfmts = new ArrayList();

      for(CompositeTextFormat fmt : fmts) {
         tfmts.add(fmt.getUserDefinedFormat());
      }

      return new AllTextFormat(tfmts);
   }

   /**
    * Set user defined format.
    */
   @Override
   public void setUserDefinedFormat(TextFormat fmt) {
      fmt = AllTextFormat.fixFormat(fmt);
      applySet("setUserDefinedFormat", fmt, TextFormat.class);
   }

   @Override
   public void setFont(Font font) {
      applySet("setFont", font, Font.class);
   }

   @Override
   public void setAlignment(int align) {
      applySet("setAlignment", align, int.class);
   }

   @Override
   public void setColor(Color color) {
      applySet("setColor", color, Color.class);
   }

   @Override
   public void setBackground(Color color) {
      applySet("setBackground", color, Color.class);
   }

   @Override
   public void setAlpha(int alpha) {
      applySet("setAlpha", alpha, int.class);
   }

   @Override
   public void setRotation(Number rotation) {
      applySet("setRotation", rotation, Number.class);
   }

   @Override
   public void setFormat(XFormatInfo finfo) {
      applySet("setFormat", finfo, XFormatInfo.class);
   }

   /**
    * Retrieve property from multiple objects.
    */
   private Object applyGet(String funcName) {
      if(fmts.size() == 0) {
         return null;
      }

      try {
         Method func = CompositeTextFormat.class.getMethod(
            funcName, new Class[0]);

         return func.invoke(fmts.get(0));
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Make function call on all objects.
    */
   private void applySet(String funcName, Object param, Class type) {
      try {
         Method func = CompositeTextFormat.class.getMethod(
            funcName, new Class[] { type });

         for(int i = 0; i < fmts.size(); i++) {
            func.invoke(fmts.get(i), param);
         }
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   @Override
   public CompositeTextFormat clone() {
      try {
         List<CompositeTextFormat> fmts0 = new ArrayList();

         for(CompositeTextFormat fmt : fmts) {
            fmts0.add((CompositeTextFormat) fmt.clone());
         }

         return new AllCompositeTextFormat(fmts0);
      }
      catch(Exception ex) {
         LOG.error("Failed to clone text format", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(java.io.PrintWriter writer) {
      throw new RuntimeException("Unsupported method \"writeXML\" call!");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(org.w3c.dom.Element elem) throws Exception {
      throw new RuntimeException("Unsupported method \"parseXML\" call!");
   }

   private List<CompositeTextFormat> fmts;
}
