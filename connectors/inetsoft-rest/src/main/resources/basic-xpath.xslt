<?xml version="1.0" encoding="utf-8"?>
<!--

    inetsoft-rest - StyleBI is a business intelligence web application.
    Copyright © 2024 InetSoft Technology (info@inetsoft.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.

-->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:is="xalan://inetsoft.uql.rest.xml.xslt.TransformFunctions">
  <!-- add additional namespace mappings from source document -->

  <xsl:output method="text"/>
  <xsl:strip-space elements="*" />

  <!-- document parser -->
  <xsl:param name="parser"/>

  <!-- xpath variable is set externally to the selection xpath from the query -->
  <xsl:template match ="$xpath">
    <xsl:value-of select="is:addNode(., $parser)"/>
  </xsl:template>
</xsl:stylesheet>
