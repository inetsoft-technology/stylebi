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
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class VSFilterRecommendation extends VSAbstractObjectRecommendation {
   public VSFilterRecommendation() {
      setType(VSRecommendType.FILTER);
   }

   /**
    * Set dataref for the recommended text.
    */
   public void setDataRefs(DataRef[] refs) {
      this.refs = refs;
   }

   /**
    * Get dataref for the recommended text.
    */
   public DataRef[] getDataRefs() {
      return this.refs;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(refs == null || refs.length == 0) {
         return;
      }

      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];
         ref.writeXML(writer);
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      NodeList list = Tool.getChildNodesByTagName(elem, "dataRef");
      refs = new DataRef[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item != null) {
            refs[i] = AbstractDataRef.createDataRef(item);
         }
      }
   }

   @JsonIgnore
   private DataRef[] refs;
}
