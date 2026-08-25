package com.fuma.hiselectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"discovery.defaults.enabled=false",
		"scheduling.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:context-load;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class HiselectorsApplicationTests {

	@Test
	void contextLoads() {
	}

}
