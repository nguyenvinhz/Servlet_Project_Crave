package com.foodordering.dev;

import org.apache.catalina.startup.Tomcat;

import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the packaged application on the same Servlet/JSP generation as production. */
public final class DevServer {
    private DevServer() {
    }

    public static void main(String[] args) throws Exception {
        Path war = Path.of(args.length > 0 ? args[0] : "backend/target/crave.war").toAbsolutePath();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        if (!Files.isRegularFile(war) || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Cần WAR đã build và cổng trong khoảng 1–65535.");
        }
        Path serverDirectory = war.getParent().resolve("tomcat-dev-" + port + "-" + ProcessHandle.current().pid());
        Files.createDirectories(serverDirectory.resolve("webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(serverDirectory.toString());
        tomcat.setPort(port);
        tomcat.getConnector().setURIEncoding("UTF-8");
        var context = tomcat.addWebapp("/crave", war.toString());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
                // The container may have already shut down through Ctrl+C.
            }
        }, "crave-dev-shutdown"));
        tomcat.start();
        if (!context.getState().isAvailable() || !tomcat.getConnector().getState().isAvailable()) {
            tomcat.stop();
            tomcat.destroy();
            throw new IllegalStateException("Không khởi động được Crave hoặc cổng " + port
                    + " đang được sử dụng. Xem lỗi phía trên.");
        }
        System.out.println("Crave: http://localhost:" + port + "/crave/menu");
        System.out.println("Quản trị: http://localhost:" + port + "/crave/admin");
        tomcat.getServer().await();
    }
}
