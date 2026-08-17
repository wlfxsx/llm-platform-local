package io.llmplatform.config;

import io.llmplatform.common.UserDataPaths;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** 在打开 SQLite 前确保用户数据目录已存在，并完成版本化结构升级。 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(UserDataPaths paths) {
        paths.ensureDirectories();
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + paths.database());
        SchemaMigrator.migrate(dataSource);
        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
