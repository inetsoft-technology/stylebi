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
package inetsoft.web.composer.vs.objects.event;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.dnd.TableTransfer;

import java.util.List;

/**
 * Class that encapsulates the parameters for changing the binding of an object.
 *
 * @since 12.3
 */
public class ChangeVSObjectBindingEvent extends VSObjectEvent {
   public List<AssetEntry> getBinding() {
      return binding;
   }

   public void setBinding(List<AssetEntry> binding) {
      this.binding = binding;
   }

   public TableTransfer getComponentBinding() {
      return componentBinding;
   }

   public void setComponentBinding(TableTransfer componentBinding) {
      this.componentBinding = componentBinding;
   }

   public int getX() {
      return x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return y;
   }

   public void setY(int y) {
      this.y = y;
   }

   public boolean isTab() {
      return tab;
   }

   public void setTab(boolean tab) {
      this.tab = tab;
   }

   @Override
   public String toString() {
      return "ChangeVSObjectBindingEvent{" +
         "name='" + this.getName() + '\'' +
         "binding='" + binding + '\'' +
         "componentBinding='" + componentBinding + '\'' +
         "x='" + x + '\'' +
         "y='" + y + '\'' +
         "tab='" + tab + '\'' +
         '}';
   }

   private List<AssetEntry> binding;
   private TableTransfer componentBinding;
   private int x;
   private int y;
   private boolean tab;
}
