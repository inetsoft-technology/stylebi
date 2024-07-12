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

/**
 * The context for using the powerpoint slide show.
 *
 * @version 8.5, 8/7/2006
 * @author InetSoft Technology Corp
 */
interface PPTContext {
   /**
    * Get the powerpoint slide show.
    */
   XMLSlideShow getSlideShow();
}
