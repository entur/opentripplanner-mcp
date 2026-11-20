FROM bellsoft/liberica-openjre-alpine:27
WORKDIR /deployments
COPY target/opentripplanner-mcp-0.0.1-SNAPSHOT.jar opentripplanner-mcp.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
CMD ["java", "-jar", "opentripplanner-mcp.jar" ]