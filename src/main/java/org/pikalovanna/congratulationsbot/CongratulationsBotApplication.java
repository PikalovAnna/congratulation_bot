package org.pikalovanna.congratulationsbot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.pikalovanna.congratulationsbot.config.ApplicationProperties;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.ApiContextInitializer;

import javax.annotation.PostConstruct;
import java.util.TimeZone;


@SpringBootApplication
@EnableScheduling
public class CongratulationsBotApplication implements ApplicationContextAware {

	public static ApplicationContext context;
	public static ApplicationProperties properties;

	public static void main(String[] args) {
		ApiContextInitializer.init();

		SpringApplication.run(CongratulationsBotApplication.class, args);
	}

	@PostConstruct
	void onStart() {
		TimeZone.setDefault(TimeZone.getTimeZone(context.getBean(ApplicationProperties.class).getTimezone()));
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		CongratulationsBotApplication.context = applicationContext;
		CongratulationsBotApplication.properties = applicationContext.getBean(ApplicationProperties.class);
	}

}
