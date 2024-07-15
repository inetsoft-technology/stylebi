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
package inetsoft.report.io.viewsheet.ppt;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulate the slideshow, to implement PowerPointContext.
 *
 * @version 8.5, 8/7/2006
 * @author InetSoft Technology Corp
 */
public class PPTExporter implements PPTContext {
   /**
    * Get slide show.
    * If other people got slideshow,
    * they can made uncontrollerable change to it.
    * @return the specified SlideShow created in setUp method.
    */
   @Override
   public XMLSlideShow getSlideShow() {
      return show;
   }

   /**
    * Create a slide show.
    */
   public void setUp() {
      try {
         show = new XMLSlideShow();
      }
      catch(Exception e) {
         LOG.error("Failed to initialize presentation", e);
      }
   }

   private XMLSlideShow show = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(PPTExporter.class);
}
