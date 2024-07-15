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
package inetsoft.report.internal.info;

import inetsoft.report.ChartElement;
import inetsoft.report.internal.binding.BindingAttr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class represents a chart element. Chart elements are painters, so
 * a chart element also shares all properties in a painter element.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChartElementInfo extends PainterElementInfo
   implements GroupableInfo
{
   /**
    * Constructor.
    */
   public ChartElementInfo() {
      super();
      filters = new BindingAttr(ChartElement.class);
   }

   /**
    * Set the filter info holder in this table.
    */
   @Override
   public void setBindingAttr(BindingAttr infos) {
      filters = infos;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return filters;
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ChartElementInfo info = (ChartElementInfo) super.clone();

         if(filters != null) {
            info.setBindingAttr((BindingAttr) filters.clone());
         }

         return info;
      }
      catch(Exception e) {
         LOG.error("Failed to clone chart element info", e);
      }

      return null;
   }

   /**
    * Set the border color around this text box.
    */
   public void setBorderColor(Color color) {
      this.borderColor = color;
   }

   /**
    * Get the border color around this text box.
    */
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the individual border line styles. This overrides the default border
    * setting.
    * @param borders line styles defined in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorders(Insets borders) {
      this.borders = borders;
   }

   /**
    * Get the individual border line styles.
    * @return border line style.
    */
   public Insets getBorders() {
      return this.borders;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "chartElementInfo";
   }

   /**
    * Create an ElementInfo.
    */
   @Override
   protected ElementInfo create() {
      return new ChartElementInfo();
   }

   private BindingAttr filters;
    private Color borderColor;
   private Insets borders = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(ChartElementInfo.class);
}
