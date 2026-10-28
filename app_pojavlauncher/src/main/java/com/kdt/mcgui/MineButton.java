package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));
		// NOTE(Apex): shared primary button look (gradient pill) across every screen
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.apex_btn_primary, null));
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
	}

}
