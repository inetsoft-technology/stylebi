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

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class RefreshNewObjectPositionCommand implements TimeSensitiveCommand {
   public int getTop() {
      return top;
   }

   public void setTop(int top) {
      this.top = top;
   }

   public int getLeft() {
      return left;
   }

   public void setLeft(int left) {
      this.left = left;
   }

   public int getWidth() {
      return 300;
   }

   public int getHeight() {
      return 240;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      writer.print(" top=\"" + top + "\"");
      writer.print(" left=\"" + left + "\"");
   }

   @Override
   public void parseAttributes(Element tag) throws Exception {
      top = Integer.parseInt(Tool.getAttribute(tag, "top"));
      left = Integer.parseInt(Tool.getAttribute(tag, "left"));
   }

   private int top;
   private int left;
   private int width;
   private int height;
}
