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
package inetsoft.report.gui.viewsheet;

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.util.Tool;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * VSCheckBoxGroup component for view sheet.
 *
 * @version 8.5, 7/27/2006
 * @author InetSoft Technology Corp
 */
public class VSCheckBox extends VSCompound {
   /**
    * Constructor.
    */
   public VSCheckBox(Viewsheet vs) {
      super(vs);
   }

   /**
    * Create JCheckBox.
    */
   @Override
   public JComponent createComponent(int index) {
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) getAssemblyInfo();
      Object[] values = info.getValues();
      Set selected = new HashSet();
      Object[] arr = info.getSelectedObjects();
      String type = info.getDataType();

      for(int i = 0; arr != null && i < arr.length; i++) {
         selected.add(arr[i]);
      }

      String icon = selected.contains(Tool.getData(type, values[index])) ||
         selected.contains(Tool.toString(values[index]))
         ? "selectedUpIcon" : "upIcon";
      Image img = getTheme().getImage("s|CheckBox", icon, -1, -1);

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

      com.setIcon(new ImageIcon(img));
      com.setOpaque(false);

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
