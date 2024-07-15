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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class ScheduleTransformListener implements TransformListener {
   @Override
   public void transform(Document doc, String cname) {
      transform(doc, cname, null, null);
   }

   @Override
   public void transform(Document doc, String cname, String sourceName, TransformListener trans) {
      try {
         TransformerManager transf = TransformerManager.getManager(TransformerManager.SCHEDULE);
         transf.transform(doc);
      }
      catch(Exception e) {
         LOG.error("Failed to transform " + TransformerManager.SCHEDULE + " XML", e);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleTransformListener.class);
}
