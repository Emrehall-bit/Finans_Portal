package com.emrehalli.financeportal.news.provider.finnhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinnhubConfigPlaceholderTest {

    @Test
    void resolvesFinnhubApiKeyFromEnvironmentPlaceholder() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources propertySources = new MutablePropertySources();

        List<org.springframework.core.env.PropertySource<?>> yamlSources =
                loader.load("applicationConfig", new ClassPathResource("application.yml"));

        for (org.springframework.core.env.PropertySource<?> yamlSource : yamlSources) {
            propertySources.addLast(yamlSource);
        }
        propertySources.addFirst(new SystemEnvironmentPropertySource("testEnv", Map.of(
                "FINNHUB_API_KEY", "env-test-key"
        )));

        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertThat(resolver.getProperty("finnhub.api.key")).isEqualTo("env-test-key");
    }
}
