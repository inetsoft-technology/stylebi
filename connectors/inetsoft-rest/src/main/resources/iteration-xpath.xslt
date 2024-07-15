<!--
  ~ This file is part of StyleBI.
  ~ Copyright (C) 2024  InetSoft Technology
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
  -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xalan="http://xml.apache.org/xalan"
                xmlns:is="xalan://inetsoft.uql.rest.xml.xslt.TransformFunctions">

  <xsl:output method="text"/>
  <xsl:strip-space elements="*" />

  <!-- document parser -->
  <xsl:param name="parser"/>
  <xsl:param name="transformer"/>

  <!-- The spec disallows multiple templates of the same priority matching the same node.
       Need to use mode attribute to invoke multiple templates. -->
  <xsl:template match="/">
    <xsl:apply-templates mode="xpath"/>
    <xsl:apply-templates mode="pageOffset"/>
    <xsl:apply-templates mode="hasNext"/>
  </xsl:template>

  <!-- xpath variable is set externally to the selection xpath from the query -->
  <xsl:template match="$xpath" mode="xpath">
    <xsl:value-of select="is:addNode(., $parser)"/>
  </xsl:template>

  <!-- pageOffsetXpath variable is set externally -->
  <xsl:template match="$pageOffsetXpath" mode="pageOffset">
    <xsl:value-of select="is:setPageOffsetParam(., $transformer)"/>
  </xsl:template>

  <!-- hasNextXpath variable is set externally -->
  <xsl:template match="$hasNextXpath" mode="hasNext">
    <xsl:value-of select="is:setHasNext(., $transformer)"/>
  </xsl:template>
</xsl:stylesheet>
