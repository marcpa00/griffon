/* 
 * Copyright 2004-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.documentation

import org.w3c.dom.Document
import org.xhtmlrenderer.pdf.ITextRenderer

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Copy of grails.doc.PdfBuilder.<p>
 * Subclassed in order to tweak some values.
 */
class PdfBuilder {
    static void build(String baseDir, String griffonHome) {
        baseDir = new File(baseDir).canonicalPath

        File htmlFile = new File("${baseDir}/manual/guide/single.html")
        File outputFile = new File("${baseDir}/manual/guide/single.pdf")
        String urlBase = "file://${griffonHome}/src/griffon/docs/style"

        String xml = createXml(htmlFile, "${baseDir}/manual")
        createPdf(xml, outputFile, urlBase)
    }

    private static String createXml(File htmlFile, String base) {
        String xml = htmlFile.text

        // tweak main css so it doesn't get ignored
        xml = xml.replace('media="screen"', 'media="print"')

        // fix inner anchors
        xml = xml.replaceAll('<a href="../guide/single.html', '<a href="')

//        xml = xml.replaceAll('href="../css/', "href=\"file://${base}/css/")
        xml = xml.replaceAll('src="../img/', "src=\"file://${base}/img/")

        // convert tabs to spaces otherwise they only take up one space
        xml = xml.replaceAll('\t', '    ')
        xml
    }

    private static void createPdf(String xml, File outputFile, String urlBase) {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()))

        ITextRenderer renderer = new ITextRenderer()
        try {
            renderer.setDocument(doc, urlBase + '/dummy')
        } catch(x) {
            // ignore
        }

        OutputStream outputStream
        try {
            outputStream = new FileOutputStream(outputFile)
            renderer.layout()
            renderer.createPDF(outputStream)
        } catch(x) {
            // ignore
        } finally {
            try {
                outputStream?.close()
            } catch(x) {
                // ignore
            }
        }
    }
}
