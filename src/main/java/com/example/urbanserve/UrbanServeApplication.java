
package com.example.urbanserve;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.model.ServicePackage;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import com.example.urbanserve.repo.ServicePackageRepository;

@SpringBootApplication
public class UrbanServeApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrbanServeApplication.class, args);
    }

    // Rohnit: initialize categories, sample providers and service packages.
    @Bean
    CommandLineRunner seed(
            CategoryRepository cats,
            ProviderRepository provs,
            ServicePackageRepository packages) {

        return args -> {

            // Remember whether this is a completely empty category database.
            boolean databaseWasEmpty = cats.count() == 0;

            // Load existing categories so we never depend on fixed database IDs.
            Map<String, ServiceCategory> categoriesByName = new HashMap<>();

            for (ServiceCategory category : cats.findAll()) {
                categoriesByName.putIfAbsent(category.getName(), category);
            }

            // Create any missing default categories.
            ServiceCategory clean = getOrCreateCategory(
                    cats, categoriesByName,
                    "Home Cleaning", "Deep cleaning for homes", 799
            );

            ServiceCategory plumb = getOrCreateCategory(
                    cats, categoriesByName,
                    "Plumbing", "Leaks, taps, fittings", 399
            );

            ServiceCategory elec = getOrCreateCategory(
                    cats, categoriesByName,
                    "Electrician", "Wiring, fans, switches", 349
            );

            ServiceCategory salon = getOrCreateCategory(
                    cats, categoriesByName,
                    "Salon at Home", "Haircut, facial, grooming", 599
            );

            // Preserve the existing sample provider initialization.
            // Sample providers are inserted only for a fresh database.
            if (databaseWasEmpty && provs.count() == 0) {

                provs.save(new Provider(
                        "Ravi Kumar", "9000000001", "Bengaluru", 4.8, clean
                ));

                provs.save(new Provider(
                        "Sunita Devi", "9000000002", "Bengaluru", 4.6, clean
                ));

                provs.save(new Provider(
                        "Imran Shaikh", "9000000003", "Bengaluru", 4.7, plumb
                ));

                provs.save(new Provider(
                        "Manoj Nair", "9000000004", "Mumbai", 4.5, elec
                ));

                provs.save(new Provider(
                        "Priya Menon", "9000000005", "Bengaluru", 4.9, salon
                ));
            }

            // Initialize all backend-managed service packages.
            seedPackages(packages, clean, plumb, elec, salon);
        };
    }

    private static ServiceCategory getOrCreateCategory(
            CategoryRepository cats,
            Map<String, ServiceCategory> categoriesByName,
            String name,
            String description,
            double basePrice) {

        return categoriesByName.computeIfAbsent(
                name,
                key -> cats.save(
                        new ServiceCategory(name, description, basePrice)
                )
        );
    }

    private static void seedPackages(
            ServicePackageRepository packages,
            ServiceCategory clean,
            ServiceCategory plumb,
            ServiceCategory elec,
            ServiceCategory salon) {

        // Home Cleaning: 8 packages.
        seedCategoryPackages(packages, clean, List.of(
                new PackageSeed("1bhk", "1 BHK", "4.4"),
                new PackageSeed("2bhk", "2 BHK", "5.6"),
                new PackageSeed("3bhk", "3 BHK", "7.0"),
                new PackageSeed("4bhk", "4 BHK", "8.6"),
                new PackageSeed("villa", "Independent house / Villa", "11.5"),
                new PackageSeed("kitchen", "Kitchen deep cleaning", "2.0"),
                new PackageSeed("bathroom", "Bathroom deep cleaning", "0.75"),
                new PackageSeed("sofa", "Sofa shampooing (5 seats)", "1.25")
        ));

        // Plumbing: 5 packages.
        seedCategoryPackages(packages, plumb, List.of(
                new PackageSeed("tap", "Tap / mixer repair", "1"),
                new PackageSeed("leak", "Pipe leakage fix", "1.25"),
                new PackageSeed("basin", "Wash basin installation", "1.75"),
                new PackageSeed("toilet", "Toilet / flush repair", "1.5"),
                new PackageSeed("tank", "Water tank cleaning", "3")
        ));

        // Electrician: 5 packages.
        seedCategoryPackages(packages, elec, List.of(
                new PackageSeed("switch", "Switch / socket repair", "1"),
                new PackageSeed("fan", "Ceiling fan installation", "1.25"),
                new PackageSeed("light", "Light fitting installation", "1.1"),
                new PackageSeed("mcb", "MCB / fuse repair", "1.5"),
                new PackageSeed("wiring", "Home wiring inspection", "2.5")
        ));

        // Salon at Home: 4 packages.
        seedCategoryPackages(packages, salon, List.of(
                new PackageSeed("haircut", "Haircut & styling", "1"),
                new PackageSeed("facial", "Signature facial", "1.75"),
                new PackageSeed("manipedi", "Manicure + pedicure", "1.5"),
                new PackageSeed("wax", "Full arms & legs waxing", "1.3")
        ));
    }

    private static void seedCategoryPackages(
            ServicePackageRepository packages,
            ServiceCategory category,
            List<PackageSeed> packageSeeds) {

        for (PackageSeed seed : packageSeeds) {

            // Skip existing packages so application restarts don't duplicate them.
            boolean exists = packages.existsByCategory_IdAndCode(
                    category.getId(),
                    seed.code()
            );

            if (!exists) {
                packages.save(new ServicePackage(
                        category,
                        seed.code(),
                        seed.name(),
                        new BigDecimal(seed.priceFactor())
                ));
            }
        }
    }

    private record PackageSeed(
            String code,
            String name,
            String priceFactor) {
    }
}
