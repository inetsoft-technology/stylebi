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
package inetsoft.web.reportviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the td cell model for TableStylePageModel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionParameterModel extends ChoiceParameterModel {
   public OptionParameterModel() {
      super();
   }

   public void setSelectedValues(boolean[] selectedValues) {
      this.selectedValues = selectedValues;
   }

   public boolean[] getSelectedValues() {
      return this.selectedValues;
   }

   public boolean[] getSelectedValues(Object[] values, Object[] defaults) {
      boolean[] selected = null;

      if(values != null) {
         selected = new boolean[values.length];

         for(int i = 0; i < values.length; i++) {
            selected[i] = false;

            for(int j = 0; defaults != null && j < defaults.length; j++) {
               if(Tool.compare(defaults[j], values[i], true, true) == 0) {
                  selected[i] = true;
                  break;
               }
            }
         }
      }

      return selected;
   }

   public Object[] updateValues() {
      Object[] choiceVals = getChoicesValue();
      List<Object> values = new ArrayList<>();

      if(choiceVals != null) {
         for(int i = 0; i < choiceVals.length; i++) {
            if(selectedValues[i]) {
               values.add(choiceVals[i]);
            }
         }
      }

      return values.toArray(new Object[values.size()]);
   }

   private boolean[] selectedValues;
}
