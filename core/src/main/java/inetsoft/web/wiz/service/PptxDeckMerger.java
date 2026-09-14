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
   record ChartSlide(String title, String caption, byte[] singleSlideDeckBytes, boolean failed,
                     String insightsMarkdown)
   {
      public ChartSlide(String title, String caption, byte[] singleSlideDeckBytes, boolean failed) {
         this(title, caption, singleSlideDeckBytes, failed, null);
      }
   }

   /**
    * @param title the board name. Rendered as a compact header sharing the FIRST chart's own
    *              slide (bug-76110 round 3) — mirroring the sibling PDF export's convention of
    *              sharing page 1's header with the first chart — rather than a slide of its own.
    *              Falls back to a standalone title/recap slide only when {@code slides} is empty
    *              (no chart slide exists to share with).
    * @param recap optional findings-recap paragraph, rendered under the title in that same
    *              shared header. Unlike the empty-{@code slides} fallback's recap box, the shared
    *              header's recap is stripped to plain text and shrunk/truncated to fit its small
    *              fixed budget — a PPTX slide is a hard container with no PDF-style continuous-
    *              flow overflow, so there isn't room for an open-ended rendered markdown block.
    * @param slides one entry per kept chart, in display order. A non-failed entry's
    *               singleSlideDeckBytes is a single-slide .pptx produced by exporting that
    *               chart's own saved visualization via VSExportService/PPTVSExporter
    *               (FileFormatInfo.EXPORT_TYPE_POWERPOINT), pre-sized to occupy only the top
    *               portion of the slide so its own insights have room below it (and, for the
    *               first entry, room for the shared title/recap header above it too). A failed
    *               entry's caption/title are still used (for the placeholder slide's text);
    *               singleSlideDeckBytes may be null/empty and must not be read. insightsMarkdown
    *               (either entry kind), if non-blank, is packed onto the chart's own slide first
    *               (below the imported picture) as far as it fits, then spills into additional
    *               "(cont'd)" insights-only slide(s) immediately after — a failed entry has no
    *               imported picture and so no reserved region, and its insights always start on
    *               their own dedicated slide.
    * @return a merged multi-slide .pptx: for each chart one slide (imported content + caption, or
    *         a text-only "failed to render" placeholder — whichever slide the first chart lands
    *         on also carries the board's title/recap header) with that chart's own insights
    *         sharing the same slide when they fit, optionally followed by additional insights-only
    *         "(cont'd)" slide(s) for whatever does not fit. If {@code slides} is empty, a single
    *         standalone title/recap slide instead.
    */
   byte[] mergeSlides(String title, String recap, java.util.List<ChartSlide> slides) throws Exception;
}
