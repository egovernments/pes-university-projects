# One image carrying both halves of the engine: the Spring Boot API and the Python
# batch it shells out to. They ship together because PythonEngineRunner invokes the
# batch as a subprocess on the same filesystem - splitting them into two services
# would mean rebuilding that call as a network hop.

# --- Stage 1: build the JAR -------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS java-build
WORKDIR /build
COPY pdi-service/pom.xml .
# Warm the dependency cache on its own layer so source edits do not re-download Maven.
RUN mvn -B dependency:go-offline
COPY pdi-service/src ./src
RUN mvn -B clean package -DskipTests

# --- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# GDAL/GEOS/PROJ are the native libraries behind rasterio, geopandas and pyproj.
# Without them the wheels install but fail at import time.
RUN apt-get update && apt-get install -y --no-install-recommends \
      python3 python3-pip python3-venv \
      gdal-bin libgdal-dev libgeos-dev libproj-dev \
    && rm -rf /var/lib/apt/lists/*

# The engine runs from its own venv so the interpreter path is stable and explicit.
ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY pdi-batch ./pdi-batch
COPY --from=java-build /build/target/*.jar ./app.jar

# Where the engine finds its interpreter and working directory. These MUST match the
# layout above; the service shells out using exactly these values.
ENV PDI_ENGINE_PYTHON=/opt/venv/bin/python \
    PDI_ENGINE_WORKDIR=/app/pdi-batch \
    PDI_CACHE_DIR=/data/cache \
    PDI_ARTIFACTS_DIR=/data/artifacts \
    PDI_ENGINE_TIMEOUT=0

# Default working directories for the cache and artifacts. There is deliberately no
# VOLUME instruction: Railway rejects one outright ("docker VOLUME is not supported, use
# Railway Volumes"), and the documented demo deployment runs volumeless anyway - it
# overrides PDI_CACHE_DIR/PDI_ARTIFACTS_DIR to /tmp and is pre-seeded through the Postgres
# result cache on Neon (docs/DEPLOYMENT.md, "Demo mode"). Anywhere with real storage,
# mount it at /data from outside the image instead.
RUN mkdir -p /data/cache /data/artifacts

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Honour the platform's injected $PORT (Railway/Render) while defaulting to 8080.
CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
