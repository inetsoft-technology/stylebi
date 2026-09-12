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
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class VSCrosstabRecommendation extends VSAbstractObjectRecommendation {
   public VSCrosstabRecommendation() {
      setType(VSRecommendType.CROSSTAB);
   }

   /**
    * Set recommended crosstabInfo.
    */
   public void setCrosstabInfo(VSCrosstabInfo crosstabInfo) {
      this.crosstabInfo = crosstabInfo;
   }

   /**
    * Set recommended crosstabInfo.
    */
   public VSCrosstabInfo getCrosstabInfo() {
      return this.crosstabInfo;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(crosstabInfo != null) {
         crosstabInfo.writeXML(writer);
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element enode = Tool.getChildNodeByTagName(elem, "VSCrosstabInfo");

      if(enode != null) {
         crosstabInfo = new VSCrosstabInfo();
         crosstabInfo.parseXML(enode);
      }
   }

   @JsonIgnore
   private VSCrosstabInfo crosstabInfo;
}
