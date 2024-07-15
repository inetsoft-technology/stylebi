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
package inetsoft.graph.mxgraph.util;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Administrator
 */
public class mxLightweightLabel extends JLabel {

   private static final Logger log = Logger.getLogger(mxLightweightLabel.class.getName());

   /**
    *
    */
   private static final long serialVersionUID = 1L;

   /**
    *
    */
   protected static mxLightweightLabel sharedInstance;

   /**
    * Initializes the shared instance.
    */
   static {
      try {
         sharedInstance = new mxLightweightLabel();
      }
      catch(Exception e) {
         log.log(Level.SEVERE, "Failed to initialize the shared instance", e);
      }
   }

   /**
    *
    */
   public mxLightweightLabel()
   {
      setFont(new Font(mxConstants.DEFAULT_FONTFAMILY, 0,
                       mxConstants.DEFAULT_FONTSIZE));
      setVerticalAlignment(SwingConstants.TOP);
   }

   /**
    *
    */
   public static mxLightweightLabel getSharedInstance()
   {
      return sharedInstance;
   }

   /**
    * Overridden for performance reasons.
    */
   public void validate()
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void revalidate()
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void repaint(long tm, int x, int y, int width, int height)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void repaint(Rectangle r)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   protected void firePropertyChange(String propertyName, Object oldValue,
                                     Object newValue)
   {
      // Strings get interned...
      if(propertyName == "text" || propertyName == "font") {
         super.firePropertyChange(propertyName, oldValue, newValue);
      }
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, byte oldValue,
                                  byte newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, char oldValue,
                                  char newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, short oldValue,
                                  short newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, int oldValue,
                                  int newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, long oldValue,
                                  long newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, float oldValue,
                                  float newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, double oldValue,
                                  double newValue)
   {
   }

   /**
    * Overridden for performance reasons.
    */
   public void firePropertyChange(String propertyName, boolean oldValue,
                                  boolean newValue)
   {
   }

}
