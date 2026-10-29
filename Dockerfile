FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
WORKDIR /deployments
COPY target/opentripplanner-mcp-0.0.1-SNAPSHOT.jar opentripplanner-mcp.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
CMD ["java", "-jar", "opentripplanner-mcp.jar" ]