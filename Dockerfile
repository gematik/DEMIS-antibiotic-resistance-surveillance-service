# Declare Source Digest for the Base Image
ARG SOURCE_DIGEST=b7771460aba4079c91c0cb19e6a358451edeed8a1a84b09dc51b59cca9e47bdf
FROM gematik1/osadl-alpine-openjdk21-jre:1.0.2@sha256:${SOURCE_DIGEST}

# Redeclare Source Digest to be used in the build context
# https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG SOURCE_DIGEST=b7771460aba4079c91c0cb19e6a358451edeed8a1a84b09dc51b59cca9e47bdf

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

# Define the exposed port or range of ports for the service
EXPOSE 8080

# Defining Healthcheck
HEALTHCHECK --interval=15s \
            --timeout=10s \
            --start-period=30s \
            --retries=3 \
            CMD ["/usr/bin/wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

COPY --chown=$USERID:$GROUPID target/antibiotic-resistance-surveillance-service.jar /app.jar

# Run as User (not root)
USER $USERID:$USERID

ENTRYPOINT ["java", "-jar", "/app.jar"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="Antibiotic-Resistance-Surveillance-Service" \
      de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/git/demis/services/antibiotic-resistance-surveillance-service.git" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION \
      de.gematik.source.digest=$SOURCE_DIGEST
