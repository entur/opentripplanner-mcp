# Build stage
FROM golang:1.23-alpine AS builder

WORKDIR /app

# Copy go.mod and go.sum first to leverage Docker cache
COPY go.mod go.sum* ./

# Pre-download all dependencies to avoid messages during runtime
RUN go mod download
RUN go get github.com/mark3labs/mcp-go@v0.30.0

# Copy the source code
COPY . .

# Update go.mod file with all dependencies
RUN go mod tidy

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -o opentripplanner-mcp .

# Development image for local testing
FROM golang:1.23-alpine AS development

WORKDIR /app

# Copy everything from the builder stage
COPY --from=builder /app .
COPY --from=builder /go/pkg /go/pkg

# Set environment variables
ENV ENV=development

# Run the application in development mode - build first to avoid download messages
CMD ["sh", "-c", "go build -o /tmp/opentripplanner-mcp && /tmp/opentripplanner-mcp"]

# Production image - minimal size
FROM alpine:3.19 AS production

WORKDIR /app

# Copy the binary from the builder stage
COPY --from=builder /app/opentripplanner-mcp .

# Set environment variables
ENV ENV=production

# Run the application
CMD ["/app/opentripplanner-mcp"]
