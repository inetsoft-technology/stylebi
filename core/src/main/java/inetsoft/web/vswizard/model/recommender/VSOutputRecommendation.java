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
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class VSOutputRecommendation extends VSAbstractObjectRecommendation {
   /**
    * Set dataref for the recommended text.
    */
   public void setDataRef(DataRef dataRef) {
      this.dataRef = dataRef;
   }

   /**
    * Get dataref for the recommended text.
    */
   public DataRef getDataRef() {
      return this.dataRef;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

       if(dataRef != null) {
          dataRef.writeXML(writer);
       }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element item = Tool.getChildNodeByTagName(elem, "dataRef");

      if(item != null) {
         dataRef = AbstractDataRef.createDataRef(item);
      }
   }

   @JsonIgnore
   private DataRef dataRef;
}
