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
package inetsoft.uql.viewsheet.internal;

import java.io.Serializable;

/**
 * This interface defines the API for assemblies with pop component.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public interface PopVSAssemblyInfo {
   /**
    * Without pop component option.
    */
   public static final int NO_POP_OPTION = 0;
   /**
    * With pop component option.
    */
   public static final int POP_OPTION = 1;

   public enum PopLocation implements Serializable {
      MOUSE("MOUSE"),
      CENTER("CENTER");

      public final String value;

      private PopLocation(String value) {this.value=value;}
   }

   /**
    * Get the run time pop option.
    */
   public int getPopOption();

   /**
    * Set the run time pop option.
    */
   public void setPopOption(int popOption);

   /**
    * Get the design time pop option.
    */
   public int getPopOptionValue();

   /**
    * Set the design time pop option.
    */
   public void setPopOptionValue(int popOption);

   /**
    * Get the run time pop component.
    */
   public String getPopComponent();

   /**
    * Set the run time pop component.
    */
   public void setPopComponent(String popComponent);

   /**
    * Get the design time pop component.
    */
   public String getPopComponentValue();

   /**
    * Set the design time pop component.
    */
   public void setPopComponentValue(String popComponent);

   /**
    * set PopLocation design
    */
   public void setPopLocationValue(PopLocation popLocation);

   /**
    * set PopLocation runtime
    */
   public void setPopLocation(PopLocation popLocation);

   /**
    * get PopLocation design
    */
   public PopLocation getPopLocationValue();

   /**
    * get PopLocation runtime
    */
   public PopLocation getPopLocation();

   /**
    * Get the run time alpha.
    */
   public String getAlpha();

   /**
    * Set the run time alpha.
    */
   public void setAlpha(String alpha);

   /**
    * Get the design time alpha.
    */
   public String getAlphaValue();

   /**
    * Set the design time alpha.
    */
   public void setAlphaValue(String alpha);
}
