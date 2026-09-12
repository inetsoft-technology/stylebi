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
package inetsoft.web.vswizard.model.recommender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.uql.ColumnSelection;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class VSTableRecommendation extends VSAbstractObjectRecommendation {
   public VSTableRecommendation() {
      setType(VSRecommendType.TABLE);
   }

   /**
    * Set columnselection for the recommended table.
    */
   public void setColumns(ColumnSelection columns) {
      this.columns = columns;
   }

   /**
    * Get columnselection for the recommended table.
    */
   public ColumnSelection getColumns() {
      return this.columns;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(columns != null) {
         columns.writeXML(writer);
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element item = Tool.getChildNodeByTagName(elem, "ColumnSelection");

      if(item != null) {
         columns = new ColumnSelection();
         columns.parseXML(item);
      }
   }

   @JsonIgnore
   private ColumnSelection columns;
}
