package com.aiops.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 启动时验证数据库连接，避免只看到 Hikari 原始堆栈却不知道要改哪里。
 */
@Slf4j
@Component
@Profile("!test")
public class DatabaseStartupCheck implements ApplicationRunner {

    private final DataSource dataSource;
    private final Environment environment;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.database.check-on-startup:true}")
    private boolean enabled;

    public DatabaseStartupCheck(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("数据库启动检查已关闭（app.database.check-on-startup=false）");
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("SELECT 1");

            log.info("数据库连接成功: {} (profiles={})",
                    sanitize(datasourceUrl),
                    String.join(",", environment.getActiveProfiles()));
        } catch (SQLException exception) {
            String hint = """
                    数据库连接失败，请按顺序检查：
                    1. MySQL 是否已启动，localhost:3306 是否可访问；
                    2. 是否执行过 sql/schema.sql 创建 aiops_agent 数据库和表；
                    3. agent/application-local.yml 中的 username/password 是否正确；
                    4. IDE 运行时是否加载了 local profile，或设置了 DB_PASSWORD 环境变量。
                    原始错误：%s""".formatted(exception.getMessage());

            log.error(hint, exception);
            throw new IllegalStateException(
                    "数据库连接失败，请检查 MySQL 是否启动、数据库是否初始化以及密码配置",
                    exception);
        }
    }

    private String sanitize(String url) {
        if (url == null || url.isBlank()) {
            return "未配置";
        }
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex);
    }
}
