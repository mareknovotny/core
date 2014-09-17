/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.environment.deployment.discovery;

import static org.jboss.weld.environment.util.URLUtils.JAR_URL_SEPARATOR;
import static org.jboss.weld.environment.util.URLUtils.PROCOTOL_FILE;
import static org.jboss.weld.environment.util.URLUtils.PROCOTOL_HTTP;
import static org.jboss.weld.environment.util.URLUtils.PROCOTOL_HTTPS;
import static org.jboss.weld.environment.util.URLUtils.PROCOTOL_JAR;
import static org.jboss.weld.environment.util.URLUtils.PROTOCOL_FILE_PART;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.jboss.logging.Logger;
import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.bootstrap.spi.BeanDiscoveryMode;
import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.environment.deployment.AbstractWeldDeployment;
import org.jboss.weld.environment.deployment.WeldResourceLoader;
import org.jboss.weld.environment.logging.CommonLogger;
import org.jboss.weld.resources.spi.ResourceLoader;

/**
 * Scans the classpath and tries to process all "META-INF/beans.xml" resources.
 *
 * @author Martin Kouba
 */
public class DefaultBeanArchiveScanner implements BeanArchiveScanner {

    private static final Logger log = Logger.getLogger(DefaultBeanArchiveScanner.class);

    protected final ResourceLoader resourceLoader;

    protected final Bootstrap bootstrap;

    /**
     *
     * @param resourceLoader
     * @param bootstrap
     */
    public DefaultBeanArchiveScanner(ResourceLoader resourceLoader, Bootstrap bootstrap) {
        this.resourceLoader = resourceLoader;
        this.bootstrap = bootstrap;
    }

    @Override
    public Map<BeansXml, String> scan() {
        Map<BeansXml, String> beansXmlMap = new HashMap<BeansXml, String>();
        // META-INF/beans.xml
        String[] resources = AbstractWeldDeployment.RESOURCES;

        // Find all beans.xml files
        for (String resourceName : resources) {
            for (URL beansXmlUrl : resourceLoader.getResources(resourceName)) {
                BeansXml beansXml = bootstrap.parse(beansXmlUrl);
                if (accept(beansXml)) {
                    beansXmlMap.put(beansXml, getBeanArchiveReference(beansXmlUrl));
                }
            }
        }
        return beansXmlMap;
    }

    protected boolean accept(BeansXml beansXml) {
        return !BeanDiscoveryMode.NONE.equals(beansXml.getBeanDiscoveryMode());
    }

    /**
     * @param url
     * @return
     * @throws URISyntaxException
     */
    protected String getBeanArchiveReference(URL url) {

        String ref = null;
        URI uri = null;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            CommonLogger.LOG.couldNotReadResource(url, e);
        }

        if(PROCOTOL_FILE.equals(url.getProtocol())) {
            // Adapt file URL, e.g. "file:///home/weld/META-INF/beans.xml" becomes "/home/weld"
            ref = new File(uri.getSchemeSpecificPart()).getParentFile().getParent();

        } else if(PROCOTOL_JAR.equals(url.getProtocol())) {
            // Adapt JAR file URL, e.g. "jar:file:/home/duke/duke.jar!/META-INF/beans.xml" becomes "/home/duke/duke.jar"

            // The decoded part without protocol part, i.e. without "jar:"
            ref = uri.getSchemeSpecificPart();

            if(ref.lastIndexOf(JAR_URL_SEPARATOR) > 0) {
                ref = ref.substring(0, ref.lastIndexOf(JAR_URL_SEPARATOR));
            }
            return getBeanArchiveReferenceForJar(ref, url);
        } else {
            log.warnv("Unable to adapt URL: {0}, using its external form instead", url);
            ref = url.toExternalForm();
        }
        log.debugv("Resolved bean archive reference: {0} for URL: {1}", ref, url);
        return ref;
    }

    protected String getBeanArchiveReferenceForJar(String path, URL fallback) {
        // jar:file:
        if (path.startsWith(PROTOCOL_FILE_PART)) {
            return path.substring(PROTOCOL_FILE_PART.length());
        }
        // jar:http:
        if (path.startsWith(PROCOTOL_HTTP) || path.startsWith(PROCOTOL_HTTPS)) {
            // WebStart support: get path to local cached copy of remote JAR file
            // Class loader should be an instance of com.sun.jnlp.JNLPClassLoader
            ClassLoader jnlpClassLoader = WeldResourceLoader.getClassLoader();
            try {
                // Try to call com.sun.jnlp.JNLPClassLoader#getJarFile(URL) from JDK 6
                Method m = jnlpClassLoader.getClass().getMethod("getJarFile", URL.class);
                // returns a reference to the local cached copy of the JAR
                ZipFile jarFile = (ZipFile) m.invoke(jnlpClassLoader, new URL(path));
                return jarFile.getName();
            } catch (NoSuchMethodException nsme) {
                CommonLogger.LOG.unexpectedClassLoader(nsme);
            } catch (IllegalArgumentException iarge) {
                CommonLogger.LOG.unexpectedClassLoader(iarge);
            } catch (InvocationTargetException ite) {
                CommonLogger.LOG.jnlpClassLoaderInternalException(ite);
            } catch (Exception iacce) {
                CommonLogger.LOG.jnlpClassLoaderInvocationException(iacce);
            }
        }
        log.warnv("Unable to adapt JAR file URL: {0}, using its external form instead", path);
        return fallback.toExternalForm();
    }
}