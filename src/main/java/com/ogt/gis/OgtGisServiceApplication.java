package com.ogt.gis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OgtGisServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OgtGisServiceApplication.class, args);
	}

}
