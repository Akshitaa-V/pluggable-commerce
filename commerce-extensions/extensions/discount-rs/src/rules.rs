//! Pricing and validation rules. Pure functions with no I/O, so they are easy to test.

use serde::{Deserialize, Serialize};

/// One cart line as the commerce core sends it.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Line {
    pub sku: String,
    pub quantity: u32,
    pub line_total_cents: i64,
}

/// A price change sent back to the core. Negative is a discount.
#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Adjustment {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sku: Option<String>,
    pub label: String,
    pub amount_cents: i64,
}

/// The answer to an order.validate call.
#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct Verdict {
    pub allowed: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

/// Rule settings. Read from environment variables in `main.rs`.
#[derive(Debug, Clone)]
pub struct Rules {
    /// Quantity from which the bulk discount applies to a line.
    pub bulk_min_quantity: u32,
    /// Bulk discount in percent of the line total.
    pub bulk_percent: i64,
    /// If every sku in this list is in the cart, `bundle_discount_cents` is taken off.
    pub bundle_skus: Vec<String>,
    pub bundle_discount_cents: i64,
    /// Orders with more than this many units of one sku are rejected.
    pub max_quantity_per_sku: u32,
}

impl Default for Rules {
    fn default() -> Self {
        Self {
            bulk_min_quantity: 5,
            bulk_percent: 10,
            bundle_skus: vec!["COFFEE-1KG".into(), "GRINDER-H".into()],
            bundle_discount_cents: 500,
            max_quantity_per_sku: 20,
        }
    }
}

impl Rules {
    /// Discounts for a cart: a bulk discount per qualifying line and an optional bundle discount.
    pub fn price(&self, lines: &[Line]) -> Vec<Adjustment> {
        let mut adjustments = Vec::new();

        for line in lines {
            if line.quantity >= self.bulk_min_quantity && line.line_total_cents > 0 {
                // Integer cents, rounded down, so the discount never exceeds the line.
                let discount = line.line_total_cents * self.bulk_percent / 100;
                if discount > 0 {
                    adjustments.push(Adjustment {
                        sku: Some(line.sku.clone()),
                        label: format!(
                            "Bulk {}% ({}+ units)",
                            self.bulk_percent, self.bulk_min_quantity
                        ),
                        amount_cents: -discount,
                    });
                }
            }
        }

        let has_bundle = !self.bundle_skus.is_empty()
            && self
                .bundle_skus
                .iter()
                .all(|sku| lines.iter().any(|line| &line.sku == sku));
        if has_bundle && self.bundle_discount_cents > 0 {
            adjustments.push(Adjustment {
                sku: None,
                label: "Starter bundle".into(),
                amount_cents: -self.bundle_discount_cents,
            });
        }

        adjustments
    }

    /// Rejects orders that buy too many units of one product.
    pub fn validate(&self, lines: &[Line]) -> Verdict {
        match lines
            .iter()
            .find(|line| line.quantity > self.max_quantity_per_sku)
        {
            Some(line) => Verdict {
                allowed: false,
                reason: Some(format!(
                    "At most {} units of {} per order",
                    self.max_quantity_per_sku, line.sku
                )),
            },
            None => Verdict {
                allowed: true,
                reason: None,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn line(sku: &str, quantity: u32, unit_cents: i64) -> Line {
        Line {
            sku: sku.into(),
            quantity,
            line_total_cents: unit_cents * i64::from(quantity),
        }
    }

    #[test]
    fn no_discount_below_the_bulk_threshold() {
        let rules = Rules::default();
        assert!(rules.price(&[line("MUG-CER", 4, 1290)]).is_empty());
    }

    #[test]
    fn bulk_discount_is_ten_percent_rounded_down() {
        let rules = Rules::default();
        // 5 x 4.99 = 24.95, 10% = 2.495 -> 2.49
        let adjustments = rules.price(&[line("X", 5, 499)]);
        assert_eq!(adjustments.len(), 1);
        assert_eq!(adjustments[0].amount_cents, -249);
        assert_eq!(adjustments[0].sku.as_deref(), Some("X"));
    }

    #[test]
    fn bundle_discount_needs_every_bundle_sku() {
        let rules = Rules::default();
        assert!(rules.price(&[line("COFFEE-1KG", 1, 2490)]).is_empty());

        let adjustments = rules.price(&[line("COFFEE-1KG", 1, 2490), line("GRINDER-H", 1, 5990)]);
        assert_eq!(
            adjustments,
            vec![Adjustment {
                sku: None,
                label: "Starter bundle".into(),
                amount_cents: -500
            }]
        );
    }

    #[test]
    fn bulk_and_bundle_can_apply_together() {
        let rules = Rules::default();
        let adjustments = rules.price(&[line("COFFEE-1KG", 5, 2490), line("GRINDER-H", 1, 5990)]);
        let total: i64 = adjustments.iter().map(|a| a.amount_cents).sum();
        assert_eq!(total, -1245 - 500);
    }

    #[test]
    fn validation_rejects_too_many_units_with_a_reason() {
        let rules = Rules::default();
        assert!(rules.validate(&[line("MUG-CER", 20, 1290)]).allowed);

        let verdict = rules.validate(&[line("MUG-CER", 21, 1290)]);
        assert!(!verdict.allowed);
        assert_eq!(
            verdict.reason.as_deref(),
            Some("At most 20 units of MUG-CER per order")
        );
    }

    #[test]
    fn empty_bundle_list_never_matches() {
        let rules = Rules {
            bundle_skus: vec![],
            ..Rules::default()
        };
        assert!(rules.price(&[line("A", 1, 100)]).is_empty());
    }
}
