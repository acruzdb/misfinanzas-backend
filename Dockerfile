# ---- Fase 1: construcción ----
# Compila el .jar dentro de un contenedor temporal con Maven + JDK,
# para no depender de tener Maven instalado en la máquina/runner que
# construye la imagen final (típicamente, un servidor de CI/CD).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos primero solo lo necesario para resolver dependencias, antes
# que el resto del código. Así, si solo cambias una clase Java, Docker
# reutiliza la capa de dependencias ya descargadas (caché), en vez de
# volver a bajar todo Maven Central en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ---- Fase 2: ejecución ----
# Imagen final: solo el JRE (no el JDK completo) + el .jar ya
# compilado. Mucho más ligera que arrastrar todo el toolchain de
# construcción a producción, donde no hace ninguna falta.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]