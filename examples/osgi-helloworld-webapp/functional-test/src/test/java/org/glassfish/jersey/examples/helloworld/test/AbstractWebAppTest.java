/*
 * Copyright (c) 2010, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.helloworld.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

import javax.inject.Inject;

import org.glassfish.jersey.internal.util.JdkVersion;
import org.glassfish.jersey.internal.util.PropertiesHelper;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.vmOption;

/**
 * @author Jakub Podlesak
 * @author Adam Lindenthal
 */
public abstract class AbstractWebAppTest {

    @Inject
    BundleContext bundleContext;
    /**
     * maximum waiting time for runtime initialization and Jersey deployment
     */
    public static final long MAX_WAITING_SECONDS = 10L;
    /**
     * Latch for blocking the testing thread until the runtime is ready and
     * Jersey deployed
     */
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    private static final int port = getProperty("jersey.config.test.container.port", 8080);
    private static final String runtimePolicy = AccessController.doPrivileged(
            PropertiesHelper.getSystemProperty("runtime.policy"));
    private static final String felixPolicy = AccessController.doPrivileged(PropertiesHelper.getSystemProperty("felix.policy"));
    private static final String CONTEXT = "/helloworld";
    private static final URI baseUri = UriBuilder.fromUri("http://localhost").port(port).path(CONTEXT).build();
    private static final Logger LOGGER = Logger.getLogger(AbstractWebAppTest.class.getName());

    /**
     * Allow subclasses to define additional OSGi configuration - called after
     * genericOsgiOptions() and jettyOptions()
     *
     * @return list of pax exam Options
     */
    public abstract List<Option> osgiRuntimeOptions();

    /**
     * Generic OSGi options - defines which dependencies (bundles) should be
     * loaded into runtime
     */
    public List<Option> genericOsgiOptions() {

        // uncomment for debugging using felix console (lookup gogo string in the commnented lines below)
        String gogoVersion = "0.8.0";

        @SuppressWarnings("RedundantStringToString")
        List<Option> options = Arrays.asList(options(
                // vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                // vmOption("-Djava.security.debug=scl"),

                // uncomment for verbose class loading info
                // vmOption("-verbose:class"),
                getAddOpensForFelixFrameWorkSecurity(),

                // bootDelegationPackage("org.glassfish.jersey.client.*"),

                systemProperty("java.security.manager").value(""),
                systemProperty("felix.policy").value(felixPolicy),
                systemProperty("java.security.policy").value(runtimePolicy),
                systemProperty(org.osgi.framework.Constants.FRAMEWORK_SECURITY)
                        .value(org.osgi.framework.Constants.FRAMEWORK_SECURITY_OSGI),
                systemProperty("org.osgi.service.http.port").value(String.valueOf(port)),
                systemProperty("org.osgi.framework.system.packages.extra").value("jakarta.annotation"),
                systemProperty("jersey.config.test.container.port").value(String.valueOf(port)),
                // systemProperty(BundleLocationProperty).value(bundleLocation),

                // do not remove the following line
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("FINEST"),

                JdkVersion.getJdkVersion().getMajor() > 16
                    ? vmOption("--add-opens=java.base/java.net=ALL-UNNAMED")
                    : null,

                // uncomment the following 4 lines should you need to debug from th felix console
                // mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.gogo.runtime").version(gogoVersion),
                // mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.gogo.shell").version(gogoVersion),
                // mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.gogo.command").version(gogoVersion),
                // mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.shell.remote").versionAsInProject(),
                mavenBundle("org.apache.felix", "org.apache.felix.framework.security").versionAsInProject(),
                // uncomment for logging (do not remove the following two lines)
                //                 mavenBundle("org.ops4j.pax.logging", "pax-logging-api", "1.4"),
                //                 mavenBundle("org.ops4j.pax.logging", "pax-logging-service", "1.4"),

                // javax.annotation must go first!
                mavenBundle().groupId("jakarta.annotation").artifactId("jakarta.annotation-api").versionAsInProject(),
                // pax exam dependencies
                // mavenBundle("org.ops4j.pax.url", "pax-url-mvn"),
                junitBundles(), // adds junit classes to the OSGi context

                // HK2
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-api").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("osgi-resource-locator").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-locator").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-utils").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2.external").artifactId("jakarta.inject").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2.external").artifactId("aopalliance-repackaged").versionAsInProject(),
                mavenBundle().groupId("org.javassist").artifactId("javassist").versionAsInProject(),
                //JAXB-API
                mavenBundle().groupId("jakarta.xml.bind").artifactId("jakarta.xml.bind-api").versionAsInProject(),
                //SUN JAXB IMPL OSGI
                mavenBundle().groupId("com.sun.xml.bind").artifactId("jaxb-osgi").versionAsInProject().versionAsInProject(),
                getActivationBundle(),
                systemPackage("com.sun.source.tree"),
                systemPackage("com.sun.source.util"),

                // validation - required by jersey-container-servlet-core
                mavenBundle().groupId("jakarta.validation").artifactId("jakarta.validation-api").versionAsInProject(),
                // Jersey bundles
                mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-common").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-server").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-client").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.jersey.containers").artifactId("jersey-container-servlet-core")
                        .versionAsInProject(),
                mavenBundle().groupId("org.glassfish.jersey.inject").artifactId("jersey-hk2").versionAsInProject(),

                // JAX-RS API
                mavenBundle().groupId("jakarta.ws.rs").artifactId("jakarta.ws.rs-api").versionAsInProject(),

                // Those two bundles have different (unique) maven coordinates, but represent the same OSGi bundle in two
                // different versions.
                // (see the maven bundle plugin configuration in each of the two pom.xml files
                // Both bundles are explicitly loaded here to ensure, that both co-exist within the OSGi runtime;
                mavenBundle().groupId("org.glassfish.jersey.examples.osgi-helloworld-webapp")
                             .artifactId("additional-bundle").versionAsInProject(),
                // The alternate-version-bundle contains the same resource in the same package
                // (org.glassfish.jersey.examples.osgi.helloworld.additional.resource.AdditionalResource),
                // mapped to the same URI (/additional), but returning a different string as a response.
                // ---> if the test passes, it ensures, that Jersey sees/uses the correct version of the bundle
                mavenBundle().groupId("org.glassfish.jersey.examples.osgi-helloworld-webapp")
                             .artifactId("alternate-version-bundle").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.jersey.examples.osgi-helloworld-webapp")
                             .artifactId("war-bundle").type("war").versionAsInProject()
                // uncomment for debugging
                // vmOption( "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005" )
                ));

        final String localRepository = AccessController.doPrivileged(PropertiesHelper.getSystemProperty("localRepository"));
        if (localRepository != null) {
            options = new ArrayList<Option>(options);
            options.add(systemProperty("org.ops4j.pax.url.mvn.localRepository").value(localRepository));
        }

        return options;
    }

    private static Option getActivationBundle() {
        return JdkVersion.getJdkVersion().getMajor() > 8
                ? mavenBundle().groupId("com.sun.activation").artifactId("jakarta.activation").versionAsInProject()
                : null;
    }

    private static Option getAddOpensForFelixFrameWorkSecurity() {
        return JdkVersion.getJdkVersion().getMajor() > 8
                ? vmOption("--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED")
                : null;
    }

    public List<Option> jettyOptions() {
        return Arrays.asList(options(
                mavenBundle().groupId("org.ops4j.pax.web").artifactId("pax-web-jetty-bundle").versionAsInProject(),
                mavenBundle().groupId("org.ops4j.pax.web").artifactId("pax-web-extender-war").versionAsInProject()));
    }

    private void updatePermissionsFromFile() throws IOException {

        final ServiceReference cpaRef = bundleContext.getServiceReference(ConditionalPermissionAdmin.class.getName());
        final ConditionalPermissionAdmin conditionalPermissionAdmin = (ConditionalPermissionAdmin) bundleContext
                .getService(cpaRef);
        final ConditionalPermissionUpdate permissionUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate();
        final List conditionalPermissionInfos = permissionUpdate.getConditionalPermissionInfos();

        try {

            final BufferedReader reader = new BufferedReader(new FileReader(felixPolicy));
            String line;
            final Set<String> cpiNames = new HashSet<String>();

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("//")) {
                    final ConditionalPermissionInfo cpi = conditionalPermissionAdmin.newConditionalPermissionInfo(line);
                    final String cpiName = cpi.getName();
                    if (cpiNames.contains(cpiName)) {
                        throw new RuntimeException("Redundant policy name!");
                    }
                    cpiNames.add(cpiName);
                    conditionalPermissionInfos.add(cpi);
                }
            }
            reader.close();
            permissionUpdate.commit();

        } finally {
            bundleContext.ungetService(cpaRef);
        }
    }

    /**
     * After the war bundle is loaded and initialized, it sends custom OSGi
     * event "jersey/test/DEPLOYED"; This class handles the event (releases the
     * waiting lock)
     */
    @SuppressWarnings("UnusedDeclaration")
    public class WebEventHandler implements EventHandler {

        @Override
        public void handleEvent(Event event) {
            countDownLatch.countDown();
        }

        public WebEventHandler(String handlerName) {
            this.handlerName = handlerName;
        }
        private final String handlerName;

        protected String getHandlerName() {
            return handlerName;
        }
    }

    /**
     * Configuration method called by pax-exam framework
     *
     * @return
     */
    @SuppressWarnings("UnusedDeclaration")
    @Configuration
    public Option[] configuration() {
        List<Option> options = new LinkedList<Option>();

        options.addAll(genericOsgiOptions());
        options.addAll(jettyOptions());
        options.addAll(osgiRuntimeOptions());

        return options.toArray(new Option[options.size()]);
    }

    /**
     * Registers the event handler for custom jersey/test/DEPLOYED event
     */
    public void defaultMandatoryBeforeMethod() throws Exception {
        bundleContext.registerService(EventHandler.class.getName(),
                new WebEventHandler("Deploy Handler"), getHandlerServiceProperties("jersey/test/DEPLOYED"));

        assertNotNull(System.getSecurityManager());

        updatePermissionsFromFile();
    }

    /**
     * The test method itself - installs the war-bundle and sends two testing
     * requests
     *
     * @throws Exception
     */
    public WebTarget webAppTestTarget(String appRoot) throws Exception {

        LOGGER.info(bundleList());

        // restart war bundle...
        final Bundle warBundle = lookupWarBundle();
        warBundle.stop();
        warBundle.start();

        // and wait until it's ready
        LOGGER.fine("Waiting for jersey/test/DEPLOYED event with timeout " + MAX_WAITING_SECONDS + " seconds...");
        if (!countDownLatch.await(MAX_WAITING_SECONDS, TimeUnit.SECONDS)) {
            throw new TimeoutException("The event jersey/test/DEPLOYED did not arrive in "
                    + MAX_WAITING_SECONDS
                    + " seconds. Waiting timed out.");
        }
        final Client c = ClientBuilder.newClient();

        // server should be listening now and everything should be initialized
        return c.target(baseUri + appRoot);

    }

    private Bundle lookupWarBundle() {
        for (Bundle b : bundleContext.getBundles()) {
            if (b.getSymbolicName().contains("war-bundle")) {
                return b;
            }
        }
        return null;
    }

    private static int getProperty(final String varName, int defaultValue) {
        if (null == varName) {
            return defaultValue;
        }
        String varValue = AccessController.doPrivileged(PropertiesHelper.getSystemProperty(varName));
        if (null != varValue) {
            try {
                return Integer.parseInt(varValue);
            } catch (NumberFormatException e) {
                // will return default value below
            }
        }
        return defaultValue;
    }

    @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
    private Dictionary getHandlerServiceProperties(String... topics) {
        Dictionary result = new Hashtable();
        result.put(EventConstants.EVENT_TOPIC, topics);
        return result;
    }

    private String bundleList() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Bundle list -- \n");
        for (Bundle b : bundleContext.getBundles()) {
            sb.append(String.format("%1$5s", "[" + b.getBundleId() + "]")).append(" ")
                    .append(String.format("%1$-70s", b.getSymbolicName())).append(" | ")
                    .append(String.format("%1$-20s", b.getVersion())).append(" |");
            sb.append(stateString(b)).append(" |");
            sb.append(b.getLocation()).append("\n");
        }
        sb.append("-- \n\n");
        return sb.toString();
    }

    private String stateString(Bundle b) {
        switch (b.getState()) {
            case Bundle.ACTIVE:
                return "ACTIVE";
            case Bundle.INSTALLED:
                return "INSTALLED";
            case Bundle.RESOLVED:
                return "RESOLVED";
            default:
                return "???";
        }
    }
}
