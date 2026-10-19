package fr.petitesannonces;

// Spring Boot (dépendance spring-boot-starter, via le parent du pom.xml)
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application backend.
 *
 * <p>Rôle : démarrer Spring Boot, qui crée tous les objets de l'application (contrôleurs,
 * services, repositories, configuration de sécurité) et lance le serveur web Tomcat.
 *
 * <p>Utilisation :
 * <ul>
 *   <li>en développement : {@code ./mvnw spring-boot:run} (dans le dossier backend) ;</li>
 *   <li>après packaging : {@code java -jar target/app.jar}.</li>
 * </ul>
 */
// @SpringBootApplication (Spring Boot) regroupe trois annotations :
//  - @Configuration : cette classe peut déclarer des objets Spring (@Bean) ;
//  - @EnableAutoConfiguration : Spring Boot configure tout seul ce qu'il trouve dans le
//    classpath (serveur web, JPA, sécurité, Flyway…) à partir de application.yml ;
//  - @ComponentScan : Spring cherche les classes annotées (@RestController, @Service,
//    @Component…) dans ce package et ses sous-packages (controller, service, etc.).
@SpringBootApplication
public class PetitesAnnoncesApplication {

    // Méthode main standard de Java : c'est elle que la JVM exécute au lancement.
    public static void main(String[] args) {
        // SpringApplication.run (Spring Boot) : démarre le contexte Spring et le serveur web.
        // Le premier argument indique la classe de configuration principale (celle-ci).
        SpringApplication.run(PetitesAnnoncesApplication.class, args);
    }
}
