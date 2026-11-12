package br.com.familhas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import br.com.familhas.booking.BookingProperties;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {
}
