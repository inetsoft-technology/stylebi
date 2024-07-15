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
package inetsoft.util.dep;

import java.util.HashMap;
import java.util.Map;

/**
 * XAsset configuration for importing an asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XAssetConfig {
   /**
    * Constructor.
    */
   public XAssetConfig() {
   }

   /**
    * Overwrite existing assets or not.
    * @return <tt>true</tt> if overwrite, <tt>false</tt> otherwise.
    */
   public boolean isOverwriting() {
      return overwriting;
   }

   /**
    * Set overwriting option.
    * @param overwriting <tt>true</tt> to overwrite, <tt>false</tt> otherwise.
    */
   public void setOverwriting(boolean overwriting) {
      this.overwriting = overwriting;
   }

   /**
    * Gets the shared context for the current import transaction.
    *
    * @return the context.
    */
   public Map<String, Object> getContext() {
      return context;
   }

   /**
    * Gets the context attribute with the specified name.
    *
    * @param name the name of the attribute.
    *
    * @param <T> the type of the attribute value.
    *
    * @return the attribute value.
    */
   @SuppressWarnings("unchecked")
   public <T> T getContextAttribute(String name) {
      return (T) context.get(name);
   }

   /**
    * Sets the value of a context attribute.
    *
    * @param name  the attribute name.
    * @param value the attribute value.
    */
   public void setContextAttribute(String name, Object value) {
      context.put(name, value);
   }

   private boolean overwriting;
   private final Map<String, Object> context = new HashMap<>();
}