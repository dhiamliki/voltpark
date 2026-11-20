package tn.voltpark.energy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TariffProperties.class)
public class EnergyConfig {
}
