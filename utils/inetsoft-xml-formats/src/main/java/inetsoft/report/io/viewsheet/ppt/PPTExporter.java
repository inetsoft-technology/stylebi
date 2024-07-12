/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
