/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.service;

public interface PptxDeckMerger {
   record ChartSlide(String title, String caption, byte[] singleSlideDeckBytes, boolean failed) {}

   /**
    * @param title the board name (rendered on a leading title/recap slide)
    * @param recap optional findings-recap paragraph, rendered under the title on the same slide
    * @param slides one entry per kept chart, in display order. A non-failed entry's
    *               singleSlideDeckBytes is a single-slide .pptx produced by exporting that
    *               chart's own saved visualization via VSExportService/PPTVSExporter
    *               (FileFormatInfo.EXPORT_TYPE_POWERPOINT). A failed entry's caption/title are
    *               still used (for the placeholder slide's text); singleSlideDeckBytes may be
    *               null/empty and must not be read.
    * @return a merged multi-slide .pptx: one title/recap slide, then one slide per chart
    *         (imported content + caption, or a text-only "failed to render" placeholder)
    */
   byte[] mergeSlides(String title, String recap, java.util.List<ChartSlide> slides) throws Exception;
}
