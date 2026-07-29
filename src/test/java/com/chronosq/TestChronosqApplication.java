package com.chronosq;

import org.springframework.boot.SpringApplication;

public class TestChronosqApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChronosqApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
