package ma.aboulhoda.consumptionagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "consumption.report")
public class ReportProperties {

    private String outputDir = "reports";

    private String publicBasePath = "/api/consumption/report";
}
