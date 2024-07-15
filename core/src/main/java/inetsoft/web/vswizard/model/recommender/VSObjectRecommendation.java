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

import java.util.List;

/**
 * VSRecommendObject holding the recommended information created for viewsheet component
 * wizard after changing the binding fields, include visualization type and subtype, and
 * the concrete implementation classes should include the recommended binding information
 * for each visualization type.
 *
 * @version 13.2
 * @author  InetSoft Technology Corp.
 */
public interface VSObjectRecommendation {
   /**
    * Set recommender type of this object.
    */
   void setType(VSRecommendType type);

   /**
    * Get recommender type of this object.
    */
   VSRecommendType getType();

   /**
    * Set sub types of the recommender type.
    */
   void setSubTypes(List<VSSubType> types);

   /**
    * Get sub types of recommender type.
    */
   List<VSSubType> getSubTypes();

   /**
    * Get selected sub type index of recommender type.
    */
   int getSelectedIndex();

   /**
    * Set selected sub type index of the recommender type.
    */
   void setSelectedIndex(int index);
}
