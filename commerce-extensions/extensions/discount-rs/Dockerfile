# Build stage: compile a release binary.
FROM rust:1-slim AS build
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

# Run stage: small Debian image, numeric non-root user.
FROM debian:bookworm-slim
RUN useradd --system --no-create-home --uid 10001 app
USER 10001
COPY --from=build /src/target/release/discount-extension /usr/local/bin/discount-extension
EXPOSE 8081
ENTRYPOINT ["discount-extension"]
