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
import inetsoft.web.vswizard.model.recommender.VSRecommendationModel;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class RefreshDescriptionCommand implements TimeSensitiveCommand {
   public RefreshDescriptionCommand() {}

   public RefreshDescriptionCommand(String description) {
      this.description = description;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      if(description != null) {
         writer.print(" description=\"" + description + "\"");
      }
   }

   @Override
   public void parseAttributes(Element elem) throws Exception {
      description = Tool.getAttribute(elem, "description");
   }

   private String description;
}