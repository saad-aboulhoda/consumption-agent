package ma.aboulhoda.consumptionagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(SqlQueryProperties.class)
public class SqlQueryConfig {

    @Bean
    public JdbcTemplate sqlQueryJdbcTemplate(DataSource dataSource, SqlQueryProperties properties) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setMaxRows(properties.getMaxRows());
        jdbcTemplate.setQueryTimeout(properties.getQueryTimeoutSeconds());
        return jdbcTemplate;
    }
}
