FROM bellsoft/liberica-openjre-alpine:27-36@sha256:e598f7ef012857bc8d679d245ccee4663906c338c37de19b83814cec85e7d263
WORKDIR /deployments
COPY target/opentripplanner-mcp-0.0.1-SNAPSHOT.jar opentripplanner-mcp.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
CMD ["java", "-jar", "opentripplanner-mcp.jar" ]