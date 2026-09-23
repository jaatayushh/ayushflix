package android.widget;

import android.content.Context;
import android.view.ViewGroup;

@android.annotation.Stub
public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) {
        super(context);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int gravity = -1;

        public LayoutParams() {
            super(WRAP_CONTENT, WRAP_CONTENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source != null ? source.width : WRAP_CONTENT, source != null ? source.height : WRAP_CONTENT);
        }
    }
}
