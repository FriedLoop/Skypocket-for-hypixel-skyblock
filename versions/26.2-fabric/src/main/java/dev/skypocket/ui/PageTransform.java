package dev.skypocket.ui;

/**
 * Maps screen coordinates into the page's design space.
 *
 * <p>A page is laid out in design units and drawn through a pose transform - scaled to fit the
 * window, then offset so it sits where the host wants it. Input arrives in screen pixels, so every
 * pointer position has to be run backwards through the same transform before it can be compared
 * against any bounds. Getting this wrong is not subtle: controls respond where they are not drawn.
 *
 * <p>The host owns the transform, because the host owns the pose. It sets it once with
 * {@link UiPage#transform} and then hands {@code UiPage} raw screen coordinates.
 */
public interface PageTransform {
    /** Identity: the page is drawn 1:1 at the origin. */
    PageTransform NONE = new PageTransform() {
        @Override
        public float toDesignX(double screenX) {
            return (float) screenX;
        }

        @Override
        public float toDesignY(double screenY) {
            return (float) screenY;
        }
    };

    float toDesignX(double screenX);

    float toDesignY(double screenY);
}
