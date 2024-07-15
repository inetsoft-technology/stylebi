/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.drm;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class AttributeRefModel extends AbstractDataRefModel {
   public AttributeRefModel() {
   }

   public AttributeRefModel(AttributeRef dataRef) {
      super(dataRef);

      this.caption = dataRef.getCaption();
   }

   /**
    * Get the caption.
    * @return the caption.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * Set the caption.
    * @param caption the specified caption.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      AttributeRef ref = new AttributeRef(this.getEntity(), this.getAttribute());
      ref.setRefType(this.getRefType());
      ref.setCaption(this.getCaption());

      return ref;
   }

   private String caption;

   @Component
   public static final class AttributeRefModelFactory
      extends DataRefModelFactory<AttributeRef, AttributeRefModel>
   {
      @Override
      public Class<AttributeRef> getDataRefClass() {
         return AttributeRef.class;
      }

      @Override
      public AttributeRefModel createDataRefModel(AttributeRef dataRef) {
         return new AttributeRefModel(dataRef);
      }
   }
}
