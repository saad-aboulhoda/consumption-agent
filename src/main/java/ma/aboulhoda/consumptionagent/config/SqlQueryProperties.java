package ma.aboulhoda.consumptionagent.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

@Getter
@Setter
@ConfigurationProperties(prefix = "consumption.sql-query")
public class SqlQueryProperties {

    private int maxRows = 500;

    private int queryTimeoutSeconds = 5;

    private String forbiddenKeywords =
            "insert|update|delete|drop|alter|create|truncate|attach|detach|pragma|vacuum|reindex|grant|revoke";

    @Getter(lombok.AccessLevel.NONE)
    private Pattern forbiddenPattern;

    @PostConstruct
    void compileForbiddenPattern() {
        forbiddenPattern = Pattern.compile("\\b(" + forbiddenKeywords + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    public Pattern getForbiddenPattern() {
        return forbiddenPattern;
    }
}
