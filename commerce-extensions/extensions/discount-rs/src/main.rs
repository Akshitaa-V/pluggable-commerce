use std::env;

use discount_extension::{app, rules::Rules};

/// Reads a number from the environment, falling back to the default if unset.
/// An invalid value stops start-up instead of being silently ignored.
fn env_number<T: std::str::FromStr>(key: &str, default: T) -> T {
    match env::var(key) {
        Ok(value) => value
            .parse()
            .unwrap_or_else(|_| panic!("{key} must be a number, got '{value}'")),
        Err(_) => default,
    }
}

#[tokio::main]
async fn main() {
    let defaults = Rules::default();
    let rules = Rules {
        bulk_min_quantity: env_number("BULK_MIN_QUANTITY", defaults.bulk_min_quantity),
        bulk_percent: env_number("BULK_PERCENT", defaults.bulk_percent),
        bundle_skus: env::var("BUNDLE_SKUS")
            .map(|v| {
                v.split(',')
                    .map(|s| s.trim().to_string())
                    .filter(|s| !s.is_empty())
                    .collect()
            })
            .unwrap_or(defaults.bundle_skus),
        bundle_discount_cents: env_number("BUNDLE_DISCOUNT_CENTS", defaults.bundle_discount_cents),
        max_quantity_per_sku: env_number("MAX_QUANTITY_PER_SKU", defaults.max_quantity_per_sku),
    };
    assert!(
        (0..=100).contains(&rules.bulk_percent),
        "BULK_PERCENT must be 0 to 100"
    );

    let port: u16 = env_number("PORT", 8081);
    let listener = tokio::net::TcpListener::bind(("0.0.0.0", port))
        .await
        .expect("could not bind port");
    println!("discount extension listening on port {port} with {rules:?}");

    axum::serve(listener, app(rules))
        .with_graceful_shutdown(shutdown_signal())
        .await
        .expect("server error");
}

/// Waits for Ctrl-C locally or SIGTERM, which Kubernetes sends when it stops a pod.
async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminate = async {
        if let Ok(mut sig) =
            tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        {
            sig.recv().await;
        }
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        () = ctrl_c => {},
        () = terminate => {},
    }
}
