package com.mafia.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Mafia Game Spring Boot application.
 * Initializes and bootstraps the Spring Boot context for the game server.
 */
@SpringBootApplication
public class GameApplication {

	/**
	 * Main method to run the Spring Boot application.
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(GameApplication.class, args);
	}

}
