package com.kdt.mcgui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;

/**
 * A single item of the Apex navigation rail / bottom bar.
 *
 * The icon and the label are supplied through the {@code apexNavIcon} and
 * {@code apexNavLabel} attributes, the selected look is handled by the
 * {@code apex_nav_item} background + the duplicated parent state.
 */
public class ApexNavItem extends LinearLayout {

    private ImageView mIcon;
    private TextView mLabel;

    public ApexNavItem(Context context) {
        this(context, null);
    }

    public ApexNavItem(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.apex_nav_item);

        LayoutInflater.from(getContext()).inflate(R.layout.item_apex_nav, this, true);
        mIcon = findViewById(R.id.apex_nav_icon);
        mLabel = findViewById(R.id.apex_nav_label);

        if (attrs == null) return;
        TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.ApexNavItem);
        try {
            int iconRes = attributes.getResourceId(R.styleable.ApexNavItem_apexNavIcon, 0);
            CharSequence label = attributes.getText(R.styleable.ApexNavItem_apexNavLabel);
            if (iconRes != 0) mIcon.setImageResource(iconRes);
            if (label != null) mLabel.setText(label);
        } finally {
            attributes.recycle();
        }
    }

    public void setApexLabel(CharSequence label) {
        if (mLabel != null) mLabel.setText(label);
    }

    public void setApexIcon(int drawableRes) {
        if (mIcon != null) mIcon.setImageResource(drawableRes);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        // duplicateParentState handles the children, but the label of a
        // multi-line/ellipsized item may need an explicit refresh.
        View icon = mIcon;
        View label = mLabel;
        if (icon != null) icon.refreshDrawableState();
        if (label != null) label.refreshDrawableState();
    }
}
