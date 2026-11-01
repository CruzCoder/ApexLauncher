package com.kdt.mcgui;

import android.content.*;
import android.util.*;
import android.graphics.*;
import android.widget.EditText;

import net.kdt.pojavlaunch.R;

public class MineEditText extends androidx.appcompat.widget.AppCompatEditText {
	public MineEditText(Context ctx) {
		super(ctx);
		init();
	}

	public MineEditText(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setBackgroundResource(R.drawable.bg_input_glass);
		int horizontalPadding = Math.round(getResources().getDisplayMetrics().density * 12f);
		setPadding(horizontalPadding, 0, horizontalPadding, 0);
	}
}
