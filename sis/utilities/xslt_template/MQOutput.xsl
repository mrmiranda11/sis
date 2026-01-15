<?xml version="1.0" encoding="ISO-8859-1"?>
<!-- Edited by XMLSpy® -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="/">
  <html>
  <body>
  <h2>Transact Result</h2>
    Code: <xsl:value-of select="result/returnCode"/><br/>
    Message: <xsl:value-of select="result/returnmsg"/>
  </body>
  </html>
</xsl:template>
</xsl:stylesheet>

