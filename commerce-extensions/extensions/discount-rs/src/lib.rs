//! HTTP layer: the two hook endpoints the commerce core calls, plus a health check.

pub mod rules;

use std::sync::Arc;

use axum::{
    Json, Router,
    extract::State,
    routing::{get, post},
};
use serde::Deserialize;
use serde_json::{Value, json};

use rules::{Adjustment, Line, Rules, Verdict};

#[derive(Debug, Deserialize)]
struct CartPriceRequest {
    lines: Vec<Line>,
}

#[derive(Debug, Deserialize)]
struct QuoteBody {
    lines: Vec<Line>,
}

#[derive(Debug, Deserialize)]
struct OrderValidateRequest {
    quote: QuoteBody,
}

#[derive(serde::Serialize)]
struct CartPriceResponse {
    adjustments: Vec<Adjustment>,
}

/// Builds the router. Malformed JSON is rejected by axum with a 4xx status, which the
/// core treats as a failed extension call.
pub fn app(rules: Rules) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/hooks/cart-price", post(cart_price))
        .route("/hooks/order-validate", post(order_validate))
        .with_state(Arc::new(rules))
}

async fn health() -> Json<Value> {
    Json(json!({ "status": "UP" }))
}

async fn cart_price(
    State(rules): State<Arc<Rules>>,
    Json(request): Json<CartPriceRequest>,
) -> Json<CartPriceResponse> {
    Json(CartPriceResponse {
        adjustments: rules.price(&request.lines),
    })
}

async fn order_validate(
    State(rules): State<Arc<Rules>>,
    Json(request): Json<OrderValidateRequest>,
) -> Json<Verdict> {
    Json(rules.validate(&request.quote.lines))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{body::Body, http::Request, http::StatusCode};
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    async fn post_json(uri: &str, body: Value) -> (StatusCode, Value) {
        let response = app(Rules::default())
            .oneshot(
                Request::post(uri)
                    .header("content-type", "application/json")
                    .body(Body::from(body.to_string()))
                    .unwrap(),
            )
            .await
            .unwrap();
        let status = response.status();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        let value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
        (status, value)
    }

    #[tokio::test]
    async fn cart_price_accepts_the_payload_the_core_sends() {
        // Same shape as PricingService.payload() in the Java core, including extra fields.
        let payload = json!({
            "hook": "cart.price",
            "currency": "EUR",
            "lines": [{"sku": "MUG-CER", "name": "Ceramic mug", "quantity": 5,
                       "unitPriceCents": 1290, "lineTotalCents": 6450}],
            "subtotalCents": 6450,
            "currentTotalCents": 6450
        });
        let (status, body) = post_json("/hooks/cart-price", payload).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(body["adjustments"][0]["sku"], "MUG-CER");
        assert_eq!(body["adjustments"][0]["amountCents"], -645);
    }

    #[tokio::test]
    async fn order_validate_returns_a_verdict() {
        let payload = json!({
            "hook": "order.validate",
            "quote": {"lines": [{"sku": "MUG-CER", "quantity": 25, "lineTotalCents": 32250}]}
        });
        let (status, body) = post_json("/hooks/order-validate", payload).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(body["allowed"], false);
        assert!(body["reason"].as_str().unwrap().contains("MUG-CER"));
    }

    #[tokio::test]
    async fn malformed_payload_is_rejected() {
        let (status, _) = post_json("/hooks/cart-price", json!({"items": []})).await;
        assert!(status.is_client_error());
    }

    #[tokio::test]
    async fn health_answers() {
        let response = app(Rules::default())
            .oneshot(Request::get("/health").body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
    }
}
