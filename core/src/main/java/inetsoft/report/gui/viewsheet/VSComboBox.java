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

import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * VSComboBox component for view sheet.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class VSComboBox extends VSFloatable {
   /**
    * Constructor.
    */
   public VSComboBox(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the components in compound component.
    */
   @Override
   public void paintComponent(Graphics2D g) {
      ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) getAssemblyInfo();
      VSCompositeFormat format = info.getFormat();
      int w = getContentWidth();
      int h = getContentHeight();

      Graphics2D g2 = (Graphics2D) g.create(getContentX(), getContentY(),
                                            w + 1, h + 1);

      String[] labels = info.getLabels();
      Object[] values = info.getValues();
      // @by stephenwebster, fix bug1395864966703
      // default value to existing selected label
      String label = Tool.localize(info.getSelectedLabel());

      if(label != null && values != null && labels != null) {
         Object data = info.getSelectedObject();
         String type = info.getDataType();

         for(int i = 0; i < values.length; i++) {
            Object obj = Tool.getData(type, values[i]);

            if(Tool.equals(data, obj) && labels.length > i) {
               label = Tool.localize(labels[i]);
               break;
            }
         }
      }

      if(label == null) {
         Object obj = info.getSelectedObject();

         if(obj != null) {
            label = Tool.toString(obj);
         }
      }

      // @by stephenwebster, fix bug1395864966703
      // If the label was not set, the list values of the combobox may need
      // to be rebound. That will ensure that the datatype of the combobox
      // is properly set from its updated source.
      if(label == null) {
         label = "";
      }

      //Image img = getTheme().getImage("s|ComboBox", "upSkin", w, h);
      //g2.drawImage(img, 0, 0, null);
      g2.setColor(format.getBackground());
      g2.fillRect(0, 0, w, h);
      g2.setColor(format.getForeground());

      int align = format.getAlignment();
      // combobox doesn't support vertical layout in html, match it in export
      format.getUserDefinedFormat()
         .setAlignment((align & StyleConstants.H_ALIGN_MASK) | StyleConstants.V_CENTER);

      int imgW = 18;
      drawString(g2, 0, 0, w - imgW, h, label, format);

      g2.dispose();
   }
}
