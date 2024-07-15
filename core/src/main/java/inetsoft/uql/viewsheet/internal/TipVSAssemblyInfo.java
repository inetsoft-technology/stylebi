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

/**
 * This interface defines the API for assemblies with tooltip and data tip.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public interface TipVSAssemblyInfo {
   /**
    * Tooltip option.
    */
   public static final int TOOLTIP_OPTION = 0;
   /**
    * Data view tip option.
    */
   public static final int VIEWTIP_OPTION = 1;

   /**
    * Get the run time tip option.
    */
   public int getTipOption();

   /**
    * Set the run time tip option.
    */
   public void setTipOption(int tipOption);

   /**
    * Get the design time tip option.
    */
   public int getTipOptionValue();

   /**
    * Set the design time tip option.
    */
   public void setTipOptionValue(int tipOption);

   /**
    * Get the run time tip view.
    */
   public String getTipView();

   /**
    * Set the run time tip view.
    */
   public void setTipView(String tipView);

   /**
    * Get the design time tip view.
    */
   public String getTipViewValue();

   /**
    * Set the design time tip view.
    */
   public void setTipViewValue(String tipView);

   /**
    * Get the runtime alpha.
    */
   public String getAlpha();

   /**
    * Set the runtime alpha.
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


   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   public String[] getFlyoverViews();

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   public void setFlyoverViews(String[] views);

   /**
    * Get the views to apply filtering on mouse flyover over this assembly.
    */
   public String[] getFlyoverViewsValue();

   /**
    * Set the views to apply filtering on mouse flyover over this assembly.
    */
   public void setFlyoverViewsValue(String[] views);
}
