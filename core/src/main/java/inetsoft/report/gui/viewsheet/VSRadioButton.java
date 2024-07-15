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
package inetsoft.report.gui.viewsheet;

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.util.Tool;

import javax.swing.*;
import java.awt.*;

/**
 * VSRadioButtonGroup component for view sheet.
 *
 * @version 8.5, 7/27/2006
 * @author InetSoft Technology Corp
 */
public class VSRadioButton extends VSCompound {
   /**
    * Constructor.
    */
   public VSRadioButton(Viewsheet vs) {
      super(vs);
   }

   /**
    * Create JRadioButton.
    */
   @Override
   public JComponent createComponent(int index) {
      RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) getAssemblyInfo();
      Object[] values = info.getValues();
      String type = info.getDataType();
      Object obj = Tool.getData(type, values[index]);
      boolean selected = Tool.equals(obj, info.getSelectedObject());
      String icon = selected ? "selectedUpIcon" : "upIcon";
      Image img = getTheme().getImage("s|RadioButton", icon, -1, -1);

      JLabel com = new JLabel() {
         @Override
         public GraphicsConfiguration getGraphicsConfiguration() {
            GraphicsConfiguration gc = null;

            try {
               gc = super.getGraphicsConfiguration();

               return gc != null ? gc : GraphicsEnvironment.
                  getLocalGraphicsEnvironment().getDefaultScreenDevice().
                     getDefaultConfiguration();
            }
            catch(Exception ex) {
               return gc;
            }
         }
      };

      com.setOpaque(false);
      com.setIcon(new ImageIcon(img));

      VSCompositeFormat[] vsformats = info.getFormats();
      VSCompositeFormat format = null;

      if(vsformats != null && vsformats.length > 0) {
         format = vsformats[0];
      }

      if(format != null && format.getFont() != null) {
         com.setFont(format.getFont());
      }

      if(format != null && format.getForeground() != null) {
         com.setForeground(format.getForeground());
      }

      return com;
   }
}
