package com.mirigangneung;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "tour.api.key="
        }
)
class MiriGangNeungApplicationTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void applicationContextProvidesTheJackson2MapperUsedByPlaceService() {
        assertThat(context.getBean(ObjectMapper.class)).isNotNull();
    }
}
