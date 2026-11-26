package dev.viren.coach.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Every "now" in the domain comes from this clock, so time-driven behaviour (slots, expiry, streaks) is testable. */
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
