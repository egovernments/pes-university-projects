INSERT INTO risk_score_config (
    campaign_type,
    weight_population_gap,
    weight_building_density,
    weight_facility_distance,
    weight_past_performance,
    weight_missed_children,
    tenant_id
) VALUES (
    'POLIO',
    0.300,
    0.150,
    0.200,
    0.250,
    0.100,
    'default'
)
ON CONFLICT (campaign_type, tenant_id) DO NOTHING;
