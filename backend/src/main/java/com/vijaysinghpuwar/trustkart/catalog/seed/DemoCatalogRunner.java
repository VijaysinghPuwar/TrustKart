package com.vijaysinghpuwar.trustkart.catalog.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/** Seeds demo data at startup only when explicitly enabled (dev/demo). Production never enables it by default. */
@Component
@ConditionalOnBooleanProperty("trustkart.demo.seed-catalog")
class DemoCatalogRunner implements ApplicationRunner {

    private final DemoCatalogSeeder seeder;

    DemoCatalogRunner(DemoCatalogSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed();
    }
}
