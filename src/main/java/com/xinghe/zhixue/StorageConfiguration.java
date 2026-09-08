package com.xinghe.zhixue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(@Value("${spring.datasource.url}") String url,
                               @Value("${spring.datasource.username}") String username,
                               @Value("${spring.datasource.password}") String password) {
        HikariDataSource source = pool(url, username, password);
        try (Connection connection = source.getConnection()) {
            if (!connection.isValid(3)) throw new SQLException("Connection unavailable");
            return source;
        } catch (SQLException e) {
            source.close();
            if (url.startsWith("jdbc:h2:mem:")) throw new IllegalStateException("无法初始化体验存储", e);
            LoggerFactory.getLogger(StorageConfiguration.class).warn("未连接到配置的数据库，本次启动使用体验模式；修复连接配置后重启可重新检测。");
            return pool("jdbc:h2:mem:xinghe_fallback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
    }

    private HikariDataSource pool(String url, String username, String password) {
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        source.setConnectionTimeout(4000);
        source.setValidationTimeout(2000);
        source.setInitializationFailTimeout(-1);
        if (url.startsWith("jdbc:mysql:")) {
            source.addDataSourceProperty("connectTimeout", "3000");
            source.addDataSourceProperty("socketTimeout", "5000");
        }
        return source;
    }
}
