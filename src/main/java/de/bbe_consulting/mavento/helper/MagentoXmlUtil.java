/**
 * Copyright 2011-2012 BBe Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.bbe_consulting.mavento.helper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Magento xml related helpers.
 * 
 * @author Erik Dannenberg
 */
public final class MagentoXmlUtil {

    /**
     * Private constructor, only static methods in this util class
     */
    private MagentoXmlUtil() {
    }

    /**
     * Reads a xml file and returns it as dom document.
     * 
     * @param fileName
     * @return Document
     * @throws MojoExecutionException
     */
    public static Document readXmlFile(String fileName)
            throws MojoExecutionException {

        final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true); // never forget this!
        DocumentBuilder builder = null;
        try {
            builder = domFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        Document domDocument = null;
        try {
            domDocument = builder.parse(fileName);
            domDocument.getDocumentElement().normalize();
        } catch (SAXException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        return domDocument;
    }

    /**
     * Writes string content to file.
     * 
     * @param content
     * @param fileName
     * @throws MojoExecutionException
     */
    public static void writeXmlFile(String content, String fileName)
            throws MojoExecutionException {

        final File targetFile = new File(fileName);
        FileWriter xmlWriter = null;
        try {
            xmlWriter = new FileWriter(targetFile);
            xmlWriter.write(content);
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing local.xml", e);
        } finally {
            if (xmlWriter != null) {
                try {
                    xmlWriter.close();
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Update xml entries in dom document.
     * 
     * @param config
     * @param payload
     */
    public static void updateXmlValues(Map<String, String> config, Document payload) {

        for (Map.Entry<String, String> configEntry : config.entrySet()) {
            // get <connection> nodes
            NodeList connectionNodes = payload.getElementsByTagName(configEntry.getKey());
            // update value for each node
            CDATASection cdata = null;
            for (int i = 0; i < connectionNodes.getLength(); i++) {
                Node n = connectionNodes.item(i);
                n.setNodeValue("");
                NodeList dbNodes = n.getChildNodes();
                while (dbNodes.getLength() > 0) {
                    n.removeChild(dbNodes.item(0));
                }
                cdata = payload.createCDATASection(configEntry.getValue());
                n.appendChild(cdata);
            }
        }
    }

    /**
     * Extract data from an etc/modules/config.xml document.
     * 
     * @param payload
     * @return Map<String, String>
     * @throws MojoExecutionException
     */
    public static Map<String, String> getEtcModulesValues(Document payload)
            throws MojoExecutionException {

        NodeList moduleValueNodes = null;
        final NodeList configNodes = payload.getElementsByTagName("modules");
        final NodeList moduleNodes = configNodes.item(0).getChildNodes();

        final Map<String, String> result = new HashMap<String, String>();
        for (int j = 0; j < moduleNodes.getLength(); j++) {
            String nodeName = moduleNodes.item(j).getNodeName();
            if (nodeName.contains("_")) {
                moduleValueNodes = moduleNodes.item(j).getChildNodes();
                String[] moduleName = nodeName.split("_", 2);
                result.put("nameSpace", moduleName[0]);
                result.put("moduleName", moduleName[1]);
            }
        }

        if (moduleValueNodes == null || moduleValueNodes.getLength() == 0) {
            throw new MojoExecutionException("Could not find config nodes in a modules.xml file.");
        }

        for (int j = 0; j < moduleValueNodes.getLength(); j++) {
            Node valueNode = moduleValueNodes.item(j);
            switch (valueNode.getNodeName()) {
            case "active":
                result.put("active", getNodeValue(valueNode));
                break;
            case "codePool":
                result.put("codePool", getNodeValue(valueNode));
                break;
            }
        }
        return result;
    }

    /**
     * Get magento install date from local.xml
     * 
     * @param payload
     * @return String install date
     * @throws MojoExecutionException
     */
    public static String getMagentoInstallData(Document payload)
            throws MojoExecutionException {

        NodeList installNodes = payload.getElementsByTagName("install");
        NodeList valueNodes = installNodes.item(0).getChildNodes();

        String installDate = null;
        for (int j = 0; j < valueNodes.getLength(); j++) {
            Node valueNode = valueNodes.item(j);
            switch (valueNode.getNodeName()) {
            case "date":
                installDate = getNodeValue(valueNode);
            }
        }

        if (installDate == null || installDate.isEmpty()) {
            throw new MojoExecutionException("Could parse install date in /app/etc/local.xml");
        }

        return installDate;
    }

    /**
     * Extract magento module version from config.xml
     * 
     * @param payload
     * @param moduleName
     * @return String magento module version
     * @throws MojoExecutionException
     */
    public static String getModuleVersion(Document payload, String moduleName)
            throws MojoExecutionException {

        String moduleVersion = "undefined";
        final NodeList configNodes = payload.getElementsByTagName("modules");
        if (configNodes.getLength() == 0) {
            return moduleVersion;
        }
        final NodeList moduleNodes = configNodes.item(0).getChildNodes();
        NodeList moduleValueNodes = null;

        for (int j = 0; j < moduleNodes.getLength(); j++) {
            final String nodeName = moduleNodes.item(j).getNodeName();
            if (nodeName.equals(moduleName)) {
                moduleValueNodes = moduleNodes.item(j).getChildNodes();
            }
        }

        if (moduleValueNodes == null || moduleValueNodes.getLength() == 0) {
            throw new MojoExecutionException("Could not find config nodes for module: " + moduleName);
        }

        for (int j = 0; j < moduleValueNodes.getLength(); j++) {
            Node valueNode = moduleValueNodes.item(j);
            switch (valueNode.getNodeName()) {
            case "version":
                moduleVersion = getNodeValue(valueNode);
            }
        }

        if (moduleVersion == null || moduleName.isEmpty()) {
            moduleVersion = "undefined";
        }
        return moduleVersion;
    }

    /**
     * Get magento db connection details from local.xml
     * @param payload
     * @return Map<String, String>
     * @throws MojoExecutionException
     */
    public static Map<String, String> getDbValues(Document payload)
            throws MojoExecutionException {

        String magentoDbHost = null;
        String magentoDbPort = "3306";
        String magentoDbUser = null;
        String magentoDbPasswd = null;
        String magentoDbName = null;

        NodeList dbNodes = null;
        final NodeList setupNodes = payload.getElementsByTagName("default_setup");
        final NodeList connectionNodes = setupNodes.item(0).getChildNodes();
        // get connection nodes
        for (int j = 0; j < connectionNodes.getLength(); j++) {
            if (connectionNodes.item(j).getNodeName().equals("connection")) {
                dbNodes = connectionNodes.item(j).getChildNodes();
            }
        }
        if (dbNodes == null) {
            throw new MojoExecutionException("Error parsing /app/etc/local.xml");
        }
        // parse db values
        for (int j = 0; j < dbNodes.getLength(); j++) {
            Node dbNode = dbNodes.item(j);
            switch (dbNode.getNodeName()) {
            case "host":
                magentoDbHost = getNodeValue(dbNode);
                if (magentoDbHost.contains(":")) {
                    final String[] s = magentoDbHost.split(":", 2);
                    magentoDbHost = s[0];
                    magentoDbPort = s[1];
                }
                break;
            case "username":
                magentoDbUser = getNodeValue(dbNode);
                break;
            case "password":
                magentoDbPasswd = getNodeValue(dbNode);
                break;
            case "dbname":
                magentoDbName = getNodeValue(dbNode);
                break;
            }
        }

        if (magentoDbHost == null || magentoDbUser == null || magentoDbName == null) {
            throw new MojoExecutionException("Could not find db settings in /app/etc/local.xml");
        }

        // TODO: parse host for possible port
        
        final Map<String, String> result = new HashMap<String, String>();
        result.put("host", magentoDbHost);
        result.put("port", magentoDbPort);
        result.put("user", magentoDbUser);
        result.put("password", magentoDbPasswd);
        result.put("dbname", magentoDbName);
        return result;
    }

    /**
     * Update magento db connection details in local.xml
     * 
     * @param magentoDbHost
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbName
     * @param payload
     */
    public static void updateDbValues(String magentoDbHost, String magentoDbUser,
            String magentoDbPasswd, String magentoDbName, Document payload) {

        // get <connection> nodes
        final NodeList connectionNodes = payload.getElementsByTagName("connection");

        // update database settings for each <connection> block
        for (int i = 0; i < connectionNodes.getLength(); i++) {
            Node n = connectionNodes.item(i);
            NodeList dbNodes = n.getChildNodes();
            for (int j = 0; j < dbNodes.getLength(); j++) {
                Node dbNode = dbNodes.item(j);
                NodeList cdataNodes = dbNode.getChildNodes();
                CDATASection cdata = null;
                switch (dbNode.getNodeName()) {
                case "host":
                    dbNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        dbNode.removeChild(cdataNodes.item(0));
                    }
                    cdata = payload.createCDATASection(magentoDbHost);
                    dbNodes.item(j).appendChild(cdata);
                    break;
                case "username":
                    dbNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        dbNode.removeChild(cdataNodes.item(0));
                    }
                    cdata = payload.createCDATASection(magentoDbUser);
                    dbNodes.item(j).appendChild(cdata);
                    break;
                case "password":
                    dbNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        dbNode.removeChild(cdataNodes.item(0));
                    }
                    if (magentoDbPasswd == null) {
                        magentoDbPasswd = "";
                    }
                    cdata = payload.createCDATASection(magentoDbPasswd);
                    dbNodes.item(j).appendChild(cdata);
                    break;
                case "dbname":
                    dbNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        dbNode.removeChild(cdataNodes.item(0));
                    }
                    cdata = payload.createCDATASection(magentoDbName);
                    dbNodes.item(j).appendChild(cdata);
                    break;
                }
            }
        }
    }

    /**
     * Update base url details in local.xml.phpunit
     * 
     * @param magentoDbHost
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbName
     * @param payload
     */
    public static void updateBaseUrls(String baseUrlUnsecure, String baseUrlSecure,
            boolean seoRewrites, Document payload) {

        // get <secure> nodes
        final NodeList secureNodes = payload.getElementsByTagName("secure");
        for (int i = 0; i < secureNodes.getLength(); i++) {
            NodeList urlNodes = secureNodes.item(i).getChildNodes();
            for (int j = 0; j < urlNodes.getLength(); j++) {
                Node urlNode = urlNodes.item(j);
                NodeList cdataNodes = urlNode.getChildNodes();
                CDATASection cdata = null;
                switch (urlNode.getNodeName()) {
                case "base_url":
                    urlNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        urlNode.removeChild(cdataNodes.item(0));
                    }
                    cdata = payload.createCDATASection(MagentoUtil.validateBaseUrl(baseUrlSecure, true));
                    urlNodes.item(j).appendChild(cdata);
                    break;
                }
            }
        }

        // get <unsecure> nodes
        final NodeList unsecureNodes = payload.getElementsByTagName("unsecure");
        for (int i = 0; i < unsecureNodes.getLength(); i++) {
            NodeList urlNodes = unsecureNodes.item(i).getChildNodes();
            for (int j = 0; j < urlNodes.getLength(); j++) {
                Node urlNode = urlNodes.item(j);
                NodeList cdataNodes = urlNode.getChildNodes();
                CDATASection cdata = null;
                switch (urlNode.getNodeName()) {
                case "base_url":
                    urlNodes.item(j).setNodeValue("");
                    while (cdataNodes.getLength() > 0) {
                        urlNode.removeChild(cdataNodes.item(0));
                    }
                    cdata = payload.createCDATASection(MagentoUtil.validateBaseUrl(baseUrlUnsecure, false));
                    urlNodes.item(j).appendChild(cdata);
                    break;
                }
            }
        }
    }

    /**
     * Returns value/cdata from xml node.
     * @param node
     * @return String
     */
    public static String getNodeValue(Node node) {

        String value = node.getNodeValue();
        if (value == null) {
            Node nChild = node.getFirstChild();
            if (nChild instanceof CharacterData) {
                CharacterData cd = (CharacterData) nChild;
                value = cd.getData();
            }
        }
        return value;
    }

    /**
     * Returns dom document as string.
     * 
     * @param payload
     * @return String
     * @throws TransformerException
     */
    public static String transformXmlToString(Document payload)
            throws TransformerException {

        final TransformerFactory transFactory = TransformerFactory.newInstance();
        final Transformer transformer = transFactory.newTransformer();
        final StringWriter buffer = new StringWriter();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.transform(new DOMSource(payload), new StreamResult(buffer));
        return buffer.toString();
    }

}
