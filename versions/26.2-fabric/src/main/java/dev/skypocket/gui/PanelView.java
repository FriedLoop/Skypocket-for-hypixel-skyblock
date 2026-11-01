package dev.skypocket.gui;

import java.util.List;

/**
 * Everything the panel's geometry, hit tests and drawing read from the screen, in one immutable
 * snapshot.
 *
 * <p>The panel logic used to reach into the screen's fields from ~40 methods, which is what kept
 * {@code OneClickScreen} a god class: {@link PanelGeometry} can only be extracted if its inputs are
 * explicit. Build one per frame ({@code OneClickScreen.view()}) - it is a plain record, so that
 * costs nothing.
 *
 * @param settingsTab   whether the panel settings page is open
 * @param viewingFeature the feature whose detail page is open, or null
 * @param selected      selected sidebar category (null = "All")
 * @param scrollY       vertical scroll offset of the content area, in design units
 * @param search        current search text
 * @param features      all registered features
 */
public record PanelView(boolean settingsTab, Feature viewingFeature, Category selected, int scrollY,
                        String search, List<Feature> features) {
}
