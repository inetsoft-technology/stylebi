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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.CompositeValue;

import java.awt.*;

/**
 * This interface is implemented by assemblies with a title.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface TitledVSAssemblyInfo {
   /**
    * Get the run time title.
    * @return the title of assembly.
    */
   public String getTitle();

   /**
    * Set the run time title.
    * @param value the specified title.
    */
   public void setTitle(String value);

   /**
    * Get the title value in design time.
    * @return the title value of assembly.
    */
   public String getTitleValue();

   /**
    * Set the design time title value.
    * @param value the specified title value.
    */
   public void setTitleValue(String value);

   /**
    * Check whether current assembly title is visible in run time.
    * @return true if title is visible, otherwise false.
    */
   public boolean isTitleVisible();

   /**
    * Set the runtime title visible.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisible(boolean visible);

   /**
    * Check whether current assembly title is visible in design time.
    * @return true if title is visible, otherwise false.
    */
   public boolean getTitleVisibleValue();

   /**
    * Set the design time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisibleValue(boolean visible);

   /**
    * Get the run time title height.
    * @return the title height of assembly.
    */
   public int getTitleHeight();

   /**
    * Set the run time title height.
    * @param value the specified title height.
    */
   public void setTitleHeight(int value);

   /**
    * Get the title height value in design time.
    * @return the title height value of assembly.
    */
   public int getTitleHeightValue();

   /**
    * Set the design time title height value.
    * @param value the specified title height value.
    */
   public void setTitleHeightValue(int value);

   Insets getTitlePadding();


   void setTitlePadding(Insets padding, CompositeValue.Type type);
}
