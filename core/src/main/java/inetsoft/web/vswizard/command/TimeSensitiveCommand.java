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
package inetsoft.web.vswizard.command;

import inetsoft.util.XMLSerializable;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

public interface TimeSensitiveCommand extends ViewsheetCommand, Serializable, XMLSerializable {
   default long getTimestamp() {
      return System.currentTimeMillis();
   }

   default void setTimestamp(long timestamp) {
   }

   default boolean isWizard() {
      return true;
   }

   @Override
   default void writeXML(PrintWriter writer) {
      writer.print("<timeSensitiveCommand class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</vsObjectRecommendation>");
   }

   default void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   default void writeContents(PrintWriter writer) {
      // do nothing
   }

   @Override
   default void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   default void parseContents(Element elem) throws Exception {
      // do nothing
   }

   default void parseAttributes(Element elem) throws Exception {
      // do nothing
   }
}
