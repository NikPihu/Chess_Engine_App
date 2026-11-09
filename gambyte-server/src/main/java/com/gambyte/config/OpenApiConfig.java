package com.gambyte.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description of the generated API docs at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI gambyteOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Gambyte API")
                .version("1")
                .description(
                    """
                    REST API of the Gambyte chess site. Browsers authenticate with the session \
                    cookie plus the X-XSRF-TOKEN header; bots with `Authorization: Bearer gbt_...`. \
                    Live game updates use STOMP over WebSocket at /ws (see docs/API.md)."""));
  }
}
