<?xml version="1.0"?>                   
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema"> 
<xsl:output method="html"/>
<xsl:template match="/">
<html>
  <head>
    <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
    <title>Strategy Documentation</title>
    <style type="text/css">
    
      body{
        background-color:white;
        font-family: Arial, Sans Serif, Verdana;
        font-size: 12px;
        color:#444444;
        text-align:center;
      }
      
      .tSubtitle{
        width:95%;
        margin: 0 auto; 
        border:0 solid black; 
        
      }
      
      .tSubtitle th{
        background-color:#015EAE;
        font-weight:bold;
        font-size:1.2em;
        color:white;
      }
      
      .tTitle{
        width:95%;
        margin: 0 auto; 
        border:0 solid black; 
        
      }
      
      .tTitle th{
        background-color:#015EAE;
        font-weight:bold;
        font-size:1.5em;
        color:white;
      }
      
      .tContent{
        width:95%;
        margin:0 auto; 
      }
      
      .tContent th{
         width:200px;
         font-weight:bold;
         color:#015EAE;
         border:1px solid #015EAE; 
      }
      
      .tContent td{
        border:1px solid #015EAE; 
      }
      
      .R{
        text-align:right;
      }
      
      .C{
        text-align:center;
      }
      
      .L{
        text-align:left;
      }
      
      .W200{
          width:200px;
      }
      
    </style>
  </head>
  <body>
      <table class="tTitle">
        <tr>
          <td class="W200 L"><img src="experian.png"/></td>
          <th>Experian NBSM. <xsl:value-of select="/xs:schema/xs:annotation/xs:appinfo"/> Strategy definition</th>
        </tr>
      </table>
      <br/>
      <table class="tContent">
        <tr>
          <th class="W200 L"> Creation date </th>
          <td> 2012-06-07</td>
        </tr>
        <tr>
          <th class="W200 L"> Strategy file </th>
          <td> <xsl:value-of select="/xs:schema/xs:annotation/xs:appinfo"/>.ser</td>
        </tr>
        <tr>
          <th class="W200 L"> WSDL </th>
          <td> <a target="_NEW">
                  <xsl:attribute name="href"><xsl:value-of select="/xs:schema/xs:annotation/xs:appinfo"/>.wsdl</xsl:attribute>
                  Open WSDL
                </a></td>
        </tr>
        <tr>
          <th class="W200 L"> Description </th>
          <td> 
          <!-- ADD DESCRIPTION HERE -->
          
          Replace this text
          
          <!-- END DESCRIPTION HERE -->
          </td>
        </tr>
      </table>
      
      <xsl:apply-templates/>      
      <br/>
      <table class="tSubtitle">
        <tr>
          <th>MESSAGE EXAMPLE</th>
        </tr>
      </table>
      
      <table class="tContent">
        <tr>
          <td>
            <a target="_NEW">
                  <xsl:attribute name="href"><xsl:value-of select="/xs:schema/xs:annotation/xs:appinfo"/>_sample.xml</xsl:attribute>
                  Open XML sample file</a>
          </td>
        </tr>
      </table>
      
      
  </body>
</html>

</xsl:template>

<xsl:template match="xs:schema/xs:element[@name='DAXMLDocument']/xs:complexType/xs:all/xs:element">
      <br/>
      <table class="tSubtitle">
        <tr>
          <th>DICTIONARY:<xsl:value-of select="@name"/></th>
        </tr>
      </table>
      <table class="tContent">
          <xsl:for-each select="xs:complexType/xs:all/xs:element">
            <xsl:choose>
            <xsl:when test="@type">
              <tr>
                <th class="W200 L"><xsl:value-of select="@name"/></th>
                <td colspan="2"><xsl:value-of select="@type"/></td>
                <td class="W200">
                  <xsl:choose><xsl:when test="@minOccurs='0'">Optional</xsl:when><xsl:otherwise>&#160;</xsl:otherwise></xsl:choose>
                </td>
              </tr>
            </xsl:when>
            <xsl:otherwise>  
              <tr>
                <th class="W200 L"><xsl:value-of select="@name"/></th>
                <td colspan="2">Array</td>
                <td class="W200">
                  <xsl:choose><xsl:when test="@minOccurs='0'">Optional</xsl:when><xsl:otherwise>&#160;</xsl:otherwise></xsl:choose>
                </td>
              </tr>
              <xsl:for-each select="xs:complexType/xs:all/xs:element">
                  <xsl:if test="@name!='data_type'">
                  <tr>
                    <th class="W200 L">&#160;</th>
                    <th class="W200 L"><xsl:value-of select="@name"/></th>
                    <td><xsl:value-of select="@type"/></td>
                    <td class="W200">
                      <xsl:choose><xsl:when test="@minOccurs='0'">Optional</xsl:when><xsl:otherwise>&#160;</xsl:otherwise></xsl:choose>
                    </td>
                  </tr>
                  </xsl:if>
              </xsl:for-each>
            </xsl:otherwise>
          </xsl:choose>
          </xsl:for-each>
      </table>
</xsl:template>

<xsl:template match="xs:annotation">
</xsl:template>

<!--<xsl:template match="xs:complexType/xs:all/xs:element">
      <xsl:value-of select="@name"/>
      
</xsl:template> -->



</xsl:stylesheet> 
