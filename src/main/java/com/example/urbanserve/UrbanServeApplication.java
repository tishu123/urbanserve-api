package com.example.urbanserve;

import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UrbanServeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrbanServeApplication.class, args);
    }

    @Bean
    CommandLineRunner seed(CategoryRepository cats, ProviderRepository provs) {
        return args -> {
            if (cats.count() > 0) return;
            ServiceCategory clean = cats.save(new ServiceCategory("Home Cleaning", "Deep cleaning for homes", 799));
            ServiceCategory plumb = cats.save(new ServiceCategory("Plumbing", "Leaks, taps, fittings", 399));
            ServiceCategory elec = cats.save(new ServiceCategory("Electrician", "Wiring, fans, switches", 349));
            ServiceCategory salon = cats.save(new ServiceCategory("Salon at Home", "Haircut, facial, grooming", 599));
            provs.save(new Provider("Ravi Kumar", "9000000001", "Bengaluru", 4.8, clean));
            provs.save(new Provider("Sunita Devi", "9000000002", "Bengaluru", 4.6, clean));
            provs.save(new Provider("Imran Shaikh", "9000000003", "Bengaluru", 4.7, plumb));
            provs.save(new Provider("Manoj Nair", "9000000004", "Mumbai", 4.5, elec));
            provs.save(new Provider("Priya Menon", "9000000005", "Bengaluru", 4.9, salon));
        };
    }
}
