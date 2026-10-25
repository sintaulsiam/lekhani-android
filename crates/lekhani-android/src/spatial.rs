//! Spatial Bivariate Gaussian Touch Model
//!
//! Provides fat-finger touch error correction on mobile touchscreens.
//! Each key is modeled as a 2D Gaussian distribution over (x, y) coordinates:
//!
//! P(key = k | x, y) = 1 / (2π |Σ_k|^{1/2}) * exp(-0.5 * (p - μ_k)^T * Σ_k^{-1} * (p - μ_k))
//!
//! Inverse covariance matrices and normalization constants are pre-computed
//! when keyboard geometry changes (e.g. `onSizeChanged`), guaranteeing zero
//! heap allocations in the touch loop hot path.

use std::collections::HashMap;
use std::f32::consts::PI;

/// Input geometry configuration exported via UniFFI
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct KeyGeometryConfig {
    pub label: String,
    pub center_x: f32,
    pub center_y: f32,
    pub width: f32,
    pub height: f32,
}

/// Pre-computed 2D Gaussian distribution parameters for a single key
#[derive(Debug, Clone, PartialEq)]
pub struct SpatialKey {
    pub label: String,
    pub center_x: f32,
    pub center_y: f32,
    pub visual_center_x: f32,
    pub visual_center_y: f32,
    pub width: f32,
    pub height: f32,
    pub inv_sigma_xx: f32,
    pub inv_sigma_yy: f32,
    pub inv_sigma_xy: f32,
    pub log_norm: f32,
}

impl SpatialKey {
    /// Create a new pre-computed spatial key model.
    ///
    /// The standard deviations `sigma_x` and `sigma_y` are derived from key dimensions:
    /// - `sigma_x = width / 2.8`
    /// - `sigma_y = height / 2.4`
    /// - Vertical touch bias offset: users typically touch slightly below the visual center
    ///   by ~`0.08 * height`. We adjust `mu_y` accordingly.
    pub fn new(label: String, center_x: f32, center_y: f32, width: f32, height: f32) -> Self {
        let safe_width = width.max(1.0);
        let safe_height = height.max(1.0);

        let sigma_x = safe_width / 2.8;
        let sigma_y = safe_height / 2.4;

        // Systematic user touch center offset (empirical touch centroid bias)
        let effective_center_y = center_y + 0.08 * safe_height;

        let inv_sigma_xx = 1.0 / (sigma_x * sigma_x);
        let inv_sigma_yy = 1.0 / (sigma_y * sigma_y);
        let inv_sigma_xy = 0.0;

        // Normalization factor: 1 / (2 * π * sigma_x * sigma_y)
        // Log normalization: -ln(2 * π * sigma_x * sigma_y)
        let log_norm = -(2.0 * PI * sigma_x * sigma_y).ln();

        Self {
            label,
            center_x,
            center_y: effective_center_y,
            visual_center_x: center_x,
            visual_center_y: effective_center_y,
            width: safe_width,
            height: safe_height,
            inv_sigma_xx,
            inv_sigma_yy,
            inv_sigma_xy,
            log_norm,
        }
    }

    /// Dynamically calibrates personal touch center using an exponential moving average.
    /// Clamped to at most +/- 20% of key dimension to prevent drifting into neighboring keys.
    pub fn calibrate_touch(&mut self, touch_x: f32, touch_y: f32) {
        const LEARNING_RATE: f32 = 0.05;
        let max_drift_x = self.width * 0.20;
        let max_drift_y = self.height * 0.20;

        let new_cx = self.center_x + LEARNING_RATE * (touch_x - self.center_x);
        let new_cy = self.center_y + LEARNING_RATE * (touch_y - self.center_y);

        self.center_x = new_cx.clamp(self.visual_center_x - max_drift_x, self.visual_center_x + max_drift_x);
        self.center_y = new_cy.clamp(self.visual_center_y - max_drift_y, self.visual_center_y + max_drift_y);
    }

    /// Returns learned relative offset ratios (dx_ratio, dy_ratio) normalized to key dimensions.
    #[inline]
    pub fn get_relative_offsets(&self) -> (f32, f32) {
        let dx_ratio = (self.center_x - self.visual_center_x) / self.width;
        let dy_ratio = (self.center_y - self.visual_center_y) / self.height;
        (dx_ratio, dy_ratio)
    }

    /// Applies previously calibrated relative offset ratios to this key geometry.
    #[inline]
    pub fn apply_relative_offsets(&mut self, dx_ratio: f32, dy_ratio: f32) {
        let max_drift_x = self.width * 0.20;
        let max_drift_y = self.height * 0.20;
        let target_cx = self.visual_center_x + dx_ratio * self.width;
        let target_cy = self.visual_center_y + dy_ratio * self.height;
        self.center_x = target_cx.clamp(self.visual_center_x - max_drift_x, self.visual_center_x + max_drift_x);
        self.center_y = target_cy.clamp(self.visual_center_y - max_drift_y, self.visual_center_y + max_drift_y);
    }

    /// Compute the spatial log-probability `ln P(key | x, y)`.
    /// Zero heap allocations.
    #[inline]
    pub fn log_probability(&self, x: f32, y: f32) -> f32 {
        let dx = x - self.center_x;
        let dy = y - self.center_y;

        let quad_form = dx * dx * self.inv_sigma_xx
            + dy * dy * self.inv_sigma_yy
            + 2.0 * dx * dy * self.inv_sigma_xy;

        let exponent = -0.5 * quad_form;
        (self.log_norm + exponent).max(-30.0)
    }
}

/// Candidate key alternative ranked by spatial probability
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SpatialKeyCandidate {
    pub key: String,
    pub log_prob: f32,
    pub distance_squared: f32,
}

/// Pre-allocated spatial layout model covering all keys on the active keyboard
#[derive(Debug, Clone, Default)]
pub struct SpatialTouchModel {
    keys: Vec<SpatialKey>,
    learned_offsets: HashMap<String, (f32, f32)>,
}

impl SpatialTouchModel {
    pub fn new() -> Self {
        Self {
            keys: Vec::with_capacity(40),
            learned_offsets: HashMap::with_capacity(40),
        }
    }

    /// Update pre-computed key geometries on keyboard dimension / layout change,
    /// while continuously preserving personal touch calibration offsets.
    pub fn update_geometry(&mut self, configs: Vec<KeyGeometryConfig>) {
        // Harvest current relative offsets before re-allocating
        for key in &self.keys {
            self.learned_offsets.insert(key.label.clone(), key.get_relative_offsets());
        }

        self.keys.clear();
        self.keys.reserve(configs.len());
        for cfg in configs {
            let mut key = SpatialKey::new(
                cfg.label.clone(),
                cfg.center_x,
                cfg.center_y,
                cfg.width,
                cfg.height,
            );
            if let Some(&(dx, dy)) = self.learned_offsets.get(&cfg.label) {
                key.apply_relative_offsets(dx, dy);
            }
            self.keys.push(key);
        }
    }

    /// Exports learned personal touch calibration offsets to a compact JSON string.
    pub fn export_calibration_json(&self) -> String {
        serde_json::to_string(&self.learned_offsets).unwrap_or_else(|_| "{}".to_string())
    }

    /// Imports personal touch calibration offsets from JSON and updates active keys.
    pub fn import_calibration_json(&mut self, json_str: &str) -> bool {
        if let Ok(map) = serde_json::from_str::<HashMap<String, (f32, f32)>>(json_str) {
            for (label, (dx, dy)) in &map {
                self.learned_offsets.insert(label.clone(), (*dx, *dy));
                for key in &mut self.keys {
                    if key.label == *label {
                        key.apply_relative_offsets(*dx, *dy);
                        break;
                    }
                }
            }
            true
        } else {
            false
        }
    }

    /// Get spatial log-probability for a specific key label given touch (x, y).
    /// Returns default low log-prob (-20.0) if key label is unknown.
    /// Zero allocations.
    #[inline]
    pub fn log_prob_for_key(&self, label: &str, x: f32, y: f32) -> f32 {
        for key in &self.keys {
            if key.label == label {
                return key.log_probability(x, y);
            }
        }
        -20.0
    }

    /// Record a confirmed physical touch sample for a key to dynamically calibrate personal touch centers.
    pub fn record_touch_sample(&mut self, label: &str, touch_x: f32, touch_y: f32) {
        for key in &mut self.keys {
            if key.label == label {
                key.calibrate_touch(touch_x, touch_y);
                self.learned_offsets.insert(label.to_string(), key.get_relative_offsets());
                break;
            }
        }
    }

    /// Rank candidate keys by descending spatial probability given a touch point (x, y).
    /// Used for fat-finger autocorrection and candidate scoring.
    pub fn rank_keys_at(&self, x: f32, y: f32, top_k: usize) -> Vec<SpatialKeyCandidate> {
        let mut candidates: Vec<SpatialKeyCandidate> = self
            .keys
            .iter()
            .map(|k| {
                let dx = x - k.center_x;
                let dy = y - k.center_y;
                let dist_sq = dx * dx + dy * dy;
                SpatialKeyCandidate {
                    key: k.label.clone(),
                    log_prob: k.log_probability(x, y),
                    distance_squared: dist_sq,
                }
            })
            .collect();

        // Sort by descending log_prob (highest likelihood first)
        candidates.sort_by(|a, b| {
            b.log_prob
                .partial_cmp(&a.log_prob)
                .unwrap_or(std::cmp::Ordering::Equal)
        });

        if candidates.len() > top_k {
            candidates.truncate(top_k);
        }
        candidates
    }

    /// Check if the touch model has loaded key geometries
    pub fn is_empty(&self) -> bool {
        self.keys.is_empty()
    }

    /// Total number of mapped keys
    pub fn key_count(&self) -> usize {
        self.keys.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_spatial_key_center_maximum() {
        let key = SpatialKey::new("k".to_string(), 100.0, 200.0, 40.0, 50.0);
        let center_prob = key.log_probability(100.0, 200.0 + 0.08 * 50.0);

        // Near-center hit
        let near_prob = key.log_probability(105.0, 204.0);
        assert!(
            center_prob > near_prob,
            "Center touch must have higher probability than off-center touch"
        );

        // Far-away hit
        let far_prob = key.log_probability(150.0, 300.0);
        assert!(
            near_prob > far_prob,
            "Near touch must have higher probability than far touch"
        );
    }

    #[test]
    fn test_fat_finger_disambiguation() {
        let mut model = SpatialTouchModel::new();
        model.update_geometry(vec![
            KeyGeometryConfig {
                label: "g".to_string(),
                center_x: 100.0,
                center_y: 200.0,
                width: 40.0,
                height: 50.0,
            },
            KeyGeometryConfig {
                label: "h".to_string(),
                center_x: 140.0,
                center_y: 200.0,
                width: 40.0,
                height: 50.0,
            },
            KeyGeometryConfig {
                label: "j".to_string(),
                center_x: 180.0,
                center_y: 200.0,
                width: 40.0,
                height: 50.0,
            },
        ]);

        assert_eq!(model.key_count(), 3);

        // Touch slightly to the left of 'h', near boundary with 'g' (x = 135)
        let ranked = model.rank_keys_at(135.0, 204.0, 3);
        assert_eq!(ranked.len(), 3);
        // 'h' center is at 140, distance is 5. 'g' center is at 100, distance is 35.
        assert_eq!(ranked[0].key, "h");
        assert_eq!(ranked[1].key, "g");
        assert_eq!(ranked[2].key, "j");

        // Touch closer to 'g' (x = 110)
        let ranked_g = model.rank_keys_at(110.0, 204.0, 3);
        assert_eq!(ranked_g[0].key, "g");
        assert_eq!(ranked_g[1].key, "h");
    }

    #[test]
    fn test_log_prob_for_unknown_key() {
        let model = SpatialTouchModel::new();
        let prob = model.log_prob_for_key("nonexistent", 50.0, 50.0);
        assert_eq!(prob, -20.0);
    }

    #[test]
    fn test_dynamic_touch_calibration() {
        let mut model = SpatialTouchModel::new();
        model.update_geometry(vec![KeyGeometryConfig {
            label: "k".to_string(),
            center_x: 100.0,
            center_y: 200.0,
            width: 40.0,
            height: 50.0,
        }]);

        let initial_prob_offset = model.log_prob_for_key("k", 106.0, 204.0);

        // Typist consistently hits key 'k' slightly to the right at x = 106
        for _ in 0..10 {
            model.record_touch_sample("k", 106.0, 204.0);
        }

        let calibrated_prob = model.log_prob_for_key("k", 106.0, 204.0);
        assert!(
            calibrated_prob > initial_prob_offset,
            "Calibrated touch center must increase probability for typist's consistent touch offset"
        );
    }

    #[test]
    fn test_calibration_persists_across_geometry_update() {
        let mut model = SpatialTouchModel::new();
        model.update_geometry(vec![KeyGeometryConfig {
            label: "m".to_string(),
            center_x: 200.0,
            center_y: 300.0,
            width: 50.0,
            height: 60.0,
        }]);

        // Calibrate 'm' with systematic touch offset at x = 208, y = 310
        for _ in 0..15 {
            model.record_touch_sample("m", 208.0, 310.0);
        }

        let prob_before = model.log_prob_for_key("m", 208.0, 310.0);

        // Simulate screen rotation or keyboard resizing with new dimensions
        model.update_geometry(vec![KeyGeometryConfig {
            label: "m".to_string(),
            center_x: 200.0,
            center_y: 300.0,
            width: 50.0,
            height: 60.0,
        }]);

        let prob_after = model.log_prob_for_key("m", 208.0, 310.0);
        assert!(
            (prob_before - prob_after).abs() < 1e-4,
            "Calibrated offsets must persist across geometry updates (before: {}, after: {})",
            prob_before,
            prob_after
        );
    }

    #[test]
    fn test_calibration_json_export_import() {
        let mut model = SpatialTouchModel::new();
        model.update_geometry(vec![KeyGeometryConfig {
            label: "space".to_string(),
            center_x: 300.0,
            center_y: 500.0,
            width: 200.0,
            height: 50.0,
        }]);

        for _ in 0..10 {
            model.record_touch_sample("space", 315.0, 508.0);
        }

        let json = model.export_calibration_json();
        assert!(json.contains("space"), "Exported JSON must contain calibrated key 'space'");

        let mut fresh_model = SpatialTouchModel::new();
        fresh_model.update_geometry(vec![KeyGeometryConfig {
            label: "space".to_string(),
            center_x: 300.0,
            center_y: 500.0,
            width: 200.0,
            height: 50.0,
        }]);

        let success = fresh_model.import_calibration_json(&json);
        assert!(success, "JSON import should succeed");

        let p1 = model.log_prob_for_key("space", 315.0, 508.0);
        let p2 = fresh_model.log_prob_for_key("space", 315.0, 508.0);
        assert!((p1 - p2).abs() < 1e-4, "Imported calibration must match exported calibration");
    }
}
