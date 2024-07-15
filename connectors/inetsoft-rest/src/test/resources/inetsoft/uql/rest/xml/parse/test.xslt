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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"   xmlns:is="xalan://inetsoft.uql.rest.xml.xslt.TransformFunctions">
    <xsl:output omit-xml-declaration="yes" indent="yes"/>

    <xsl:param name="pP1" select="2"/>
    <xsl:param name="pP2" select="3"/>
    <xsl:param name="pP3" select="5"/>

    <xsl:variable name="vPass1">
        <xsl:apply-templates mode="pass1">
            <xsl:with-param name="pP1" select="$pP1"/>
        </xsl:apply-templates>
    </xsl:variable>

    <xsl:variable name="vPass2">
        <xsl:apply-templates mode="pass2">
            <xsl:with-param name="pP2" select="$pP2"/>
        </xsl:apply-templates>
    </xsl:variable>

    <xsl:variable name="vPass3">
        <xsl:apply-templates mode="pass3">
            <xsl:with-param name="pP3" select="$pP3"/>
        </xsl:apply-templates>
    </xsl:variable>

    <xsl:template match="/">
        <xsl:value-of select="$vPass1 + $vPass2 + $vPass3"/>
    </xsl:template>

    <xsl:template match="/*" mode="pass1">
        <xsl:value-of select="sum(*[. mod $pP1 = 0])"/>
        <xsl:value-of select="is:log(.)"/>
    </xsl:template>

    <xsl:template match="/*" mode="pass2">
        <xsl:value-of select="sum(*[. mod $pP2 = 0])"/>
        <xsl:value-of select="is:log(*[.])"/>
    </xsl:template>

    <xsl:template match="/*" mode="pass3">
        <xsl:value-of select="sum(*[. mod $pP3 = 0])"/>
    </xsl:template>
</xsl:stylesheet>
