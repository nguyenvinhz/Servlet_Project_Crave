package com.foodordering.config;

import com.foodordering.listener.DatabaseContextListener;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hikaricp.internal.HikariCPConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the configured MySQL database; verifies the actual pool rather than dependency/source names. */
class JpaHikariIntegrationTest {
    @BeforeEach
    void startWithANewPersistenceFactory() {
        DatabaseConfig.close();
    }

    @AfterEach
    void closePersistenceFactory() {
        DatabaseConfig.close();
    }

    @Test
    void servletLifecycleStartsUsableJpaHikariPoolAndClosesItOnShutdown() {
        ServletContext context = mock(ServletContext.class);
        ServletContextEvent event = new ServletContextEvent(context);
        DatabaseContextListener lifecycle = new DatabaseContextListener();
        lifecycle.contextInitialized(event);
        verify(context).setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, "UP");

        EntityManagerFactory factory = DatabaseConfig.getEntityManagerFactory();
        SessionFactoryImplementor hibernate = factory.unwrap(SessionFactoryImplementor.class);
        ConnectionProvider provider = hibernate.getServiceRegistry().getService(ConnectionProvider.class);
        assertInstanceOf(HikariCPConnectionProvider.class, provider);
        HikariDataSource pool = assertInstanceOf(HikariDataSource.class, provider.unwrap(DataSource.class));
        assertTrue(pool.isRunning());
        assertFalse(pool.isClosed());
        assertTrue(pool.getMaximumPoolSize() > 0);
        assertTrue(pool.getMinimumIdle() <= pool.getMaximumPoolSize());
        assertNotNull(pool.getHikariPoolMXBean());

        try (EntityManager entityManager = factory.createEntityManager()) {
            assertEquals(1, entityManager.createNativeQuery("SELECT 1", Integer.class).getSingleResult());
            boolean borrowedFromPool = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                assertFalse(connection.isClosed());
                assertTrue(connection.isValid(3));
                return pool.getHikariPoolMXBean().getActiveConnections() >= 1;
            });
            assertTrue(borrowedFromPool, "JPA work must borrow an active Hikari connection");
        }
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(), "Closing the EntityManager must return its connection");

        lifecycle.contextDestroyed(event);
        assertFalse(factory.isOpen());
        assertTrue(pool.isClosed());
        assertFalse(pool.isRunning());
        assertThrows(SQLException.class, pool::getConnection, "The stopped pool must reject new connections");
        assertDoesNotThrow(DatabaseConfig::close, "Repeated context cleanup must remain safe");
    }
}
