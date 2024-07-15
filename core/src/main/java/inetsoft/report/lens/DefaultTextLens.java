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
package inetsoft.report.lens;

import inetsoft.report.TextLens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper class for a text string to provide a TextLens interface.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultTextLens implements TextLens {
   /**
    * Create an empty text lens.
    */
   public DefaultTextLens() {
      super();
   }

   /**
    * Create a wrapper with the text contents.
    * @param text string value.
    */
   public DefaultTextLens(String text) {
      this();

      this.text = text;
   }

   /**
    * Get the text contents.
    * @return string value.
    */
   @Override
   public String getText() {
      return text;
   }

   /**
    * Set the text contents.
    * @param text content.
    */
   public void setText(String text) {
      if(text == null) {
         text = "";
      }

      this.text = text;
   }

   /**
    * Make a copy of the text lens.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "DefaultTextLens" + '[' + text + ']';
   }

   private String text;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultTextLens.class);
}
