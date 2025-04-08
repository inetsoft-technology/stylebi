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
package inetsoft.web.viewsheet.event;

import java.io.Serializable;
import java.util.*;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class ApplySelectionListEvent implements Serializable {
   public Type getType() {
      return type;
   }

   public void setType(Type type) {
      this.type = type;
   }

   public List<Value> getValues() {
      if(values == null) {
         values = new ArrayList<>();
      }

      return values;
   }

   public String getEventSource() {
      return eventSource;
   }

   public void setEventSource(String eventSource) {
      this.eventSource = eventSource;
   }

   public void setValues(List<Value> values) {
      this.values = values;
   }

   public int getSelectStart() {
      return selectStart;
   }

   public void setSelectStart(int selectStart) {
      this.selectStart = selectStart;
   }

   public int getSelectEnd() {
      return selectEnd;
   }

   public void setSelectEnd(int selectEnd) {
      this.selectEnd = selectEnd;
   }

   public boolean isToggle() {
      return toggle;
   }

   public void setToggle(boolean toggle) {
      this.toggle = toggle;
   }

   public boolean isToggleAll() {
      return toggleAll;
   }

   public void setToggleAll(boolean toggleAll) {
      this.toggleAll = toggleAll;
   }

   public int[] getToggleLevels() {
      return toggleLevels;
   }

   public void setToggleLevels(int[] toggleLevels) {
      this.toggleLevels = toggleLevels;
   }

   @Override
   public String toString() {
      return "ApplySelectionListEvent{" +
         "type:" + type + " " +
         "values:" + values + " " +
         "selectStart:" + selectStart + " " +
         "selectEnd:" + selectEnd +
         '}';
   }

   private Type type;
   private List<Value> values;
   private int selectStart;
   private int selectEnd;
   private String eventSource;
   private boolean toggle;
   private boolean toggleAll;
   private int[] toggleLevels;

   public enum Type {
      APPLY, REVERSE
   }

   public static final class Value implements Serializable {
      public String[] getValue() {
         return value;
      }

      public void setValue(String[] value) {
         this.value = value;
      }

      public boolean isSelected() {
         return selected;
      }

      public void setSelected(boolean selected) {
         this.selected = selected;
      }

      @Override
      public String toString() {
         return "Value{" +
            "value=" + Arrays.toString(value) +
            ", selected=" + selected +
            '}';
      }

      private String[] value;
      private boolean selected;
   }
}
