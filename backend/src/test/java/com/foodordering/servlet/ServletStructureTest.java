package com.foodordering.servlet;

import jakarta.persistence.Entity;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the Servlet/JSP boundaries independently of database availability. */
class ServletStructureTest {
    private static final Path BACKEND = Files.isDirectory(Path.of("src/main/java"))
            ? Path.of("").toAbsolutePath() : Path.of("backend").toAbsolutePath();
    private static final Path JAVA = BACKEND.resolve("src/main/java/com/foodordering");
    private static final Path FRONTEND = BACKEND.resolve("../frontend").normalize();

    @Test
    void productionPackagesAndClassNamesMatchTheirSourceDirectories() throws Exception {
        for (Path source : sources(JAVA, ".java")) {
            String filename = source.getFileName().toString();
            String simpleName = filename.substring(0, filename.length() - ".java".length());
            String relative = JAVA.relativize(source.getParent()).toString().replace('\\', '.').replace('/', '.');
            String packageName = "com.foodordering" + (relative.isEmpty() ? "" : "." + relative);
            String declaration = "package " + packageName + ";";
            assertTrue(Files.readString(source).contains(declaration), "Package does not match directory: " + source);
            Class<?> type = Class.forName(packageName + "." + simpleName, false, getClass().getClassLoader());
            assertEquals(simpleName, type.getSimpleName(), source.toString());
            assertFalse(simpleName.matches(".*(?:ApiServlet|Controller|Mapper|Repository|Dto)$"), source.toString());
            if (type.isAnnotationPresent(WebServlet.class)) {
                assertTrue(packageName.equals("com.foodordering.servlet") || packageName.startsWith("com.foodordering.servlet."));
                assertTrue(simpleName.endsWith("Servlet"), source.toString());
                assertTrue(HttpServlet.class.isAssignableFrom(type), source.toString());
            }
            if (type.isAnnotationPresent(WebFilter.class)) {
                assertEquals("com.foodordering.filter", packageName);
                assertTrue(simpleName.endsWith("Filter") && Filter.class.isAssignableFrom(type), source.toString());
            }
            if (type.isAnnotationPresent(WebListener.class)) {
                assertEquals("com.foodordering.listener", packageName);
                assertTrue(simpleName.endsWith("Listener") && ServletContextListener.class.isAssignableFrom(type), source.toString());
            }
            if (type.isAnnotationPresent(Entity.class)) assertEquals("com.foodordering.model", packageName);
            if (packageName.equals("com.foodordering.dao")) assertTrue(simpleName.endsWith("DAO"), source.toString());
        }
    }

    @Test
    void mavenWarUsesTheBackendServletDescriptorAndReadsExistingFrontendResources() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var pom = factory.newDocumentBuilder().parse(BACKEND.resolve("pom.xml").toFile());
        assertEquals("${project.basedir}/src/main/webapp",
                pom.getElementsByTagNameNS("*", "warSourceDirectory").item(0).getTextContent());
        assertEquals("true", pom.getElementsByTagNameNS("*", "failOnMissingWebXml").item(0).getTextContent());
        var resource = (org.w3c.dom.Element) pom.getElementsByTagNameNS("*", "webResources").item(0);
        assertEquals("${project.basedir}/../frontend", resource.getElementsByTagNameNS("*", "directory").item(0).getTextContent());
        assertEquals("WEB-INF/web.xml", resource.getElementsByTagNameNS("*", "exclude").item(0).getTextContent());
        var web = factory.newDocumentBuilder().parse(BACKEND.resolve("src/main/webapp/WEB-INF/web.xml").toFile());
        assertEquals("6.0", web.getDocumentElement().getAttribute("version"));
        assertEquals("false", web.getDocumentElement().getAttribute("metadata-complete"));
        assertEquals("index.jsp", web.getElementsByTagNameNS("*", "welcome-file").item(0).getTextContent());
        assertEquals("COOKIE", web.getElementsByTagNameNS("*", "tracking-mode").item(0).getTextContent());
    }

    @Test
    void legacyLayersCannotReappearInProductionSources() throws Exception {
        for (String removed : List.of("api", "mapper", "dto", "entity", "repository", "utils", "gateway")) {
            assertFalse(Files.exists(JAVA.resolve(removed)), "Legacy package still exists: " + removed);
        }
        Pattern legacyImport = Pattern.compile("com\\.foodordering\\.(?:api|mapper|dto|entity|repository|utils|gateway)\\.");
        for (Path source : sources(JAVA, ".java")) {
            assertFalse(legacyImport.matcher(Files.readString(source)).find(), "Legacy package reference in " + source);
        }
        for (String layer : List.of("servlet", "bean", "model", "dao")) {
            assertFalse(sources(JAVA.resolve(layer), ".java").isEmpty(), "Missing Servlet application layer: " + layer);
        }
    }

    @Test
    void browserPagesUseFormsAndJspInsteadOfJsonTransport() throws Exception {
        Pattern browserTransport = Pattern.compile("\\bfetch\\s*\\(|\\bXMLHttpRequest\\b|\\baxios\\b|\\bJSON\\s*\\.\\s*(?:parse|stringify)\\s*\\(");
        for (Path source : sources(FRONTEND.resolve("assets/js"), ".js")) {
            assertFalse(browserTransport.matcher(Files.readString(source)).find(), "Browser data transport in " + source);
        }
        for (Path source : sources(FRONTEND.resolve("WEB-INF"), ".jsp", ".jspf")) {
            String markup = Files.readString(source);
            assertFalse(browserTransport.matcher(markup).find(), "Browser data transport in JSP " + source);
            assertFalse(markup.contains("/api/"), "JSP still targets a browser API: " + source);
        }
        Pattern jsonHandler = Pattern.compile("com\\.fasterxml\\.jackson|application/json|\\breadJson\\s*\\(|\\bwriteJson\\s*\\(|IntegrationJson");
        for (String packageName : List.of("servlet", "filter")) {
            for (Path source : sources(JAVA.resolve(packageName), ".java")) {
                // payOS signs its server-to-server webhook JSON; it is not a browser page endpoint.
                if (source.getFileName().toString().equals("PayosWebhookServlet.java")) continue;
                assertFalse(jsonHandler.matcher(Files.readString(source)).find(), "Application handler uses JSON: " + source);
            }
        }
    }

    @Test
    void jpaAndHikariRemainThePersistenceImplementation() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var pom = factory.newDocumentBuilder().parse(BACKEND.resolve("pom.xml").toFile());
        Set<String> artifacts = new HashSet<>();
        var artifactIds = pom.getElementsByTagNameNS("*", "artifactId");
        for (int i = 0; i < artifactIds.getLength(); i++) artifacts.add(artifactIds.item(i).getTextContent());
        assertTrue(artifacts.containsAll(Set.of("jakarta.servlet-api", "jakarta.servlet.jsp.jstl-api",
                "jakarta.persistence-api", "hibernate-core", "hibernate-hikaricp", "mysql-connector-j")));
        assertEquals("war", pom.getElementsByTagNameNS("*", "packaging").item(0).getTextContent());

        var persistence = factory.newDocumentBuilder().parse(BACKEND.resolve("src/main/resources/META-INF/persistence.xml").toFile());
        assertEquals("org.hibernate.jpa.HibernatePersistenceProvider",
                persistence.getElementsByTagNameNS("*", "provider").item(0).getTextContent());
        var models = persistence.getElementsByTagNameNS("*", "class");
        assertTrue(models.getLength() > 0);
        for (int i = 0; i < models.getLength(); i++) {
            String className = models.item(i).getTextContent();
            assertTrue(className.startsWith("com.foodordering.model."), className);
            assertTrue(Class.forName(className, false, getClass().getClassLoader()).isAnnotationPresent(Entity.class), className);
        }
        String database = Files.readString(JAVA.resolve("config/DatabaseConfig.java"));
        assertTrue(database.contains("Persistence.createEntityManagerFactory("));
        assertTrue(database.contains("org.hibernate.hikaricp.internal.HikariCPConnectionProvider"));
        assertTrue(database.contains("hibernate.hikari.jdbcUrl"));
        assertTrue(database.contains("hibernate.hikari.maximumPoolSize"));
    }

    @Test
    void everyServletRouteAndRegistrationNameHasOneOwner() throws Exception {
        Map<String, String> owners = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Path source : sources(JAVA.resolve("servlet"), ".java")) {
            String relative = JAVA.relativize(source).toString().replace('\\', '.').replace('/', '.');
            String className = "com.foodordering." + relative.substring(0, relative.length() - ".java".length());
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            WebServlet registration = type.getAnnotation(WebServlet.class);
            if (registration == null) continue;
            assertTrue(HttpServlet.class.isAssignableFrom(type), className);
            assertEquals(type.getSimpleName(), registration.name(), "Servlet registration must use the Java class name");
            if (!registration.name().isEmpty()) {
                assertNull(names.putIfAbsent(registration.name(), className), "Duplicate Servlet name " + registration.name());
            }
            List<String> patterns = Arrays.stream(registration.urlPatterns().length > 0
                    ? registration.urlPatterns() : registration.value()).toList();
            assertFalse(patterns.isEmpty(), className);
            for (String pattern : patterns) {
                assertNull(owners.putIfAbsent(pattern, className), "Duplicate Servlet route " + pattern);
                if (pattern.equals("/admin") || pattern.startsWith("/admin/")) {
                    assertEquals("com.foodordering.servlet.admin", type.getPackageName(), "Admin Servlet is outside servlet/admin: " + className);
                    assertTrue(type.getSimpleName().startsWith("Admin"), className);
                }
                if (pattern.startsWith("/api/")) {
                    // Existing deployment probes and the external signed callback retain their URLs.
                    assertTrue(Set.of("/api/payos/webhook", "/api/health").contains(pattern), "Browser API route remains: " + pattern);
                    assertEquals(pattern.equals("/api/health") ? HealthServlet.class : PayosWebhookServlet.class, type);
                }
            }
        }
        assertTrue(owners.keySet().containsAll(Set.of("/menu", "/menu/detail", "/cart", "/cart/items",
                "/cart/promotion", "/promotions", "/checkout", "/orders", "/auth/login", "/admin/menu")));
    }

    private static List<Path> sources(Path directory, String... extensions) throws Exception {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> Arrays.stream(extensions).anyMatch(extension -> path.toString().endsWith(extension)))
                    .toList();
        }
    }
}
