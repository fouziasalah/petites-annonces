# syntax=docker/dockerfile:1
# =============================================================================
# Image du backend — build multi-stage
#   1. build   : JDK + Maven, compile et produit le jar (les tests tournent en CI, pas ici :
#                les tests d'intégration ont besoin de Docker, indisponible dans un build).
#   2. runtime : JRE seul, utilisateur non-root, couches Spring Boot extraites.
# =============================================================================

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Les dépendances changent rarement : on les résout dans une couche à part, avant de copier
# les sources. Modifier une classe Java ne retélécharge donc pas tout Maven Central.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime

# Utilisateur sans privilèges : une faille applicative ne donne pas root dans le conteneur.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# Une couche Docker par couche Spring Boot, de la plus stable à la plus changeante :
# une nouvelle version du code ne republie que la dernière couche (quelques Ko).
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8081

# MaxRAMPercentage : la JVM dimensionne son tas d'après la limite mémoire du conteneur.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
