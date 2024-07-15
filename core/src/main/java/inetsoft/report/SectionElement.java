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
package inetsoft.report;

import java.text.Format;

/**
 * A section is composed of a header, footer, and section content. Section
 * content may be a simple band, or nested with header and footer. A 
 * section is normally used to present a table or grouping. Section band
 * is repeated for each table row.
 * <p>
 * Since each element in a section band is positioned at a fixed location,
 * section can also be used as a container. This may be useful for reports
 * or section of reports where elements positions must be accurately 
 * controlled.
 */
public interface SectionElement extends ReportElement {
   /**
    * Get the section lens.
    */
   public SectionLens getSection();
   
   /**
    * Set the section lens. A section lens specifies the elements in a
    * section, and their properties and binding. It does not contain
    * the actual data.
    */
   public void setSection(SectionLens section);
   
   /**
    * Get the table lens.
    */
   public TableLens getTable();
   
   /**
    * Set the table lens. The table lens is used to get data to be presented
    * by section fields. The table can be a simple table lens, or a grouped
    * table. If a grouped table is used,  the section lens must have the
    * same nesting level as the grouping columns.
    */
   public void setTable(TableLens table);
   
   /**
    * Find an element in the section with the specified ID.
    * @param id element id.
    */
   public ReportElement getElement(String id);
   
   /**
    * Find all elements in the section.
    */
   public ReportElement[] getElements();

   /**
    * Find the report.
    */
   public ReportSheet getReport();

}

