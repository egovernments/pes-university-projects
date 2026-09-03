import config


def utm_epsg_for(longitude, latitude):
    """Return the EPSG code of the UTM zone covering the given coordinate."""
    zone = int((longitude + 180) / 6) + 1
    hemisphere_base = 32600 if latitude >= 0 else 32700
    return hemisphere_base + zone


def resolve_metric_crs(boundaries):
    """Metric CRS for distance/area work: the configured override, else auto-derived UTM."""
    if config.METRIC_CRS:
        return config.METRIC_CRS
    min_x, min_y, max_x, max_y = boundaries.to_crs(config.STORAGE_CRS).total_bounds
    return f"EPSG:{utm_epsg_for((min_x + max_x) / 2, (min_y + max_y) / 2)}"
