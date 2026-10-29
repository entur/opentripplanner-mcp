FROM bellsoft/liberica-openjre-alpine:25.0.5.0.3-5@sha256:d85a0ec55e191c5419b9338e3dd967236f0a948db0443a331514812dcdf6d751
WORKDIR /deployments
COPY target/opentripplanner-mcp-0.0.1-SNAPSHOT.jar opentripplanner-mcp.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
CMD ["java", "-jar", "opentripplanner-mcp.jar" ]